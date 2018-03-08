package scrapper.scrapper;

import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.string.stringutils.StringUtils.contains;
import static scrapper.Config.CONNECT_TIMEOUT;
import static scrapper.Config.READ_TIMEOUT;
import static scrapper.scrapper.DataStore.FAILED_DOWNLOADS;
import static scrapper.scrapper.DataStore.FAILED_DOWNLOADS_MAP;
import static scrapper.scrapper.DataStore.SUCCESSFUL_DOWNLOADS_MAP;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scrapper.Config;
import scrapper.Utils;

public class DownloadTask implements Runnable {
    private static final ConcurrentSkipListSet<Path> dirsCreated = new ConcurrentSkipListSet<>();

    /** folder -> urls */
    transient static final ConcurrentHashMap<Path, ConcurrentSkipListSet<String>> successfulDownloadsMap = SUCCESSFUL_DOWNLOADS_MAP.getData();
    /**  folder -> urls */
    static final ConcurrentHashMap<Path, ConcurrentSkipListSet<String>> failed_downloads = FAILED_DOWNLOADS.getData();
    static final ConcurrentHashMap<Path, ConcurrentHashMap<String, DownloadTask2>> failed_downloads_map = FAILED_DOWNLOADS_MAP.getData();

    private static final Logger logger = LoggerFactory.getLogger(DownloadTask.class);

    private final String urlString;
    private final boolean toParent;
    private String name;
    private Path folder;

    public DownloadTask(String url, Path parentFolder, boolean toParent) {
        this.urlString = url;
        this.folder = parentFolder;
        this.toParent = toParent;
    }
    public DownloadTask(DownloadTask2 task) {
        this.urlString = task.urlString;
        this.folder = Paths.get(task.folder);
        this.toParent = task.toParent;
    }
    @Override
    public void run()  {
        Path trgt = null;

        try {
            ConcurrentSkipListSet<String> list = successfulDownloadsMap.get(folder);

            if(list == null)
                successfulDownloadsMap.putIfAbsent(folder, list = new ConcurrentSkipListSet<>());
            else if(list.contains(urlString)) {
                logger.info(urlString+ yellow("  SKIPPED"));
                removeFailed();
                return;
            }

            URL url = new URL(urlString);
            URLConnection con = openConnection(url);

            if(name == null)
                name = getName(con, url);
            if(name == null)
                return;

            trgt = toParent ? folder.resolveSibling(name) : folder.resolve(name);

            if(Files.exists(trgt)) {
                logger.info(urlString+ yellow("  SKIPPED"));
                removeFailed();
                return;
            }

            Path temp = trgt.resolveSibling(name+".tmp");
            Path parent = temp.getParent();
            if(!dirsCreated.contains(parent)) {
                Files.createDirectories(parent);
                dirsCreated.add(parent);
            }

            try(InputStream is = con.getInputStream();
                    BufferHandler buffer = new BufferHandler();
                    ) {
                buffer.pipe(is, temp);
            }

            Files.move(temp, trgt, StandardCopyOption.REPLACE_EXISTING);

            success(trgt);
            list.add(urlString);
        } catch (IOException|InterruptedException e) {
            logger.warn(red(urlString)+"\n  "+subpath(trgt), e);
            addFailed();
        }
    }

    private void addFailed() {
        ConcurrentSkipListSet<String> list = failed_downloads.get(folder);

        if(list == null)
            failed_downloads.putIfAbsent(folder, list = new ConcurrentSkipListSet<>());

        list.add(urlString);

        ConcurrentHashMap<String, DownloadTask2> map = failed_downloads_map.get(folder);

        if(map == null)
            failed_downloads_map.putIfAbsent(folder, map = new ConcurrentHashMap<>());

        map.put(urlString, new DownloadTask2(urlString, toParent, name, folder));
    }

    private void success(Path path) {
        logger.info(yellow(urlString)+"\n   "+subpath(path));
        removeFailed();
    }
    private static final int count = Config.DOWNLOAD_DIR.getNameCount();
    private Object subpath(Path path) {
        return path == null ? "" : path.subpath(count, path.getNameCount());
    }
    private void removeFailed() {
        ConcurrentSkipListSet<String> list = failed_downloads.get(folder);

        if(list != null)
            list.remove(urlString);

        ConcurrentHashMap<String, DownloadTask2> map = failed_downloads_map.get(folder);

        if(map != null)
            map.remove(urlString);
    }

    private static final String fileNameMarker = "filename=\"";
    private String getName(URLConnection c, URL url) {
        String field = c.getHeaderField("Content-Disposition");

        String name = null;
        if(field != null) {
            field = contains(field, '\'') ? field.replace('\'', '"') : field;
            int start = field.indexOf(fileNameMarker);
            if(start > 0) {
                int end = field.indexOf('\"', start + fileNameMarker.length() + 1);
                if(end > 0)
                    name = field.substring(start + fileNameMarker.length(), end);
            }
        }

        if(name == null) {
            String ext = null;

            String mime = c.getContentType();
            if(mime != null) {
                if(contains(mime, ';'))
                    mime = mime.substring(0, mime.indexOf(';'));

                ext = DataStore.getExt(mime);
            }
            name = Utils.getFileName(url);

            if(ext != null)
                name = name.endsWith(ext) ? name : (name + ext);
        }

        if(name == null) {
            logger.warn(red("extracting name failed: ")+urlString);
            return null;
        }

        int index = name.lastIndexOf('.');
        if(index > 0) {
            String ext = name.substring(index);
            if(contains(ext, ' '))
                return name + urlString.hashCode(); 

            return name.substring(0, index) + urlString.hashCode() + ext; 
        }
        return name + urlString.hashCode();
    }

    private URLConnection openConnection(URL url) throws IOException {
        URLConnection con = url.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.connect();

        return con;
    }
    public static boolean isDownloaded(Path folder, String url) {
        ConcurrentSkipListSet<String> list = successfulDownloadsMap.get(folder);
        return list != null && list.contains(url);
    }
}

