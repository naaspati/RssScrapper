package scrapper.scrapper;

import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.string.stringutils.StringUtils.contains;
import static scrapper.Config.CONNECT_TIMEOUT;
import static scrapper.Config.READ_TIMEOUT;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ansi.ANSI;
import scrapper.Config;
import scrapper.Utils;


public class ScrappingResult {
    private static final ConcurrentHashMap<String, ScrappingResult> DATA;

    public static ScrappingResult get(String url) {
        return DATA.get(url);
    }
    public static ScrappingResult create(String pageUrl, List<String> childrenUrls, Path folder) {
        ScrappingResult sr = new ScrappingResult(pageUrl, childrenUrls, folder);
        DATA.put(pageUrl, sr);
        return sr;
    }
    
    public static Collection<ScrappingResult> keys() {
        return DATA.values();
    }
    
    static {
        Path p = Paths.get("app_data/ScrappingResult.dat");
        if(Files.notExists(p)) {
            DATA = new ConcurrentHashMap<>();
        } else {
            try(InputStream is = Files.newInputStream(p, StandardOpenOption.READ);
                    DataInputStream dis = new DataInputStream(is)) {
                DATA = new ConcurrentHashMap<>();

                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    ScrappingResult sr = new ScrappingResult(dis);
                    DATA.put(sr.pageUrl, sr);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }    
        }
        p = null;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Path p2 = Paths.get("app_data/ScrappingResult.dat");
            try {
                if(DATA.isEmpty())
                    Files.deleteIfExists(p2);
                else {
                    Files.createDirectories(p2.getParent());
                    try(OutputStream os = Files.newOutputStream(p2, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                            DataOutputStream dos = new DataOutputStream(os)) {
                        List<ScrappingResult> list = new ArrayList<>(DATA.values());
                        
                        dos.writeInt(list.size());
                        for (ScrappingResult sr : list) 
                            sr.write(dos);
                        
                        LoggerFactory.getLogger(ScrappingResult.class)
                        .info(ANSI.green("created: ")+p2);
                    }
                    
                }
            } catch (IOException e) {
                LoggerFactory.getLogger(ScrappingResult.class)
                .error("failed to save:"+p2, e);
            }
        }));
    }
    static String failedTaskLog() {
        StringBuilder sb = new StringBuilder();
        DATA.values().forEach(sr -> sr.appendFailedLog(sb));
        return sb.length() == 0 ? null : sb.toString();
    }
    
    public static boolean hasFailed() {
        return DATA.values().stream().anyMatch(ScrappingResult::_hasFailed);
    }
    private void appendFailedLog(StringBuilder sb) {
        synchronized (tasks) {
            int length = sb.length();
            boolean appended = false;
            sb.append(folder).append('\n');
            
            for (DownloadTask d : tasks) {
                if(d != null) {
                    appended = true;
                    sb.append("  ").append(d.urlString).append('\n');
                }
            }
            if(!appended)
                sb.setLength(length);
        }
    }

    private final String pageUrl;
    private final boolean toParent;
    private final DownloadTask[] tasks;
    private final Path folder;

    private ScrappingResult(String pageUrl, List<String> childrenUrls, Path folder) {
        Objects.requireNonNull(pageUrl, "url cannot be null");
        Objects.requireNonNull(childrenUrls, "childrenUrls cannot be null");
        Objects.requireNonNull(folder, "folder cannot be null");

        if(childrenUrls.isEmpty())
            throw new IllegalArgumentException("childrenUrls cannot be empty");

        this.pageUrl = pageUrl;
        this.folder = folder;
        this.toParent = childrenUrls.size() == 1;
        this.tasks = createTasks(childrenUrls);
    }
    private boolean _hasFailed() {
        return Stream.of(tasks).anyMatch(Objects::nonNull);
    }
    private DownloadTask[] createTasks(List<String> urls) {
        DownloadTask[] ts = new DownloadTask[urls.size()];

        for (int i = 0; i < ts.length; i++) {
            ts[i] = new DownloadTask(urls.get(i), i);
        }
        return ts;
    }
    private ScrappingResult(DataInputStream dis) throws IOException {
        this.pageUrl = dis.readUTF();
        this.folder = Paths.get(dis.readUTF());
        this.toParent = dis.readBoolean();
        
        int size = dis.readInt();
        this.tasks = new DownloadTask[size];

        for (int i = 0; i < tasks.length; i++)
            tasks[i] = new DownloadTask(dis, i);
    }
    private void write(DataOutputStream dos) throws IOException {
        dos.writeUTF(pageUrl);
        dos.writeUTF(folder.toString());
        dos.writeBoolean(toParent);

        synchronized (tasks) {
            int count = 0;
            
            for (DownloadTask d : tasks) {
                if(d != null)
                    count++;
            }
            
            dos.writeInt(count);
            for (DownloadTask d : tasks) {
                if(d != null)
                    d.write(dos);
            }
        }
    } 

    public String getUrl() { return pageUrl; }
    public int startDownload(ExecutorService ex) {
        synchronized (tasks) {
            int count = 0;
            
            for (DownloadTask d : tasks) {
                if(d != null) {
                    ex.execute(d);
                    count++;
                }
            }
            return count;
        }
    }

    private static final Logger downloadLogger = LoggerFactory.getLogger(DownloadTask.class);
    private static final int rootNameCount = Config.DOWNLOAD_DIR.getNameCount();

    private final ConcurrentSkipListSet<Path> dirsCreated = new ConcurrentSkipListSet<>();

    public class DownloadTask implements Runnable {
        private final String urlString;
        private final int index;

        private String name;

        private DownloadTask(String url, int index) {
            this.urlString = url;
            this.index = index;
        }
        private DownloadTask(DataInputStream dis, int index) throws IOException {
            this.urlString = dis.readUTF();
            this.name = dis.readUTF();
            this.index = index;
        }
        private void write(DataOutputStream dos) throws IOException {
            dos.writeUTF(urlString);
            dos.writeUTF(name);
        }

        @Override
        public void run()  {
            Path trgt = null;

            try {
                URL url = new URL(urlString);
                URLConnection con = openConnection(url);

                if(name == null)
                    name = getName(con, url);
                if(name == null)
                    return;

                trgt = toParent ? folder.resolveSibling(name) : folder.resolve(name);

                if(Files.exists(trgt)) {
                    downloadLogger.info(urlString + yellow("  SKIPPED"));
                    remove();
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

                downloadLogger.info(yellow(urlString)+"\n   "+subpath(trgt));
                remove();
            } catch (IOException|InterruptedException e) {
                downloadLogger.warn(red(urlString)+"\n  "+subpath(trgt), e);
            }
        }

        private Object subpath(Path path) {
            return path == null ? "" : path.subpath(rootNameCount, path.getNameCount());
        }
        private void remove() {
            synchronized (tasks) {
                tasks[index] = null;
            }
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
                downloadLogger.warn(red("extracting name failed: ")+urlString);
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
    }
}
