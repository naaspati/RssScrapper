package scrapper.scrapper;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static sam.string.stringutils.StringUtils.contains;
import static sam.string.stringutils.StringUtils.split;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ansi.ANSI;
import scrapper.Config;

public final class DataStore<E extends Collection<String>> {
    public static final DataStore<List<String>> FAILED;
    public static final DataStore<List<String>> EMPTY;
    public static final DataStore<List<String>> YOUTUBE;
    public static final DataStore<List<String>> DISABLED;

    public static final DataStore<Set<String>> BAD_URLS;

    /**
     * content-type -> file.ext 
     */
    private static final ConcurrentHashMap<String, String> MIME_EXT_MAP;

    static {
        System.out.println("DataStore: initiated");

        Function<List<String>, List<String>> mapper =  list -> list;

        BAD_URLS = parse("bad_urls", HashSet::new);
        FAILED = parse("failed", mapper);
        EMPTY = parse("empty", mapper);
        YOUTUBE = parse("youtube", mapper);
        DISABLED = parse("disabled", mapper);

        MIME_EXT_MAP = readFileext_mimeMap();

        Runtime.getRuntime().addShutdownHook(new Thread(DataStore::write));
    }

    private static <E extends Collection<String>> DataStore<E> parse(String key, Function<List<String>, E> listMapper){
        Path p = savePath(key);
        try {
            List<String> list = Files.notExists(p) ? new ArrayList<>() : Files.readAllLines(p); 
            return new DataStore<>(listMapper.apply(list), key);
        } catch (IOException e) {
            throw new RuntimeException("failed to read: "+p, e);
        }
    }

    private static Path savePath(String key) {
        return Paths.get("app_data/"+key+".txt");
    }

    private static final ReentrantLock writeLock = new ReentrantLock();

    @SuppressWarnings("unchecked")
    private static void write() {
        try {
            writeLock.lock();
            Path root = Paths.get("app_data");

            try {
                Files.createDirectories(root); 
            } catch (IOException e) {
                LoggerFactory.getLogger(DataStore.class).error(ANSI.red("failed to creatDir: ")+root, e);
                return;
            }

            for (DataStore<Collection<String>> d : new DataStore[] {FAILED, EMPTY, YOUTUBE,DISABLED, BAD_URLS }) {
                try {
                    Files.write(savePath(d.key), d.data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    LoggerFactory.getLogger(DataStore.class).error(ANSI.red("failed to write: ")+d.key.concat("txt"), e);                
                }
            }
        } finally {
            writeLock.unlock();
        } 
    }
    private  static ConcurrentHashMap<String,String> readFileext_mimeMap() {
        try(InputStream is = ClassLoader.getSystemResourceAsStream("1509617391333-file.ext-mime.tsv");
                InputStreamReader reader = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(reader)) {

            return br.lines()
                    .filter(s -> !s.startsWith("#") && contains(s, '\t'))
                    .map(s -> split(s, '\t'))
                    .collect(collectingAndThen(toMap(s -> s[1], s -> s[2], (o, n) -> n), ConcurrentHashMap::new));

        } catch (IOException e) {
            throw new RuntimeException("failed reading 1509617391333-file.ext-mime.tsv", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void saveClientCopy() {
        try {
            writeLock.lock();

            Path root = Config.DOWNLOAD_DIR;

            Logger logger = LoggerFactory.getLogger(DataStore.class);
            logger.info("\n"+ANSI.createBanner("SUMMERY"));

            for (DataStore<Collection<String>> d : new DataStore[] {BAD_URLS, FAILED, EMPTY, YOUTUBE, DISABLED}) {
                Path p = root.resolve(d.key+".txt");
                try {
                    if(d.data.isEmpty())
                        Files.deleteIfExists(p);
                    else {
                        logger.info(d.key +": "+d.data.size());
                        Files.write(p, new LinkedHashSet<>(d.data), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } catch (IOException e) {
                    logger.error("failed to write: "+p, e);
                }
            }

            Path p = root.resolve("failed.txt");
            String failed = ScrappingResult.failedTaskLog();

            try {
                if(failed == null) 
                    Files.deleteIfExists(p);
                else 
                    Files.write(p, failed.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                logger.error("failed to write: "+p, e);
            }

        } finally {
            writeLock.unlock();
        } 
    }
    private final String key;
    private final E data;

    private DataStore(E data, String key) {
        this.data = data;
        this.key = key;
    }
    public synchronized void add(String s) {
        data.add(s);
    }
    public String getKey() {
        return key;
    }
    public static String getExt(String mime) {
        return MIME_EXT_MAP.get(mime);
    }

    public synchronized void addAll(Collection<String> urls) {
        data.addAll(urls);
    }

    public synchronized void remove(String url) {
        data.remove(url);
    }
}
