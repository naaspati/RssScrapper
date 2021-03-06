package scrapper;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.io.serilizers.IOExceptionConsumer;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.string.StringUtils.StringSplitIterator;
import scrapper.scrapper.Config;

public class Utils {
	private static final Logger LOGGER = Utils.logger(Utils.class);

	private Utils() {}

	public static final Path APP_DATA = Paths.get("app_data");
	public static final Path DOWNLOAD_DIR;
	public static final int CONNECT_TIMEOUT;
	public static final int READ_TIMEOUT;
	public static final int THREAD_COUNT;
	public static final int BUFFER_SIZE;

	private static final Collection<Runnable> tasks = new ArrayList<>();

	private static final Map<String, String> ALL_MIME_EXT_MAP = new HashMap<>();
	private static final Map<String, String> FREQUENT_MIME_EXT_MAP = new HashMap<>();

	private static volatile boolean all_loaded, frequent_loaded;
	private static final Object mime_mutex = new Object();
	private static int frequent_initial_size;

	static {
		ResourceBundle rb = ResourceBundle.getBundle("config");

		CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect.timeout"));
		READ_TIMEOUT = Integer.parseInt(rb.getString("read.timeout"));
		DOWNLOAD_DIR = Paths.get(rb.getString("download.dir"));
		THREAD_COUNT = Integer.parseInt(rb.getString("thread.count"));
		BUFFER_SIZE = Integer.parseInt(rb.getString("buffer.size"));

		ResourceBundle.clearCache();
		MyUtilsException.noError(() -> Files.createDirectories(APP_DATA));

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
	public static Logger logger(String prefix, String suffix) {
		return LoggerFactory.getLogger(prefix+"("+suffix +")");
	}

	private static void writeMimeCache(Path p, Map<String, String> map) {
		try(OutputStream os = Files.newOutputStream(p, CREATE, TRUNCATE_EXISTING);
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
		return APP_DATA.resolve("frequent-mime-cache.tsv");
	}
	private static Path allMimeFilePath() {
		return APP_DATA.resolve("all-mime-cache.tsv");
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

	private static final String[] numbers = new String[100];
	public static final Path TEMP_DIR = MyUtilsPath.TEMP_DIR;

	static {
		for (int i = 0; i < numbers.length; i++) 
			numbers[i] = Integer.toString(i);
	}

	public static String toString(int i) {
		if(i >= 0 && i < 100)
			return numbers[i];
		return Integer.toString(i);
	}

	public static Config[] configs() throws InstantiationException, IllegalAccessException, ClassNotFoundException, JSONException, IOException {
		JSONObject json = new JSONObject(new JSONTokener(Files.newInputStream(Paths.get("configs.json"), READ)));
		Config[] configs = new Config[json.length()];

		int n = 0;
		for (String s : json.keySet()) 
			configs[n++] = new Config(s, json.getJSONObject(s));

		return configs;
	}

	public static void exit() {
		StackTraceElement t = Thread.currentThread().getStackTrace()[2]; 
		if(!(t.getFileName().equals(MainView.class.getSimpleName()+".java") && t.getClassName().equals(MainView.class.getName())))
			throw new IllegalAccessError("only accessfrom: "+MainView.class.getName());

		synchronized (tasks) {
			tasks.forEach(Runnable::run);
		}
	}

	public static Stream<String> lines(Path path) throws IOException {
		return Files.lines(path)
				.filter(s -> !s.isEmpty() && s.charAt(0) != '#');
	}
	public static <E> E readList(Path path, Logger logger, Collector<String, ?, E> collector) {
		try {
			E e = lines(path).collect(collector);
			logger.debug("read: "+path);
			return e;
		} catch (IOException e) {
			LOGGER.error("failed to read: "+path+", error: "+e);
		}
		return null;
	}
	private static BufferedWriter writer(Path path) throws IOException {
		return Files.newBufferedWriter(path, CREATE, APPEND);
	}
	
	public static void writeWithDate(IOExceptionConsumer<BufferedWriter> write, Path path, Logger logger) {
		try(BufferedWriter writer = writer(path)) {
			writer.write("#" + LocalDateTime.now()+"\n");
			write.accept(writer);
			writer.write('\n');
			writer.flush();
		} catch (IOException e) {
			logger.error("failed to write: "+path+", where: "+Thread.currentThread().getStackTrace()[3], e);
			return ;
		}
		logger.info("saved: "+path);
	}
	
	public static void writeWithDate(Iterable<String> list, Path path, Logger logger) {
		writeWithDate(writer -> {
			for (String s : list) {
				writer.write(s);
				writer.write('\n');
			}
		}, path, logger);
	}
	@SuppressWarnings("rawtypes")
	public static void writeWithDate(Map<?, ?> map, Path path, Logger logger) {
		if(map.isEmpty())
			return;
		
		writeWithDate(writer -> {
			for (Entry e : map.entrySet()) {
				writer.append(toString(e.getKey())).append('\t')
				.append(toString(e.getValue())).append('\n');
			}
		}, path, logger);
	}
	public static CharSequence toString(Object key) {
		return key == null ? "" : key.toString();
	}
	public static void linesTabSeparated(Path path, BiConsumer<String, String> action) throws IOException {
		lines(path)
		.forEach(s -> {
			int n = s.indexOf('\t');
			if(n >= 0)
				action.accept(s.substring(0, n), s.substring(n+1));
		});
	}
	public static Map<String, String> readMap(Path path, Logger logger) {
		Map<String, String> map = new HashMap<>();
		try {
			linesTabSeparated(path, map::put);
			logger.debug("read: "+path);
		} catch (IOException e) {
			logger.error("failed to read: "+path+", where: "+Thread.currentThread().getStackTrace()[2], e);
		}
		return map;
	}

	public static void write(Path path, Iterable<String> itr, Logger logger) {
		try {
			Files.write(path, itr, CREATE, TRUNCATE_EXISTING);
			logger.debug("saved: "+path);
		} catch (IOException e) {
			logger.error("failed to read: "+path+", where: "+Thread.currentThread().getStackTrace()[2], e);
		}
	}
}
