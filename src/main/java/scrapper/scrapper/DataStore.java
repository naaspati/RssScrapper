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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ansi.ANSI;
import sam.myutils.fileutils.FilesUtils;
import scrapper.Config;

public final class DataStore<E> {
    public static final DataStore<ConcurrentLinkedQueue<String>> FAILED;
    public static final DataStore<ConcurrentLinkedQueue<String>> EMPTY;
    public static final DataStore<ConcurrentLinkedQueue<String>> YOUTUBE;
    public static final DataStore<ConcurrentLinkedQueue<String>> DISABLED;

    /**
     * folder ->urls
     */
    public static final DataStore<ConcurrentHashMap<Path, ConcurrentSkipListSet<String>>> SUCCESSFUL_DOWNLOADS_MAP;
    /**
     * folder ->urls
     */
    public static final DataStore<ConcurrentHashMap<Path, ConcurrentSkipListSet<String>>> FAILED_DOWNLOADS;
    public static final DataStore<ConcurrentHashMap<Path, ConcurrentHashMap<String, DownloadTask2>>> FAILED_DOWNLOADS_MAP;

    public static final DataStore<ConcurrentSkipListSet<String>> SUCCESSFULLY_SCRAPPED;
    public static final DataStore<ConcurrentSkipListSet<String>> BAD_URLS;
    public static final DataStore<ConcurrentHashMap<String, ScrappingResult>> URL_SCRAPPING_RESULT_MAP;

    /**
     * content-type -> file.ext 
     */
    private static final DataStore<ConcurrentHashMap<String, String>> MIME_EXT_MAP;

    static {
        System.out.println("DataStore: initiated");
        
        Path p = Paths.get("app_data/backups.dat");
        Map<String, Object> temp = null;

        try {
            temp = Files.exists(p) ? FilesUtils.readObjectFromFile(p) : new HashMap<>();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> map = Collections.unmodifiableMap(temp);

        BAD_URLS = parse(map, "bad_urls", new ConcurrentSkipListSet<>());
        FAILED = parse(map, "failed", new ConcurrentLinkedQueue<String>());
        EMPTY = parse(map, "empty", new ConcurrentLinkedQueue<String>());
        YOUTUBE = parse(map, "youtube", new ConcurrentLinkedQueue<String>());
        DISABLED = parse(map, "disabled", new ConcurrentLinkedQueue<String>());
        SUCCESSFULLY_SCRAPPED = parse(map, "successfullyScrapped", new ConcurrentSkipListSet<String>());

        URL_SCRAPPING_RESULT_MAP = parse(map, "urlScrappingResultMap", new ConcurrentHashMap<String, ScrappingResult>());

        String key = "mime_extMap";
        MIME_EXT_MAP = map.containsKey(key) ?  parse(map, key, null) : new DataStore<ConcurrentHashMap<String,String>>(readFileext_mimeMap(), key);

        SUCCESSFUL_DOWNLOADS_MAP = parsePathMap(map, "successfulDownloadsMap");
        FAILED_DOWNLOADS = parsePathMap(map, "failed_downloads");
        FAILED_DOWNLOADS_MAP = parsePathMap(map, "failed_downloads_map");

        Runtime.getRuntime().addShutdownHook(new Thread(DataStore::write));
    }

    private static <E> DataStore<E> parse(Map<String, Object> map, String key, E defaultValue){
        @SuppressWarnings("unchecked")
        E data = (E) map.get(key);
        if(data == null)
            data = defaultValue;

        return new DataStore<>(data, key);
    }

    @SuppressWarnings("unchecked")
    private static <E> DataStore<ConcurrentHashMap<Path, E>> parsePathMap(Map<String, Object> map, String key) {
        Object[][] data = (Object[][]) map.get(key);
        if(data == null)
            data = new Object[0][0];

        ConcurrentHashMap<Path,E> ret = new ConcurrentHashMap<>();

        for (Object[] o : data) 
            ret.put(Paths.get((String)o[0]), (E)o[1]);

        return new DataStore<ConcurrentHashMap<Path,E>>(ret, key);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void write() {
        HashMap<String, Object> map = new HashMap<>();

        for (DataStore d : new DataStore[] {FAILED, EMPTY, YOUTUBE,DISABLED, BAD_URLS,
                SUCCESSFULLY_SCRAPPED, URL_SCRAPPING_RESULT_MAP, 
                MIME_EXT_MAP }) {
            map.put(d.key, d.data);
        }

        for (DataStore<ConcurrentHashMap<Path, Object>> d : new DataStore[] { SUCCESSFUL_DOWNLOADS_MAP, FAILED_DOWNLOADS, FAILED_DOWNLOADS_MAP}) {
            Object[][] data = new Object[d.data.size()][2];

            int[] index = {0}; 
            d.data.forEach((s,t) -> {
                data[index[0]][0] = s.toString();
                data[index[0]++][1] = t;
            });
            map.put(d.key, data);
        }

        Path p = Paths.get("app_data/backups.dat");
        try {
            Files.createDirectories(p.getParent());
            FilesUtils.writeObjectToFile(map, p);
            LoggerFactory.getLogger(DataStore.class).info(ANSI.green("created: ")+p);
        } catch (IOException e) {
            LoggerFactory.getLogger(DataStore.class).error(ANSI.red("failed to write: ")+p, e);
        }
    }
    private  static ConcurrentHashMap<String, String> readFileext_mimeMap() {
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
                    Files.write(p, d.data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("failed to write: "+p, e);
            }
        }

        Path p = root.resolve(FAILED_DOWNLOADS.key+".txt");

        try {
            FAILED_DOWNLOADS.getData().values().removeIf(Collection::isEmpty);

            if(FAILED_DOWNLOADS.getData().isEmpty()) 
                Files.deleteIfExists(p);
            else {
                logger.info(FAILED_DOWNLOADS.key +": "+FAILED_DOWNLOADS.data.values().stream().mapToInt(Collection::size).sum());
                
                StringBuilder sb = new StringBuilder();
                FAILED_DOWNLOADS.getData()
                .forEach((s,t) -> {
                    sb.append(s).append('\n');
                    t.forEach(z -> sb.append('\t').append(z).append('\n'));
                    sb.append('\n');
                });

                sb.setLength(sb.length() - 1);
                Files.write(p, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("failed to write: "+p, e);
        }
    }
    private final String key;
    private final E data;

    private DataStore(E data, String key) {
        this.data = data;
        this.key = key;
    }
    public E getData() {
        return data;
    }
    public String getKey() {
        return key;
    }
    public static String getExt(String mime) {
        return MIME_EXT_MAP.getData().get(mime);
    }
}
