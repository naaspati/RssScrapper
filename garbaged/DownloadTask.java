package scrapper.scrapper;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.string.StringUtils.contains;
import static scrapper.Utils.CONNECT_TIMEOUT;
import static scrapper.Utils.READ_TIMEOUT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import scrapper.Utils;
import scrapper.Utils;

class DownloadTask implements Runnable {
	private static final Logger LOGGER = Utils.logger(DownloadTask.class);
    private static final int ROOT_NAME_COUNT = Utils.DOWNLOAD_DIR.getNameCount();
    private static final KeySetView<Path, Boolean> dirsCreated = ConcurrentHashMap.newKeySet();
    private static final Set<Path> tempFiles = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    
    private static final LinkedBlockingQueue<byte[]> BUFFERS = new LinkedBlockingQueue<>((int)(Utils.THREAD_COUNT*1.5));
    
    static {
    	Utils.addShutDownTask(() -> {
    		if(tempFiles.isEmpty()) {
    			tempFiles.forEach(t -> {
					try {
						Files.deleteIfExists(t);
					} catch (IOException e) {}
				});
    		}
    	});
    }
	
    final String urlString;
    final int index;

    String name;
	private final DownloadTasks result;

    DownloadTask(String url, int index, DownloadTasks result) {
        this(url, null, index, result);
    }
    DownloadTask(String url, String name, int index, DownloadTasks result) {
    	this.result = result;
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

            trgt = result.toParent ? result.folder.resolveSibling(name) : result.folder.resolve(name);

            if(Files.exists(trgt)) {
                LOGGER.info(urlString + yellow("  SKIPPED: ")+"[exists: "+subpath(trgt)+"]");
                remove();
                return;
            }

            tempFiles.add(trgt);
            Path parent = trgt.getParent();
            if(!dirsCreated.contains(parent)) {
                Files.createDirectories(parent);
                dirsCreated.add(parent);
            }
            
            byte[] bytes = BUFFERS.poll(1, TimeUnit.SECONDS);
            if(bytes == null)
            	bytes = new byte[8*1024];

            try(InputStream is = con.getInputStream();
            		OutputStream fos = Files.newOutputStream(trgt, TRUNCATE_EXISTING, CREATE)) {
            	int n = 0;
            	while((n = is.read(bytes)) > 0) 
            		fos.write(bytes, 0, n);
            	
            	tempFiles.remove(trgt);
            } finally {
				BUFFERS.offer(bytes);
			}
            
            LOGGER.info(yellow(urlString)+"\n   "+subpath(trgt));
            remove();
        } catch (IOException|InterruptedException e) {
            LOGGER.warn(red(urlString)+"\n  "+subpath(trgt), e);
        }
    }

    private Object subpath(Path path) {
        return path == null ? "" : path.subpath(ROOT_NAME_COUNT, path.getNameCount());
    }
    private void remove() {
        result.remove(index);
    }
}
