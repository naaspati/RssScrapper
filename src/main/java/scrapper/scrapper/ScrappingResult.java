package scrapper.scrapper;

import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.string.StringUtils.contains;
import static scrapper.EnvConfig.CONNECT_TIMEOUT;
import static scrapper.EnvConfig.READ_TIMEOUT;

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




import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ANSI;
import scrapper.EnvConfig;
import scrapper.Utils;


public class ScrappingResult {
    private static final ConcurrentHashMap<String, ScrappingResult> DATA = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> EXISTING = new ConcurrentHashMap<>();

    public static ScrappingResult get(String url) {
        return DATA.get(url);
    }
    public static ScrappingResult create(String pageUrl, List<String> list, Path folder) {
        list.removeIf(t -> t == null || t.trim().isEmpty() || EXISTING.putIfAbsent(t, true) != null);

        ScrappingResult sr = new ScrappingResult(pageUrl, list, folder);
        DATA.put(pageUrl, sr);
        return sr;
    }
    public static Collection<ScrappingResult> keys() {
        return DATA.values();
    }

    static {
        read();
        addShutdownHook();
    }

    private static void read() {
        Path p = Paths.get("app_data/ScrappingResult.dat");
        if(Files.exists(p)) {
            try(InputStream is = Files.newInputStream(p, StandardOpenOption.READ);
                    GZIPInputStream gis = new GZIPInputStream(is);
                    DataInputStream dis = new DataInputStream(gis)) {

                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    ScrappingResult sr = new ScrappingResult(dis);
                    DATA.put(sr.pageUrl, sr);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Path p = Paths.get("app_data/ScrappingResult.dat");
            try {
                if(DATA.isEmpty())
                    Files.deleteIfExists(p);
                else {
                    Files.createDirectories(p.getParent());
                    
                    try(OutputStream os = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                            GZIPOutputStream gos = new GZIPOutputStream(os);
                            DataOutputStream dos = new DataOutputStream(gos)) {
                        
                        List<ScrappingResult> list = new ArrayList<>(DATA.values());

                        dos.writeInt(list.size());
                        for (ScrappingResult sr : list) 
                            sr.write(dos);

                        LoggerFactory.getLogger(ScrappingResult.class)
                        .info(ANSI.green("created: ")+p);
                    }

                }
            } catch (IOException e) {
                LoggerFactory.getLogger(ScrappingResult.class)
                .error("failed to save:"+p, e);
            }
        }));
    }

    private static final String END_MARKER = "\0\0\0END\0\0\0"; 
    private void write(DataOutputStream dos) throws IOException {
        dos.writeUTF(pageUrl);
        dos.writeUTF(folder.toString());
        dos.writeBoolean(toParent);

        int count[] = {0};
        forEach(d -> count[0]++);

        dos.writeInt(count[0]);

        if(count[0] == 0)
            return;

        int t = count[0];
        count[0] = 0;

        forEach(d -> {
            try {
                count[0]++;
                dos.writeUTF(d.urlString);
                dos.writeBoolean(d.name != null);
                if(d.name != null)
                    dos.writeUTF(d.name);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        if(count[0] != t)
            dos.writeUTF(END_MARKER); // null safety
    } 
    public ScrappingResult(DataInputStream dis) throws IOException {
        this.pageUrl = dis.readUTF();
        this.folder = Paths.get(dis.readUTF());
        this.toParent = dis.readBoolean();

        int size = dis.readInt();

        if(size == 0) {
            this.tasks = new AtomicReferenceArray<>(0);
        } else {
            this.tasks = new AtomicReferenceArray<>(size);

            for (int n = 0; n < size; n++) {
                String url = dis.readUTF();

                if(url.equals(END_MARKER))
                    break;

                String name = dis.readBoolean() ? dis.readUTF() : null;
                tasks.set(n, new DownloadTask(url, name, n));
            }
        }
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
        int length = sb.length();
        sb.append(folder).append('\n');
        int length2 = sb.length();

        forEach(d -> sb.append("  ").append(d.urlString).append('\n'));

        if(sb.length() == length2)
            sb.setLength(length);
    }

    private final String pageUrl;
    private final boolean toParent;
    private final AtomicReferenceArray<DownloadTask> tasks;
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
    private AtomicReferenceArray<DownloadTask> createTasks(List<String> urls) {
        DownloadTask[] ts = new DownloadTask[urls.size()];

        for (int i = 0; i < ts.length; i++)
            ts[i] = new DownloadTask(urls.get(i), i);
        return new AtomicReferenceArray<DownloadTask>(ts);
    }

    public ScrappingResult(String pageUrl, boolean toParent, AtomicReferenceArray<DownloadTask> tasks, Path folder) {
        this.pageUrl = pageUrl;
        this.toParent = toParent;
        this.tasks = tasks;
        this.folder = folder;
    }
    public String getUrl() { return pageUrl; }
    public int size() { return tasks.length(); }
    public int startDownload(ExecutorService ex) {
        int count[] = {0};

        forEach(d -> {
            ex.execute(d);
            count[0]++;
        });

        return count[0];
    }
    public Stream<DownloadTask> failedTasks() {
        return IntStream.range(0, tasks.length()).mapToObj(tasks::get).filter(Objects::nonNull);
    }

    public void forEach(Consumer<DownloadTask> consumer) {
        for (int i = 0; i < tasks.length(); i++) {
            DownloadTask d = tasks.get(i);
            if(d != null)
                consumer.accept(d);
        }
    }

    private static final Logger downloadLogger = LoggerFactory.getLogger(DownloadTask.class);
    private static final int rootNameCount = EnvConfig.DOWNLOAD_DIR.getNameCount();

    private final ConcurrentSkipListSet<Path> dirsCreated = new ConcurrentSkipListSet<>();

    public class DownloadTask implements Runnable {
        private final String urlString;
        private final int index;

        private String name;

        private DownloadTask(String url, int index) {
            this(url, null, index);
        }
        private DownloadTask(String url, String name, int index) {
            this.urlString = Objects.requireNonNull(url);
            this.name = name;
            this.index = index;
        }
        public String getUrl() {
            return urlString;
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
                    downloadLogger.info(urlString + yellow("  SKIPPED: ")+"[exists: "+subpath(trgt)+"]");
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
            tasks.set(index, null);
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
