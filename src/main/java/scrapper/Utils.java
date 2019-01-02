package scrapper;

import static sam.string.StringUtils.contains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.string.StringUtils.StringSplitIterator;

public class Utils {
	private static final Logger LOGGER = Utils.logger(Utils.class);
	
	private Utils() {}

	public static final String APP_DATA = "app_data";
	private static final Path MY_DIR = Paths.get(APP_DATA, Utils.class.getName());
	public static final Path DOWNLOAD_DIR;
	public static final int CONNECT_TIMEOUT;
	public static final int READ_TIMEOUT;
	public static final int THREAD_COUNT;

	private static final Collection<Runnable> tasks = new ArrayList<>();
	
	private static final Map<String, String> ALL_MIME_EXT_MAP = new HashMap<>();
	private static final Map<String, String> FREQUENT_MIME_EXT_MAP = new HashMap<>();

	private static volatile boolean all_loaded, frequent_loaded;
	private static final Object mime_mutex = new Object();
	private static int frequent_initial_size;

	static {
		System.out.println("Config: initiated");
		ResourceBundle rb = ResourceBundle.getBundle("config");

		CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect.timeout"));
		READ_TIMEOUT = Integer.parseInt(rb.getString("read.timeout"));
		DOWNLOAD_DIR = Paths.get(rb.getString("download.dir"));
		THREAD_COUNT = Integer.parseInt(rb.getString("thread.count"));

		ResourceBundle.clearCache();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (tasks) {
				tasks.forEach(Runnable::run);
			}
		}));
		
		tasks.add(() -> {
			synchronized (mime_mutex) {
				if(frequent_loaded && frequent_initial_size != FREQUENT_MIME_EXT_MAP.size())
					writeMimeCache(frequentMimeFilePath(), FREQUENT_MIME_EXT_MAP);
				
				if(all_loaded) {
					Path p = allMimeFilePath();
					if(Files.notExists(p))
						writeMimeCache(p, ALL_MIME_EXT_MAP);
				}
			}
		});
	}

	public static String getFileName(URL url) {
		String s = url.getPath();
		int start = s.lastIndexOf('/');
		int end = s.length(); 
		if(start == end - 1) {
			start = s.lastIndexOf('/', start - 1);
			end--;
		}
		return s.substring(start + 1, end);
	}

	public static void addShutDownTask(Runnable runnable) {
		synchronized (tasks) {
			tasks.add(runnable);
		}
	}
	public static void removeShutdownHook(Runnable runnable){
		synchronized (tasks) {
			tasks.remove(runnable);
		}
	}

	public static Logger logger(Class<?> cls) {
		return LoggerFactory.getLogger(cls);
	}
	public static Logger logger(String loggername) {
		return LoggerFactory.getLogger(loggername);
	}
	
	private static void writeMimeCache(Path p, Map<String, String> map) {
		try(OutputStream os = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				OutputStreamWriter oss = new OutputStreamWriter(os, "utf-8");
				BufferedWriter writer = new BufferedWriter(oss);) {
			for (Entry<String, String> e : map.entrySet()) {
				writer.append(e.getKey())
				.append('\t')
				.append(e.getValue())
				.append('\n');
			}
		} catch (IOException e) {
			LOGGER.error("failed to write"+p, e);
		}
	}

	public static String getExt(String mime) {
		synchronized(mime_mutex) {
			if(!frequent_loaded) {
				frequent_loaded = true;
				loadMimeCache(frequentMimeFilePath(), FREQUENT_MIME_EXT_MAP);
				frequent_initial_size = FREQUENT_MIME_EXT_MAP.size();
			}

			String s = FREQUENT_MIME_EXT_MAP.get(mime);
			if(s != null) 
				return s;

			if(!all_loaded) {
				all_loaded = true;
				if(!loadMimeCache(allMimeFilePath(), ALL_MIME_EXT_MAP))
					readFileext_mimeMap(ALL_MIME_EXT_MAP);
			}
			s = ALL_MIME_EXT_MAP.get(mime);
			if(s == null) {
				LOGGER.warn("not associated ext found with mime: \""+mime+"\"");
				return s;
			}
			
			FREQUENT_MIME_EXT_MAP.put(mime, s);
			return s;
		}
	}
	
	private static Path frequentMimeFilePath() {
		return MY_DIR.resolve("frequent-mime-cache.tsv");
	}
	private static Path allMimeFilePath() {
		return MY_DIR.resolve("all-mime-cache.tsv");
	}
	private static boolean loadMimeCache(Path p, Map<String, String> map) {
		if(Files.notExists(p))
			return false;
		
		try {
			Files.lines(p).forEach(s -> {
				int n = s.indexOf('\t');
				map.put(s.substring(0, n), s.substring(n+1));
			});
		} catch (IOException e) {
			throw new RuntimeException("failed reading \""+p+"\"", e);
		}
		LOGGER.debug("loaded: {}", p);
		return true;
	}

	private  static void readFileext_mimeMap(Map<String, String> map) {
		String filename = "1509617391333-file.ext-mime.tsv";
		try(InputStream is = ClassLoader.getSystemResourceAsStream(filename);
				InputStreamReader reader = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(reader)) {

			br.lines()
			.forEach(s -> {
				if(s.startsWith("#") || !contains(s, '\t'))
					return;
				StringSplitIterator sp = new StringSplitIterator(s, '\t', Integer.MAX_VALUE);
				sp.next();
				map.put(sp.next(), sp.next());
			});
			LOGGER.debug("loaded: {}", filename);	
		} catch (IOException e) {
			throw new RuntimeException("failed reading \""+filename+"\"", e);
		}
	}
}
