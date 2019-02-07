package scrapper.scrapper;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static sam.console.ANSI.red;
import static sam.string.StringUtils.contains;
import static scrapper.Utils.APP_DATA;
import static scrapper.Utils.BUFFER_SIZE;
import static scrapper.Utils.CONNECT_TIMEOUT;
import static scrapper.Utils.DOWNLOAD_DIR;
import static scrapper.Utils.READ_TIMEOUT;
import static scrapper.Utils.addShutDownTask;
import static scrapper.Utils.getExt;
import static scrapper.Utils.getFileName;
import static scrapper.Utils.logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sam.internetutils.ConnectionConfig;
import sam.internetutils.InternetUtils;
import sam.myutils.Checker;
import sam.reference.WeakPool;
import scrapper.ScrappingException;
import scrapper.Utils;

public interface Downloader {
	public boolean download(String url, PathResolverWrap resolver) throws ScrappingException, IOException;	
}

class DefaultDownloader implements Downloader {
	private static final Logger LOGGER = logger(DefaultDownloader.class);

	private static class Temp56 {
		final Path path;
		boolean downloaded;

		public Temp56(Path path, boolean downloaded) {
			this.path = path;
			this.downloaded = downloaded;
		}
	}

	protected static final Set<Path> dirsCreated;
	protected static final Set<String> old_downloaded;
	protected static final Map<String, Temp56> new_downloaded = new ConcurrentHashMap<>();
	protected static final Map<String, String> url_path_map;
	protected static final Set<Path> existing_path;

	protected static final int DOWNLOAD_DIR_COUNT = DOWNLOAD_DIR.getNameCount();

	protected static final String FILE_NAME_MARKER = "filename=\"";
	protected static final AtomicInteger COUNTER = new AtomicInteger(0);
	protected static final ConnectionConfig config = new ConnectionConfig(CONNECT_TIMEOUT, READ_TIMEOUT, false, false, false, BUFFER_SIZE, null);
	protected static final WeakPool<byte[]> buffers = new WeakPool<>(true, () -> new byte[BUFFER_SIZE]);

	static {
		Path downloaded_urls_path = APP_DATA.resolve( "downloaded_urls.txt");
		Path url_path_map_path = APP_DATA.resolve( "url_path_map.txt");
		
		old_downloaded = Optional.ofNullable(Utils.readList(downloaded_urls_path, LOGGER, Collectors.toSet()))
				.filter(set -> !set.isEmpty())
				.map(Collections::synchronizedSet)
				.orElse(Collections.emptySet());
		
		url_path_map = Optional.ofNullable(Utils.readMap(url_path_map_path, LOGGER))
				.filter(set -> !set.isEmpty())
				.map(Collections::synchronizedMap)
				.orElse(Collections.emptyMap()); 
		
		LOGGER.debug("old_download.size(): ", old_downloaded.size());
		LOGGER.debug("url_path_map.size(): ", url_path_map.size());

		Map<Boolean, List<Path>> temp = null;

		if(Files.exists(DOWNLOAD_DIR)) {
			try {
				temp = Files.walk(DOWNLOAD_DIR)
						.collect(Collectors.partitioningBy(Files::isDirectory));
			} catch (IOException e) {
				LOGGER.error("failed to walk: "+DOWNLOAD_DIR, e);
			}
		}

		if(Checker.isEmpty(temp)) {
			existing_path = Collections.emptySet();
			dirsCreated = ConcurrentHashMap.newKeySet();
		} else {
			List<Path> list = temp.get(false);
			existing_path = Checker.isEmpty(list) ? Collections.emptySet() : list.size() == 1 ? Collections.singleton(list.get(0)) : Collections.synchronizedSet(new HashSet<>(list));
			list = temp.get(true);
			dirsCreated = ConcurrentHashMap.newKeySet(Checker.isEmpty(list) ? 50 : list.size() + 10);
			if(Checker.isNotEmpty(list))
				dirsCreated.addAll(list);
		}

		addShutDownTask(() -> {
			if(new_downloaded.isEmpty())
				return;
			
			if(new_downloaded.values().stream().anyMatch(t -> !t.downloaded)) {
				Utils.writeWithDate(writer -> {
					for (Entry<String, Temp56> e : new_downloaded.entrySet()) {
						if(!e.getValue().downloaded) {
							try {
								Files.deleteIfExists(e.getValue().path);
							} catch (Exception e2) {}
							
							writer.append(e.getKey()).append('\t')
							.append(Utils.toString(e.getValue().path)).append('\n');
						}
 					}
				}, url_path_map_path, LOGGER);
			}
			
			if(new_downloaded.values().stream().anyMatch(t -> t.downloaded)) {
				Utils.writeWithDate(writer -> {
					for (Entry<String, Temp56> e : new_downloaded.entrySet()) {
						if(e.getValue().downloaded) {
							writer.append(e.getKey()).append('\n');
						}
 					}
				}, downloaded_urls_path, LOGGER);
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

				ext = getExt(mime);
			}
			name = getFileName(url);

			if(ext != null)
				name = name.endsWith(ext) ? name : (name + ext);
		}

		if(name == null) {
			logger(getClass()).warn(red("extracting name failed: ")+urlString);
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
	
	private static final Temp56 TEMP = new Temp56(null, false);

	@Override
	public boolean download(String url, PathResolverWrap dir) throws ScrappingException, IOException {
		if(Checker.isEmptyTrimmed(url))
			throw new ScrappingException("bad url '"+url+"'");

		if(new_downloaded.getOrDefault(url, TEMP).downloaded || old_downloaded.contains(url))
			return true;

		URL url2 = new URL(url);
		URLConnection con = InternetUtils.connection(url2, config);
		Path trgt;
		String trgts = url_path_map.get(url);
		
		if(trgts != null) {
			trgt = Paths.get(trgts);
		} else {
			String name = getName(con, url2, url);
			
			if(name == null) {
				trgt = countedPath(dir, url);
				LOGGER.warn("file name not found: ".concat(url));
			} else 
				trgt = dir.resolve(name, url);
		}
		
		if(existing_path.contains(trgt)) {
			LOGGER.info("SKIPPED(file_exists): "+url+", "+trgt.subpath(DOWNLOAD_DIR_COUNT, trgt.getNameCount()));
			new_downloaded.put(url, new Temp56(trgt, true));
			return true;
		}

		Path parent = trgt.getParent();
		if(!dirsCreated.contains(parent)) {
			Files.createDirectories(parent);
			dirsCreated.add(parent);
		}

		byte[] bytes = buffers.poll();
		Temp56 temp = new Temp56(trgt, false);
		new_downloaded.put(url, temp);

		try(InputStream is = con.getInputStream();
				OutputStream fos = Files.newOutputStream(trgt,  TRUNCATE_EXISTING, CREATE)) {

			int n = 0;
			while((n = is.read(bytes)) > 0) 
				fos.write(bytes, 0, n);
			
			temp.downloaded = true;
		} finally {
			buffers.offer(bytes);
		}
		return true;
	}

	protected static Path countedPath(PathResolverWrap dir, String url) {
		Path p = dir.resolve(Utils.toString(COUNTER.incrementAndGet()), url);
		while(Files.exists(p))
			p = dir.resolve(Utils.toString(COUNTER.incrementAndGet()), url);
		return p;
	}
}

