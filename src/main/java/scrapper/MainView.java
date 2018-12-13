package scrapper;
import static java.util.stream.Collectors.toCollection;
import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;








import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sam.console.ANSI;
import sam.myutils.MyUtils;
import scrapper.scrapper.AbstractScrapper;
import scrapper.scrapper.DataStore;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.ScrappingResult.DownloadTask;
import scrapper.scrapper.commons.Commons;
import scrapper.scrapper.specials.Specials;

public final class MainView extends Application {
    private static volatile boolean downloadStarted = false;
    private Logger logger = LoggerFactory.getLogger(getClass());

    static {
        System.out.println(ANSI.green("MainView initiated"));
    }

    private final TilePane infoBoxes = new TilePane(10,  10);
    private final  ProgressBar progressBar = new ProgressBar();
    private final VBox progressBox = new VBox(5);
    private static Stage stage;
    private final Label status = new Label();
    private final BorderPane root = new BorderPane();
    {
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle("-fx-background-color:black;-fx-text-fill:white;-fx-font-width:bold;-fx-font-size:10;-fx-font-family:'Courier New';-fx-padding:2");
    }

    public static Stage getStage() {
        return stage;
    }
    @Override
    public void start(Stage stage) throws Exception {
        MainView.stage = stage;

        boolean isDownload = getParameters().getRaw().contains("--download");
        final Collection<String> urls =  isDownload ? null : System.getProperty("urls-file") == null ? inputDialog() : Files.lines(Paths.get(System.getProperty("urls-file"))).distinct().collect(Collectors.toList());

        infoBoxes.setPrefColumns(3);
        infoBoxes.setAlignment(Pos.CENTER);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        progressBox.setPadding(new Insets(2));

        ScrollPane sp = new ScrollPane(progressBox);
        progressBox.maxHeight(Double.MAX_VALUE);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setPrefHeight(200);

        root.setCenter(new VBox(10, progressBar, infoBoxes, sp));
        root.setBottom(status);
        Scene scene = new Scene(root);
        stage.setScene(scene);

        scene.getStylesheets().add(ClassLoader.getSystemResource("style.css").toString());

        start2(urls);

        stage.setWidth(520);
        stage.setHeight(400);
        stage.show();
    }
    
    private ArrayList<AbstractScrapper<?>> scrappers;
    private ThreadPoolExecutor threadPool;
    
