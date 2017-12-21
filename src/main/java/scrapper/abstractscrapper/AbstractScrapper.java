package scrapper.abstractscrapper;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jaunt.Element;
import com.jaunt.NotFound;
import com.jaunt.UserAgent;

import javafx.application.Platform;
import sam.console.ansi.ANSI;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import sam.myutils.renamer.RemoveInValidCharFromString;
import scrapper.InfoBox;

public abstract class AbstractScrapper {
    protected static final Path rootDir;
    private static volatile Runnable progressor;
    private static final List<String> failed = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Path> failed_downloads = new ConcurrentHashMap<>();
    private static final List<String> youtube = Collections.synchronizedList(new ArrayList<>());
    private static final List<Path> createdFolders = Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> successfuls;
    private static final Map<String, Store> urlStoreMap;
    private static final  Map<String, Map<String, Integer>> urlSavePathMap;
    private static final Map<String, String> fileext_mimeMap; 
    protected static final int CONNECT_TIMEOUT, READ_TIMEOUT, THREAD_COUNT, ROOT_DIR_NAME_COUNT;
    private static final BlockingQueue<UserAgent> userAgents;

    static {
        System.out.println(ANSI.green("AbstractScrapper initiated"));

        ResourceBundle rb = ResourceBundle.getBundle("config");

        CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect_timeout"));
        READ_TIMEOUT = Integer.parseInt(rb.getString("read_timeout"));
        rootDir = Paths.get(rb.getString("root.dir"));
        ROOT_DIR_NAME_COUNT = rootDir.getNameCount();
        THREAD_COUNT = Integer.parseInt(rb.getString("thread_count"));
        userAgents = new LinkedBlockingQueue<>(THREAD_COUNT);

        successfuls = readObject("successfuls.dat", Collections.synchronizedSet(new HashSet<>()));  
        urlStoreMap =  readObject("urlStoreMap.dat", new ConcurrentHashMap<>());
        urlSavePathMap = readObject("urlSavePathMap.dat", new ConcurrentHashMap<>());

        List<String> youtubeStore = new ArrayList<>();
        List<String> successfulStore = new ArrayList<>();

        urlStoreMap.forEach((url, store) -> {
            if(Store.YOUTUBE.url.equals(store.url))
                youtubeStore.add(url);
            else if(Store.SUCCESSFUL.url.equals(store.url))
                successfulStore.add(url);
        });
        youtubeStore.forEach(s -> urlStoreMap.put(s, Store.YOUTUBE));
        successfulStore.forEach(s -> urlStoreMap.put(s, Store.SUCCESSFUL));

        Map<String, String> map2 = null;

        try(InputStream is = ClassLoader.getSystemResourceAsStream("1509617391333-file.ext-mime.tsv");
                InputStreamReader reader = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(reader)) {

            map2 = br.lines()
                    .filter(s -> !s.startsWith("#") && s.indexOf('\t') > 0).map(s -> s.split("\t"))
                    .collect(Collectors.toMap(s -> s[1], s -> s[2], (o, n) -> n, ConcurrentHashMap::new));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        fileext_mimeMap = map2;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writeObject("successfuls.dat", successfuls, successfuls.isEmpty());  
            writeObject("urlStoreMap.dat", urlStoreMap, urlStoreMap.isEmpty());
            writeObject("urlSavePathMap.dat", urlSavePathMap, urlSavePathMap.isEmpty());

            createdFolders.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {}
            });
        }));
    }
    private static final AtomicInteger remainingUserAgents = new AtomicInteger();  
    protected static UserAgent takeUserAgent() throws InterruptedException {
        if(remainingUserAgents.get() == 0) {
            UserAgent ag = new UserAgent();
            ag.settings.responseTimeout = READ_TIMEOUT;
            return ag;
        }
        UserAgent ag = userAgents.take();
        remainingUserAgents.decrementAndGet();
        return ag;
    }
    protected static void putUserAgent(UserAgent agent) {
        if(agent != null &&  !userAgents.contains(agent) && userAgents.offer(agent))
            remainingUserAgents.incrementAndGet();
    } 
    public static void setProgressor(Runnable runnable, boolean run) {
        progressor = runnable;
        if(run) Platform.runLater(progressor);
    }
    private static void writeObject(String pathString, Object object, boolean dontSave) {
        if(dontSave)
            return;

        try {
            FilesUtils.writeObjectToFile(object, Paths.get(pathString));            
        } catch (IOException e) {
            System.out.println(red("failed to save object: ")+pathString);
        }
    }
    private static <E> E readObject(String pathString, E defaultValue) {
        Path path = Paths.get(pathString);
        if(Files.notExists(path))
            return defaultValue;

        try {
            return  FilesUtils.readObjectFromFile(path); 
        } catch (ClassNotFoundException | IOException e) {
            System.out.println(red("failed to read: ")+path);
        }
        return defaultValue;
    }
    protected static boolean testSuperClass(Class<?> clazz, Class<?> superclass) {
        Class<?> c = clazz.getSuperclass();

        while(true) {
            if(c == null)
                return false;
            if(c == superclass)
                return true;
            c = c.getSuperclass();
        }
    }
    private final InfoBox infoBox = new InfoBox(this.getClass().getSimpleName().toLowerCase());
    public InfoBox getInfoBox() {
        return infoBox;
    }
    public int failedCount() {
        return infoBox.getFailedCount();
    }
    public abstract boolean isEmpty();
    public abstract  int size();
    public abstract Collection<String> getUrls();
    public abstract Path getPath();
    public Stream<Callable2> tasks() {
        infoBox.setTotal(size());
        return _tasks();
    }
    protected abstract Stream<Callable2>  _tasks();
    public abstract void printCount(String format);
    protected void progressCompleted() {
        infoBox.progress(true);
        if(progressor != null)
            Platform.runLater(progressor);
    }
    protected Callable2 checkSuccessful(String url) {
        Store s = urlStoreMap.get(url);
        if(s == null)
            return null;

        System.out.println(url +"  "+green("SKIPPED"));
        progressCompleted();
        return new Callable2(s);
    }
    protected Store progress(Store store) {
        infoBox.progress(store != null);

        if(store != null)
            urlStoreMap.put(store.url, store);

        if(progressor != null)
            Platform.runLater(progressor);
        return store;
    }
    protected String prepareName(org.jsoup.nodes.Document doc, URL _url) {
        return Optional.ofNullable(doc.title())
                .map(RemoveInValidCharFromString::removeInvalidCharsFromFileName)
                .orElseGet(() -> RemoveInValidCharFromString.removeInvalidCharsFromFileName(new File(_url.getFile()).getName()));
    }
    protected String prepareName(com.jaunt.Document doc, String _url) {
        try {
            String name = doc.findFirst("<title>").getText();
            if(name == null)
                throw new NullPointerException();
            return  RemoveInValidCharFromString.removeInvalidCharsFromFileName(name);
        } catch (NotFound|NullPointerException e) {
            try {
                return RemoveInValidCharFromString.removeInvalidCharsFromFileName(new File(new URL(_url).getFile()).getName());
            } catch (MalformedURLException e1) {
                return String.valueOf(System.currentTimeMillis());
            }
        }
    }
    protected void addFailed(String url) {
        failed.add(url);        
    }
    public static List<String> getFailed() {
        return failed;
    }
    public static Map<String, Path> getFailedDownloads() {
        return failed_downloads;
    }    
    public static List<String> getYoutube() {
        return youtube;
    }
    protected boolean testYoutube(org.jsoup.nodes.Document doc) {
        return testYoutube(doc.getElementsByTag("iframe").stream()
                .map(e -> e.attr("src")));
    }
    protected boolean testYoutube(com.jaunt.Document doc) {
        Stream.Builder<String> b = Stream.builder();
        
        for (Element e : doc.findEach("<iframe>")) {
            try {
                b.accept(e.getAt("src"));
            } catch (NotFound e2) {}
        }
        return testYoutube(b.build());
    }
    private boolean testYoutube(Stream<String> stream) {
        int size = getYoutube().size();
        
        stream
        .filter(Objects::nonNull)
        .filter(s -> {
            URL u;
            try {
                u = new URL(s);
                return u.getHost().contains(".youtu");
            } catch (MalformedURLException e) {}
            return false;
        })
        .forEach(getYoutube()::add);
        
        return size != getYoutube().size();
    }
    /**
     * 
     * @param urls
     * @param testAgainst
     * @param actionOnMatch calls the consumer as (url, name) -> {}
     */
    protected static void urlsFilter(Collection<String> urls, Collection<String> testAgainst, BiConsumer<String, String> actionOnMatch) {
        Iterator<String> itr = urls.iterator();

        while(itr.hasNext()){
            String url = itr.next();
            int start = -1;
            try {
                start = url.contains("feedproxy.google.com/~r/") ? url.indexOf("~r/") + 3 : url.indexOf("//") + 2;   
            } catch (Exception e) {
                continue;
            }

            if(start + 1 < url.length()){
                String part = url.substring(start, url.indexOf('/', start + 1));

                for (String name : testAgainst) {
                    if(part.contains(name)){
                        actionOnMatch.accept(url, name);
                        itr.remove();
                        break;
                    }
                }               
            }
        }
    }
    protected static void print(String urlString, int size, Exception e) {
        String str;
        if(e == null)
            str = urlString+"  "+(size != -1 ? yellow("("+size+")") : red(" EMPTY"));
        else
            str = urlString+"  "+red(e);

        System.out.println(str);
    }
    protected static void printError(String msg, Exception e, String origin) {
        System.out.println(red("{ msg: "+msg
                +", origin: "+ origin+
                ", Error: "+MyUtils.exceptionToString(e)+"}"));
    }
    public static class Store implements Serializable {
        private static final long serialVersionUID = 1845258022364933736L;

        public static final Store SUCCESSFUL = new Store("--SUCCESSFUL--SUCCESSFUL--", null, Paths.get("."));
        public static final Store YOUTUBE = new Store("--YOUTUBE--YOUTUBE--", null, Paths.get("."));

        final String url;
        final List<String> urlsList;
        final File folderFile; 
        transient Path folderPath;

        public Store(String url, List<String> urlsList, Path folder) {
            this.url = url;
            this.urlsList = urlsList;
            this.folderFile  = folder.toFile();
            this.folderPath = folder;
        }
        public Stream<DownloadTask> toDownloadTasks(){
            if(urlsList == null || urlsList.isEmpty())
                return Stream.empty();

            Path folder = null;
            try {
                Path folder2 = folder = folderPath == null ? folderFile.toPath() : folderPath;
                Map<String, Integer> map = urlSavePathMap.get(url);
                Files.createDirectories(folder);
                createdFolders.add(folder);

                if(map == null) {
                    map = new HashMap<>();
                    urlSavePathMap.put(url, map);
                }

                Stream.Builder<DownloadTask> builder = Stream.builder();

                if(urlsList.size() == 1) {
                    String u = urlsList.get(0);

                    if(!successfuls.contains(u))
                        builder.accept(new DownloadTask(u, folder2, map.getOrDefault(u, -1)));
                    return builder.build();
                }

                int max[] = {map.values().stream().mapToInt(Integer::intValue).max().orElse(0)};

                for (String u : urlsList) {
                    if(!successfuls.contains(u)) {
                        Integer i = map.get(u);
                        if(i == null) {
                            ++max[0];
                            map.put(u, max[0]);
                            builder.accept(new DownloadTask(u, folder2, max[0]));
                        }
                        else
                            builder.accept(new DownloadTask(u, folder2, i));
                    }
                }
                return builder.build();
            } catch (IOException|InvalidPathException|NullPointerException e) {
                failed.add(url);
                printError("failed to create dir: "+folder, e, "Store.toDownloadTasks");
            }
            return Stream.empty();

        }
    }
    public static class DownloadTask implements Runnable {
        final String url;
        final Path folder;
        final int name;

        public DownloadTask(String url, Path parentFolder, int name) {
            this.url = url;
            this.folder = parentFolder;
            this.name = name;
        }

        @Override
        public void run()  {
            Path target = name == -1 ? folder.resolveSibling(folder.getFileName()+".jpg") : folder.resolve(name+".jpg");;

            try {
                URLConnection con = new URL(url).openConnection();
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");
                con.setConnectTimeout(CONNECT_TIMEOUT);
                con.setReadTimeout(READ_TIMEOUT);
                con.connect();

                String ext = Optional.ofNullable(con.getContentType())
                        .map(fileext_mimeMap::get)
                        .orElse(".jpg"); 

                target = name == -1 ? folder.resolveSibling(folder.getFileName()+ext) : folder.resolve(name+ext);

                InputStream is = con.getInputStream();
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                is.close();

                System.out.println(url+"\n"+yellow(target.subpath(ROOT_DIR_NAME_COUNT, target.getNameCount())));

                successfuls.add(url);
            } catch (IOException e) {
                failed_downloads.put(url, target);
                printError("failed: "+url, e, "DownloadTask.call");
            }
            Platform.runLater(progressor);
        }
    }
    public static class Callable2 {
        final Store completed;
        final Callable<Store> task;

        public Callable2(Callable<Store> task) {
            this.completed = null;
            this.task = task;
        }
        public Callable2(Store completed) {
            this.completed = completed;
            this.task = null;
        }
        public boolean isCompleted() {
            return completed != null;
        }
        public Store getCompleted() {
            return completed;
        }
        public Callable<Store> getTask() {
            return task;
        }
    }
}
