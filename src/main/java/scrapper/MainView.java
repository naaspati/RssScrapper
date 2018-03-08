package scrapper;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import sam.console.ansi.ANSI;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import scrapper.scrapper.AbstractScrapper;
import scrapper.scrapper.Callable2;
import scrapper.scrapper.DataStore;
import scrapper.scrapper.DownloadTask;
import scrapper.scrapper.ScrappingResult;
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

        //TODO
        boolean isDownload = getParameters().getRaw().contains("--download");
        final Collection<String> urls =  isDownload ? null : inputDialog();

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

    @Override
    public void stop() throws Exception {
        System.exit(0);
    }
    ArrayList<AbstractScrapper> scrappers;
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
            DataStore.BAD_URLS.getData().addAll(urls);

        if(scrappers.isEmpty())
            showErrorAndExit(urls.isEmpty() ? "failed : no urls" : "failed : only bad urls");

        info(cyan("Total: ")+scrappers.stream().mapToInt(AbstractScrapper::size).sum());
        info(cyan("---------------------------------------------"));
        String format = "%-25s%s";
        info(yellow(String.format(format, "method_name", "count")));
        scrappers.forEach(s -> s.printCount(format));
        info(cyan("---------------------------------------------"));
        info("\n");

        Map<Boolean, List<Callable2>> tasks = 
                scrappers.stream()
                .flatMap(AbstractScrapper::tasks)
                .collect(partitioningBy(Callable2::isCompleted));

        List<ScrappingResult> completedTasks = tasks.getOrDefault(true, new ArrayList<>()).stream().map(Callable2::getCompleted).collect(toList());
        List<Callable<ScrappingResult>> remainingTasks = tasks.getOrDefault(false, new ArrayList<>()).stream().map(Callable2::getTask).collect(toList());;

        if(remainingTasks.isEmpty() && completedTasks.isEmpty())
            showErrorAndExit("no tasks found, to perform");

        scrappers.stream().map(AbstractScrapper::getInfoBox)
        .collect(toCollection(infoBoxes::getChildren));

        String format2 = "remaining: %d  | total: "+total+ "  | thread-count: %d%n";
        final double total2 = total;
        CountDownLatch latch = new CountDownLatch(remainingTasks.size());

        stage.setTitle("Scrapping");
        AtomicInteger threadCount = new AtomicInteger(0);

        AbstractUpdatable scrapperUpdate = new AbstractUpdatable() {
            @Override
            public void update() {
                long i = latch.getCount();
                status.setText(String.format(format2, i, threadCount.get()));
                progressBar.setProgress((total2 - i) / total2);
            }
        };

        AtomicInteger totalDownload = new AtomicInteger(0);
        AtomicInteger completedDownload = new AtomicInteger(0);
        String format3 = "completed: %d  | total: %d | thread-count: %d%n";

        AbstractUpdatable downloadUpdate = new AbstractUpdatable() {
            @Override
            public void update() {
                status.setText(String.format(format3, completedDownload.get(), totalDownload.get(), threadCount.get()));
                progressBar.setProgress(completedDownload.get() / (double)totalDownload.get());
            }
        };

        ThreadPoolExecutor ex = new ThreadPoolExecutor(Config.THREAD_COUNT, Config.THREAD_COUNT, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                threadCount.set(getActiveCount());

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
                        e.printStackTrace();
                    }

                    if(s == null || s == ScrappingResult.YOUTUBE || s == ScrappingResult.SUCCESSFUL)
                        return;

                    DownloadTask[] dts = s.toDownloadTasks();
                    if(dts != null) {
                        totalDownload.addAndGet(dts.length);

                        for (DownloadTask d : dts)
                            execute(d);
                    }

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
        remainingTasks.forEach(ex::submit);

        ex.execute(() -> {
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
                stage.sizeToScene();

                info("\n");
                info(ANSI.createBanner("DOWNLOADING"));

                downloadStarted = true;
                Updatables.remove(scrapperUpdate);
                Updatables.add(downloadUpdate);
            });

            downloadStarted = true;

            int[] count = {0};
            completedTasks.stream()
            .map(ScrappingResult::toDownloadTasks)
            .filter(Objects::nonNull)
            .filter(ar -> ar.length != 0)
            .flatMap(Stream::of)
            .forEach(d -> {
                count[0]++;
                ex.execute(d);
            });

            totalDownload.addAndGet(count[0]);
            downloadUpdate.setChanged();

            Thread shutdown = new Thread(() -> {
                try {
                    save();
                } catch (IOException e) {
                    logger.error("failed to save", e);
                    System.exit(0);
                }
            });

            Runtime.getRuntime().addShutdownHook(shutdown);

            ex.execute(() -> {
                Thread t = new Thread(() -> {
                    ex.shutdown();
                    try {
                        ex.awaitTermination(3, TimeUnit.DAYS);
                    } catch (InterruptedException e) {
                        logger.error("ex.awaitTermination(3, TimeUnit.DAYS); Interrupted", e);
                    }
                    
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                    shutdown.start();
                    try {
                        stop();
                    } catch (Exception e) {
                        logger.error("error while stopping", e);
                    }
                });
                t.setDaemon(true);
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

        FilesUtils.openFile(Config.DOWNLOAD_DIR.toFile());
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
