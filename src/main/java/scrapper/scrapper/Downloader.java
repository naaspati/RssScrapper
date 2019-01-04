package scrapper.scrapper;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static sam.console.ANSI.red;
import static sam.string.StringUtils.contains;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import sam.console.ANSI;
import sam.internetutils.InternetUtils;
import sam.myutils.Checker;
import sam.reference.WeakQueue;
import scrapper.ScrappingException;
import scrapper.Utils;

public interface Downloader {
	public boolean download(String url, Path dir) throws ScrappingException, IOException;	
}

class DefaultDownloader implements Downloader {
	private static final Logger LOGGER = Utils.logger(DefaultDownloader.class);
	protected static final KeySetView<Path, Boolean> dirsCreated = ConcurrentHashMap.newKeySet();
    protected static final Set<Path> tempFiles = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    protected static final WeakQueue<byte[]> buffers = new WeakQueue<>(true, () -> new byte[Utils.BUFFER_SIZE]);
    protected static final KeySetView<String, Boolean> downloaded_urls = ConcurrentHashMap.newKeySet();
    protected static final int DOWNLOAD_DIR_COUNT = Utils.DOWNLOAD_DIR.getNameCount();

	protected static final String FILE_NAME_MARKER = "filename=\"";
	protected static final AtomicInteger COUNTER = new AtomicInteger(0); 
	
	static {
		Path p2 = Utils.APP_DATA.resolve( "downloaded_urls.txt");
		
		if(Files.exists(p2)) {
			try {
				Files.lines(p2).forEach(downloaded_urls::add);
			} catch (IOException e) {
				LOGGER.error("failed to read: "+p2+", error: "+e);
			}
		}
		
    	Utils.addShutDownTask(() -> {
    		tempFiles.forEach(t -> {
				try {
					Files.deleteIfExists(t);
				} catch (IOException e) {}
			});
    		Path p = Utils.APP_DATA.resolve( "downloaded_urls.txt");
    		
    		try {
				Files.write(p, downloaded_urls, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
				LOGGER.info("saved: "+p);
			} catch (IOException e) {
				LOGGER.error("failed to save: "+p+", error: "+e);
			}
    	});
    }
	

	protected String getName(URLConnection c, URL url, String urlString) {
		String field = c.getHeaderField("Content-Disposition");

		String name = null;
		if(field != null) {
			field = contains(field, '\'') ? field.replace('\'', '"') : field;
			int start = field.indexOf(FILE_NAME_MARKER);
			if(start > 0) {
				int end = field.indexOf('\"', start + FILE_NAME_MARKER.length() + 1);
				if(end > 0)
					name = field.substring(start + FILE_NAME_MARKER.length(), end);
			}
		}

		if(name == null) {
			String ext = null;

			String mime = c.getContentType();
			if(mime != null) {
				if(contains(mime, ';'))
					mime = mime.substring(0, mime.indexOf(';'));

				ext = Utils.getExt(mime);
			}
			name = Utils.getFileName(url);

			if(ext != null)
				name = name.endsWith(ext) ? name : (name + ext);
		}

		if(name == null) {
			Utils.logger(getClass()).warn(red("extracting name failed: ")+urlString);
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

	@Override
	public boolean download(String url, Path dir) throws ScrappingException, IOException {
		if(Checker.isEmptyTrimmed(url))
			throw new ScrappingException("bad url '"+url+"'");
		
		if(downloaded_urls.contains(url))
			return true;
		
		URL url2 = new URL(url);
		URLConnection con = InternetUtils.connection(url2);
		String name = getName(con, url2, url);
		Path trgt;
		
		if(name == null) {
			trgt = countedPath(dir);
			LOGGER.warn("file name not found: ".concat(url));
		} else 
			trgt = dir.resolve(name);
		
		Path parent = trgt.getParent();
		if(!dirsCreated.contains(parent)) {
            Files.createDirectories(parent);
            dirsCreated.add(parent);
        }
		
		byte[] bytes = buffers.poll();
		tempFiles.add(trgt);
		
		try(InputStream is = con.getInputStream();
				OutputStream fos = Files.newOutputStream(trgt,  TRUNCATE_EXISTING, CREATE)) {
			
			int n = 0;
        	while((n = is.read(bytes)) > 0) 
        		fos.write(bytes, 0, n);
        	
        	tempFiles.remove(trgt);
        	LOGGER.info(ANSI.yellow(url)+"\n   "+trgt.subpath(DOWNLOAD_DIR_COUNT, trgt.getNameCount()));
		} finally {
			buffers.offer(bytes);
		}
		return true;
	}

	protected static Path countedPath(Path dir) {
		Path p = dir.resolve(Utils.toString(COUNTER.incrementAndGet()));
		while(Files.exists(p))
			p = dir.resolve(Utils.toString(COUNTER.incrementAndGet()));
		return p;
	}
}

