package scrapper;
import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.createUnColoredBanner;
import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import scrapper.abstractscrapper.AbstractScrapper;
import scrapper.abstractscrapper.AbstractScrapper.Callable2;
import scrapper.abstractscrapper.AbstractScrapper.DownloadTask;
import scrapper.abstractscrapper.AbstractScrapper.Store;
import scrapper.commons.Commons;
import scrapper.specials.Specials;

public final class MainView extends Application {
    private static volatile boolean downloadStarted = false; 
    private static final Path rootDir;
    private static final int THREAD_COUNT;

    static {
        System.out.println(ANSI.green("MainView initiated"));
        
        ResourceBundle rb = ResourceBundle.getBundle("config");
        THREAD_COUNT = Integer.parseInt(rb.getString("thread_count"));
        rootDir = Paths.get(rb.getString("root.dir"));
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


        System.out.println("\n");
        System.out.println(yellow("total urls: "+total));
        System.out.println(green("good urls: "+scrappers.stream().mapToInt(AbstractScrapper::size).sum()));
        System.out.println(red("bad urls: "+urls.size()));

        writeList("bad_urls.txt", urls);

        if(scrappers.isEmpty())
            showErrorAndExit(urls.isEmpty() ? "failed : no urls" : "failed : only bad urls");

        Iterator<AbstractScrapper> miterator = scrappers.iterator();
        List<String> failedDirMk = new ArrayList<>();

        while(miterator.hasNext()){
            AbstractScrapper m = miterator.next();

            try {
                Files.createDirectories(m.getPath());
            } catch (Exception e) {
                failedDirMk.add(m+"\n  "+m.getPath()+"\n  "+e+"\n");
                failedDirMk.add(String.join("\n   ", m.getUrls()));
                failedDirMk.add("\n");
                miterator.remove();
            }
        }

        if(!failedDirMk.isEmpty()){
            failedDirMk.add(0, createUnColoredBanner("failed to create dir")+"\n\n");
            Files.write(rootDir.resolve("failed-dir-make.txt"), failedDirMk, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        failedDirMk = null;
        miterator = null;

        if(scrappers.isEmpty())
            showErrorAndExit(urls.isEmpty() ? "failed : no urls" : "failed : only bad urls");

        System.out.println(cyan("Total: ")+scrappers.stream().mapToInt(AbstractScrapper::size).sum());
        System.out.println(cyan("---------------------------------------------"));
        String format = "%-25s%s\n";
        System.out.printf(yellow(String.format(format, "method_name", "count")));
        scrappers.forEach(s -> s.printCount(format));
        System.out.println(cyan("---------------------------------------------"));
        System.out.println("\n");

        Map<Boolean, List<Callable2>> tasks = 
                scrappers.stream()
                .flatMap(AbstractScrapper::tasks)
                .collect(Collectors.partitioningBy(Callable2::isCompleted));

        List<Store> completedTasks = tasks.getOrDefault(true, new ArrayList<>()).stream().map(Callable2::getCompleted).collect(Collectors.toList());
        List<Callable<Store>> remainingTasks = tasks.getOrDefault(false, new ArrayList<>()).stream().map(Callable2::getTask).collect(Collectors.toList());;

        if(remainingTasks.isEmpty() && completedTasks.isEmpty())
            showErrorAndExit("no tasks found, to perform");

        scrappers.stream().map(AbstractScrapper::getInfoBox)
        .collect(Collectors.toCollection(infoBoxes::getChildren));

        ExecutorService ex = Executors.newFixedThreadPool(THREAD_COUNT);

        AtomicInteger remaining = new AtomicInteger(remainingTasks.size() + 1);
        String format2 = "remaining: %d  | total: "+total+ "  | thread-count: %d%n";
        final double total2 = total;

        stage.setTitle("Scrapping");

        AbstractScrapper.setProgressor(() -> {
            int r = remaining.decrementAndGet();
            status.setText(String.format(format2, r, Thread.activeCount()));
            progressBar.setProgress((total2 - r) / total2);
        }, true);

        Thread t = new Thread(() -> {
            try {
                System.out.println("\n\n");
                System.out.println(ANSI.createBanner("SCRAPPING"));

                Stream<Store> remainingStream = Stream.empty(); 

                if(!remainingTasks.isEmpty()) {
                    Collections.shuffle(remainingTasks);
                    List<Future<Store>> futures = ex.invokeAll(remainingTasks);

                    remainingStream = futures.stream()
                            .map(f -> {
                                try {
                                    return f.get();
                                } catch (InterruptedException | ExecutionException| NullPointerException e) {}
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .filter(s -> s != Store.YOUTUBE && s != Store.SUCCESSFUL);
                }
                else
                    System.out.println(red("nothing to scrap"));

                Platform.runLater(() -> {
                    stage.setTitle("Downloading");
                    stage.getScene().setRoot(new VBox(progressBar, status));
                    progressBar.setProgress(0);
                    status.setPadding(new Insets(10));
                    stage.sizeToScene();
                });

                System.out.println("\n\n");
                System.out.println(ANSI.createBanner("DOWNLOADING"));

                List<DownloadTask> list = 
                        Stream.concat(completedTasks.stream(), remainingStream)
                        .flatMap(Store::toDownloadTasks)
                        .collect(Collectors.toList());

                String format3 = "remaining: %d  | total: "+list.size()+ "  | thread-count: %d%n";
                double total3 = list.size();

                list.removeIf(Objects::isNull);

                if(!list.isEmpty()) {
                    remaining.set(list.size() + 1);

                    AbstractScrapper.setProgressor(() -> {
                        int r = remaining.decrementAndGet();
                        status.setText(String.format(format3, r, Thread.activeCount()));
                        progressBar.setProgress((total3 - r) / total3);
                    }, true);

                    downloadStarted = true;
                    Collections.shuffle(list);
                    list.forEach(ex::execute);
                    ex.execute(() -> {
                        while(true) {
                            if(remaining.get() == 0)
                                System.exit(0);
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {}
                        }
                    });
                    ex.shutdown();
                }
                else {
                    System.out.println(red("nothing to download"));
                    System.exit(0);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                try {
                    save();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        t.setDaemon(true);
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
            } catch (IOException e) {
                System.out.println("failed to save:"+e);
                System.exit(0);
            }
        }));
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
            System.out.println(red("\n\nstopped at scrapping"));
            return;
        }

        if(saved)
            return;

        saved = true;

        Map<String, Path> map = AbstractScrapper.getFailedDownloads();

        writeList("failed-rss-owl.txt", groupByHost(AbstractScrapper.getFailed()), false);
        writeList("failed-downlods.txt", groupByHost(map.keySet())
                .stream().map(s -> s +"\t"+ (map.get(s) == null ? "" : map.get(s)))
                .collect(Collectors.toList()),
                false);    
        writeList("new 1.txt", AbstractScrapper.getYoutube());

        System.out.println(yellow("\nfile walking"));

        try {
            List<File> dirs = Files.walk(rootDir)
                    .skip(1)
                    .filter(Files::isDirectory)
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            if(!dirs.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    Iterator<File> iterator = dirs.iterator();
                    while (iterator.hasNext()) {
                        File f = iterator.next();
                        String[] files = f.list();
                        if(files == null)
                            iterator.remove();
                        else if(files.length == 1) {
                            new File(f, files[0]).renameTo(new File(f + files[0]));
                            try {
                                Files.deleteIfExists(f.toPath());
                                iterator.remove();
                            } catch (Exception e) {}
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(red("failed: "+MyUtils.exceptionToString(e)));
        }

        FilesUtils.openFile(rootDir.toFile());
        MyUtils.beep(5);

        System.out.println(red("Failed: ")+AbstractScrapper.getFailed().size());
        System.out.println(red("Download Failed: ")+AbstractScrapper.getFailedDownloads().size());
        System.out.println("\n\n"+FINISHED_BANNER);
    }

    private Collection<String> groupByHost(Collection<String> list) {
        if(list == null || list.isEmpty())
            return list;

        List<String> list2 = new ArrayList<>(); 

        list
        .stream()
        .collect(Collectors.groupingBy(s -> {
            try {
                return new URL(s).getHost();
            } catch (Exception e) {}
            return null;
        }))
        .forEach((host, lst) -> {
            list2.add("\n-----------------------\n"+host+"\n-----------------------\n");
            list2.addAll(lst);
        });

        return list2;
    }
    private void writeList(String path, Collection<String> urls) throws IOException {
        writeList(path, urls, true);
    }
    private void writeList(String pathString, Collection<String> urls, boolean append) throws IOException {
        if(urls.isEmpty()) {
            if(!append) {
                try {
                    Files.deleteIfExists(rootDir.resolve(pathString));
                } catch (IOException e) {}
            }
            return;
        }

        Path path = rootDir.resolve(pathString);
        if(append)
            Files.write(rootDir.resolve(path),  Stream.concat(Files.exists(path) ? Files.lines(path) : Stream.empty(), urls.stream()).collect(Collectors.toCollection(LinkedHashSet::new)), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        else
            Files.write(rootDir.resolve(path),  urls, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);    
    }

    private Collection<String> inputDialog() {
        Dialog<String> tt = new Dialog<>();
        tt.setTitle("RSS OWL");
        TextArea ta = new TextArea();
        ta.setPrefRowCount(20);

        tt.getDialogPane().setExpandableContent(ta);
        tt.getDialogPane().setExpanded(true);
        tt.setHeaderText("Enter Urls seperated by newline");
        tt.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        tt.setResultConverter(b -> b == ButtonType.OK ? ta.getText() : null);

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
                        System.out.println(ANSI.red(path + " deleted"));
                        AbstractScrapper.clear();
                    } catch (IOException e1) {}    
                });
                tt.getDialogPane().setContent(null);
            });
        }
         */


        Collection<String> list = tt.showAndWait().map(strs -> 
        Stream.of(strs.split("\r?\n"))
        .map(s -> s.replace('"', ' ').trim())
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet())).orElse(new HashSet<>()); 

        if(list.isEmpty()) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("no input");
            alert.showAndWait();
            System.exit(0);
        }

        return list;
    }
}