    private void start2(final Collection<String> urls) throws IOException, InstantiationException, IllegalAccessException, URISyntaxException {
        final int total = urls.size();

        scrappers = new ArrayList<>();
        scrappers.addAll(Commons.get(urls));
        scrappers.addAll(Specials.get(urls));

        info("\n"+
                yellow("total urls: {}\n")+
                green("good urls: {}\n")+
                red("bad urls: {}\n"), total, scrappers.stream().mapToInt(AbstractScrapper::size).sum(), urls.size());

        if(!urls.isEmpty())
            DataStore.BAD_URLS.addAll(urls);

        if(scrappers.isEmpty())
            showErrorAndExit(urls.isEmpty() ? "failed : no urls" : "failed : only bad urls");

        info(cyan("Total: ")+scrappers.stream().mapToInt(AbstractScrapper::size).sum());
        info(cyan("---------------------------------------------"));
        String format = "%-25s%s";
        info(yellow(String.format(format, "method_name", "count")));
        scrappers.forEach(s -> s.printCount(format));
        info(cyan("---------------------------------------------"));
        info("\n");

        List<ScrappingResult> completedTasks = new ArrayList<>();
        List<Callable<ScrappingResult>> remainingTasks = new ArrayList<>();

        scrappers.stream().forEach(a -> a.fillTasks(completedTasks, remainingTasks));

        if(remainingTasks.isEmpty() && completedTasks.isEmpty())
            showErrorAndExit("no tasks found, to perform");

        scrappers.stream().map(AbstractScrapper::getInfoBox)
        .collect(toCollection(infoBoxes::getChildren));

        String format2 = "remaining: %d  | total: "+total;
        CountDownLatch latch = new CountDownLatch(remainingTasks.size());

        stage.setTitle("Scrapping");

        AbstractUpdatable scrapperUpdate = new AbstractUpdatable() {
            @Override
            public void update() {
                long i = latch.getCount();
                status.setText(String.format(format2, i));
                progressBar.setProgress((total - i) / total);
            }
        };

        AtomicInteger totalDownload = new AtomicInteger(0);
        AtomicInteger completedDownload = new AtomicInteger(0);
        String format3 = "completed: %d  | total: %d";

        AbstractUpdatable downloadUpdate = new AbstractUpdatable() {
            @Override
            public void update() {
                status.setText(String.format(format3, completedDownload.get(), totalDownload.get()));
                progressBar.setProgress(completedDownload.get() / (double)totalDownload.get());
            }
        };

        threadPool = new ThreadPoolExecutor(EnvConfig.THREAD_COUNT, EnvConfig.THREAD_COUNT, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);

                if(r instanceof DownloadTask) {
                    completedDownload.incrementAndGet();
                    downloadUpdate.setChanged();
                } else if(r instanceof FutureTask){
                    latch.countDown();

                    @SuppressWarnings("unchecked")
                    FutureTask<ScrappingResult> f = (FutureTask<ScrappingResult>)r;
                    ScrappingResult s = null;
                    try {
                        s = f.get();
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("Future.get() failed", e);
                    }
                    if(s == null)
                        return;
                    int count = s.startDownload(threadPool);
                    
                    totalDownload.addAndGet(count);
                    
                    scrapperUpdate.setChanged();
                    downloadUpdate.setChanged();
                }
            };
        };

        scrapperUpdate.update();
        Updatables.add(scrapperUpdate);

        info("\n");
        info(ANSI.createBanner("SCRAPPING"));

        Collections.shuffle(remainingTasks);
        remainingTasks.forEach(threadPool::submit);

        threadPool.execute(() -> {
            info(red("Downloader waiting"));
            try {
                latch.await();
            } catch (InterruptedException e1) {
                logger.warn(red("latch Interrupted"), e1);
                System.exit(0);
            }

            Platform.runLater(() -> {
                stage.setTitle("Downloading");
                stage.getScene().setRoot(new VBox(progressBar, status));
                progressBar.setProgress(0);
                status.setPadding(new Insets(10));
                stage.setWidth(200);
                stage.setHeight(100);

                info("\n");
                info(ANSI.createBanner("DOWNLOADING"));

                downloadStarted = true;
                Updatables.remove(scrapperUpdate);
                Updatables.add(downloadUpdate);
            });

            downloadStarted = true;

            int count = 
                    completedTasks.stream()
                    .mapToInt(sr -> sr.startDownload(threadPool))
                    .sum();

            totalDownload.addAndGet(count);
            downloadUpdate.setChanged();
            
            Runnable finish = () -> {
                try {
                    save();
                } catch (IOException e) {
                    logger.error("failed to save", e);
                    System.exit(0);
                }
            };

            Thread shutdown = new Thread(finish);

            Runtime.getRuntime().addShutdownHook(shutdown);

            threadPool.execute(() -> {
                Thread t = new Thread(() -> {
                    System.out.println(ANSI.yellow("finishing"));
                    threadPool.shutdown();
                    try {
                        System.out.println(red("waiting to terminate: ")+threadPool.getActiveCount());
                        threadPool.awaitTermination(3, TimeUnit.DAYS);
                    } catch (InterruptedException e) {
                        logger.error("ex.awaitTermination(3, TimeUnit.DAYS); Interrupted", e);
                    }

                    Runtime.getRuntime().removeShutdownHook(shutdown);
                    finish.run();
                    Platform.runLater(stage::hide);
                });
                t.start();
            });
        });
    }
    private void info(String format, Object...obj) {
        logger.info(format, obj);
    }
    private void info(String msg) {
        logger.info(msg);
    }
    private void showErrorAndExit(String str) {
        Alert al = new Alert(AlertType.ERROR);
        al.setHeaderText(str);
        al.setTitle("error");
        al.showAndWait();
        System.exit(0);
    }

    private volatile boolean  saved = false;
    private void save() throws IOException {
        if(!downloadStarted) {
            MyUtils.beep(5);
            logger.warn(red("\n\nstopped at scrapping"));
            return;
        }

        if(saved)
            return;

        saved = true;
        DataStore.saveClientCopy();

        Utils.openFile(EnvConfig.DOWNLOAD_DIR.toFile());
        MyUtils.beep(5);

        logger.info("\n\n"+FINISHED_BANNER);
    }
    private Collection<String> inputDialog() {
        Dialog<ButtonType> tt = new Dialog<>();
        tt.setTitle("RSS OWL");
        TextArea ta = new TextArea();
        ta.setPrefRowCount(20);

        tt.getDialogPane().setExpandableContent(ta);
        tt.getDialogPane().setExpanded(true);
        tt.setHeaderText("Enter Urls seperated by newline");
        tt.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        /**
         * TODO 
                 if(AbstractScrapper.backupPaths.values().stream().map(Paths::get).anyMatch(Files::exists)) {
            Button deletebackups = new Button("delete backup.dat");

            HBox hbox = new HBox(5, deletebackups);
            hbox.setAlignment(Pos.CENTER_RIGHT);

            tt.getDialogPane().setContent(hbox);

            deletebackups.setOnAction(e -> {
                AbstractScrapper.backupPaths.values().stream().map(Paths::get)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                        tt.getDialogPane().setContent(null);
                        logger.info(ANSI.red(path + " deleted"));
                        AbstractScrapper.clear();
                    } catch (IOException e1) {}    
                });
                tt.getDialogPane().setContent(null);
            });
        }
         */

        Set<String> ret = new LinkedHashSet<>();

        ta.textProperty().addListener(i -> Platform.runLater(() -> {
            String strs = ta.getText();
            ret.clear();

            if(strs != null && !(strs = strs.trim()).isEmpty()) {
                Stream.of(strs.split("\r?\n"))
                .map(s -> s.replace('"', ' ').trim())
                .filter(s -> !s.isEmpty())
                .forEach(ret::add);                
            }
            tt.setContentText("Total: "+ret.size());
        }));

        tt.setResultConverter(b -> b);
        Optional<ButtonType> bt = tt.showAndWait();
        if(!bt.isPresent() || bt.get() != ButtonType.OK)
            ret.clear();
        else if(ret.isEmpty()) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("no input");
            alert.showAndWait();
        }

        if(ret.isEmpty())
            System.exit(0);

        return ret;
    }
}
