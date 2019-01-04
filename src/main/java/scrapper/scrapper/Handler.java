package scrapper.scrapper;

import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static scrapper.scrapper.ScrappingResult.COMPLETED;
import static scrapper.scrapper.ScrappingResult.EMPTY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;

import sam.collection.ArraysUtils;
import sam.console.ANSI;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.string.StringUtils.StringSplitIterator;
import scrapper.ScrappingException;
import scrapper.Utils;
import scrapper.scrapper.Selector.YoutubeList;

public class Handler implements Closeable {
	private static final String FAILED_MARKER;
	private static final String EMPTY_MARKER;
	private static final String COMPLETED_MARKER;
	private static final String YOUTUBE_MARKER;
	private static final String DOWNLOAD_STATUS_MARKER;

	static {
		char[] chars = new char[40];

		FAILED_MARKER = marker("FAILED", chars);
		EMPTY_MARKER = marker("EMPTY", chars);
		COMPLETED_MARKER = marker("COMPLETED", chars);
		YOUTUBE_MARKER = marker("YOUTUBE", chars);
		DOWNLOAD_STATUS_MARKER = marker("DOWNLOAD_STATUS", chars);
	}
	private static String marker(String string, char[] chars) {
		int n = chars.length/2 - string.length()/2;
		chars[0] = '|';

		for (int i = 1; i < n; i++) 
			chars[i] = '#';

		string.getChars(0, string.length(), chars, n);
		chars[n - 1] = ' ';
		chars[n  + string.length()] = ' ';

		chars[chars.length - 1] = '|';

		for (int i = n  + string.length() + 1; i < chars.length - 1; i++) 
			chars[i] = '#';

		return new String(chars);
	}
	
	public static Handler[] loadCached() throws IOException {
		String[] str = MY_DIR.toFile().list();
		if(Checker.isEmpty(str))
			return new Handler[0];
		
		str = ArraysUtils.removeIf(str, s -> !s.endsWith(".txt"));
		Handler[] hs = new Handler[str.length];
		int n = 0;
		
		for (String s : str) 
			hs[n++] = new Handler(null, s.substring(0, s.length() - 4));
		
		return hs;
	}

	private static final Logger LOGGER = Utils.logger(Handler.class);
	private static final Path MY_DIR = Utils.APP_DATA.resolve( Handler.class.getName());

	private final Config config;

	private final Set<String> _failedUrls, _emptyUrls, _youtubeUrls, _completedUrls;
	private final Map<String, DUrls> durls;
	private final Object LOCK = new Object(); 

	private final AtomicInteger completed_subdownload = new AtomicInteger();
	private final AtomicInteger total_subdownload = new AtomicInteger();
	private final AtomicInteger failed_subdownload = new AtomicInteger();
	private final String name;

	public Handler(Config config) throws IOException {
		this(config, config.name());
	}

	private Handler(Config config, String name) throws IOException {
		this.config = config;
		this.name = Objects.requireNonNull(name);
		Path path = cachePath();

		boolean initialize = config != null;

		if(Files.notExists(path)) {
			_failedUrls = set(initialize); 
			_emptyUrls = set(initialize); 
			_youtubeUrls = set(initialize); 
			_completedUrls  = set(initialize);
			durls = initialize ? new HashMap<>() : null;
		} else {
			durls = new HashMap<>();
			HashMap<String, Set<String>> map = loadCache(path);

			_failedUrls = map.get(FAILED_MARKER); 
			_emptyUrls = map.get(EMPTY_MARKER); 
			_youtubeUrls = map.get(YOUTUBE_MARKER); 
			_completedUrls  = map.get(COMPLETED_MARKER);

			Set<String> set = map.get(DOWNLOAD_STATUS_MARKER);
			if(!set.isEmpty()) {
				DUrls current = null;

				for (String s : set) {
					StringSplitIterator split = new StringSplitIterator(s, '\t', Integer.MAX_VALUE);

					if(s.startsWith(DURL_MARKER)) {
						String url = split.next().substring(DURL_MARKER.length());
						current = durls.computeIfAbsent(url, DUrls::new);
						current.count = Integer.parseInt(split.next());
					} else {
						current.map.put(split.next(), split.next().equals("true") ? Boolean.TRUE : Boolean.FALSE);
					}
				}
			}
		}
	}

	public Collection<DUrls> getDurls() {
		return durls == null ? Collections.emptyList() : Collections.unmodifiableCollection(durls.values());
	}

	private Path cachePath() {
		return MY_DIR.resolve(name+".txt");
	}

	private Set<String> set(boolean initialize) {
		return initialize ? new HashSet<>() : null;
	}
	private HashMap<String, Set<String>> loadCache(Path path) throws IOException {
		HashMap<String, Set<String>> map = new HashMap<>();

		for (String s : new String[]{
				YOUTUBE_MARKER,
				FAILED_MARKER,
				EMPTY_MARKER,
				DOWNLOAD_STATUS_MARKER,
				COMPLETED_MARKER}) {
			map.put(s, new HashSet<>());
		}

		Iterator<String> itr = Files.lines(path).iterator();
		Set<String> set = null;

		while (itr.hasNext()) {
			String s = itr.next();
			if(s.isEmpty())
				continue;

			Set<String> st = map.get(s);
			if(st != null)
				set = st;
			else if(set != null)
				set.add(s);
			else 
				LOGGER.error("no marker found for: "+s);
		}

		return map;
	}

	private static final String DURL_MARKER = "DURL: ";

	public class DUrls {
		private int count;
		private final String url;
		private final Map<String, Boolean> map = new HashMap<>();

		public DUrls(String url) {
			this.url = url;
		}

		public boolean contains(String u) {
			synchronized (LOCK) {
				return map.containsKey(u);
			}
		}

		public void succeed(String u) {
			synchronized (LOCK) {
				map.put(u, Boolean.TRUE);
			}
		}
		public void failed(String u) {
			synchronized (LOCK) {
				map.put(u, Boolean.FALSE);
			}
		}
		public void eachFailed(Consumer<String> action) {
			map.forEach((s,t) -> {
				if(t != Boolean.TRUE)
					action.accept(s);
			});
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (LOCK) {
			Path p = cachePath();

			durls.values().removeIf(map -> {
				if(map.map.isEmpty())
					return true;

				if(map.count != 0 && map.count == map.map.values().stream().filter(b -> b).count()) {
					_completedUrls.add(map.url);	
					return true;
				}
				return false;
			});

			if(urls != null && urls.size() <= _completedUrls.size() && _completedUrls.containsAll(urls)) {
				LOGGER.info(ANSI.green("Completed: ")+name);
				if(Files.deleteIfExists(p))
					LOGGER.debug("deleted: {}", p);
				return;
			}

			StringBuilder sb = new StringBuilder();

			append(_failedUrls, FAILED_MARKER, sb); 
			append(_emptyUrls, EMPTY_MARKER, sb); 
			append(_youtubeUrls, YOUTUBE_MARKER, sb);
			append(_completedUrls, COMPLETED_MARKER, sb);


			if(!durls.isEmpty()) {
				sb.append(DOWNLOAD_STATUS_MARKER).append('\n');
				durls.forEach((url,map) -> {
					sb.append(DURL_MARKER).append(url).append('\t').append(map.count).append('\n');
					map.map.forEach((s,t) -> sb.append(s).append('\t').append(t == Boolean.TRUE ? "true" : "false").append('\n'));
				});	
			}

			if(sb.length() == 0)
				Files.deleteIfExists(p);
			else {
				StringWriter2.setText(p, sb);
				LOGGER.debug("created: {}", p);
			}
		}
	}
	private void append(Set<String> list, String marker, StringBuilder sb) {
		if(Checker.isEmpty(list))
			return;

		sb.append(marker).append('\n');
		for (String s : list) 
			sb.append(s).append('\n');

		sb.append('\n');
	}

	private static final String formatEmpty = "{}  " + red(" EMPTY");
	private static final String success_format = "{}  " + yellow("({})");
	private int totalUrls = 0;
	private List<String> urls;

	public AtomicInteger handle(List<String> urls, ExecutorService executorService) {
		if(urls.isEmpty() || config.disable)
			throw new IllegalStateException(urls.isEmpty() ? "urls.isEmpty()" : "disabled");

		this.totalUrls = urls.size();
		this.urls = urls;
		AtomicInteger urls_remaining = new AtomicInteger(urls.size());

		for (String url : urls) {
			if(contains(_completedUrls, url)){
				LOGGER.info("SKIPPED: "+url);
				urls_remaining.decrementAndGet();
			} else  
				execute(url, executorService, urls_remaining);
		}
		return urls_remaining;
	}

	private void execute(String url, ExecutorService executorService, AtomicInteger urls_remaining) {
		executorService.execute(() -> {
			try {
				ScrappingResult v = config.selector.select(url, config);

				synchronized (LOCK) {
					if(v == ScrappingResult.FAILED){
						config.selectorLogger.warn(red(url));
						_failedUrls.add(url);
					} else if(v != COMPLETED && (v == EMPTY || v.getUrls().isEmpty())) {
						config.selectorLogger.warn(formatEmpty, url);
						_emptyUrls.add(url);
					} else {
						config.selectorLogger.info(success_format, url, v == COMPLETED ? "--" : v.getUrls().size());
						_failedUrls.remove(url);
						_emptyUrls.remove(url);

						List<String> list = v.getUrls(); 

						if(list instanceof YoutubeList)
							_youtubeUrls.addAll(list);
						else if(v != COMPLETED) 
							download(url, v.getPath(), list, executorService);
					}
				}
			} catch (ScrappingException|IOException e) {
				config.selectorLogger.warn(red(url), e);
				add(_failedUrls, url);
			} finally {
				urls_remaining.decrementAndGet();
			}
		});	
	}

	private void download(final String url0, Path path, List<String> list, ExecutorService executorService) {
		if(Checker.isEmpty(list))
			return ;

		int size = list.size();
		total_subdownload.addAndGet(size);
		DUrls completed;

		synchronized (LOCK) {
			completed = durls.computeIfAbsent(url0, DUrls::new);
			completed.count = size;
		}

		//download 
		for (String u : list) {
			if(completed.contains(u)) {
				LOGGER.debug("SKIPPED: {}", u);
				completed_subdownload.incrementAndGet();
			} else {
				executorService.execute(() -> {
					try {
						if(config.downloader.download(u, path)) {
							completed.succeed(u);
							completed_subdownload.incrementAndGet();
						} else 
							completed.failed(u);
						
					} catch (IOException|ScrappingException e) {
						LOGGER.warn("FAILED: "+u+", error: "+e);
						failed_subdownload.decrementAndGet();
						completed.failed(u);
					}
				});	
			}
		}
	}

	private void add(Set<String> set, String s) {
		synchronized (LOCK) {
			set.add(s);
		}
	}
	private boolean contains(Set<String> set, String s) {
		synchronized (LOCK) {
			return set.contains(s);
		}
	}
	public int getScrapFailedCount() {
		synchronized (LOCK) {
			return _failedUrls.size() + _emptyUrls.size();	
		}
	}

	public int failedSubdownload() {
		return failed_subdownload.get();
	}
	public int completedSubdownload() {
		return completed_subdownload.get();
	}
	public int totalSubdownload() {
		return total_subdownload.get();
	}

	@Override
	public String toString() {
		synchronized (LOCK) {
			StringBuilder sb = new StringBuilder(name);

			if(totalUrls != 0)
				sb.append("\n  urls      : ").append(totalUrls);
			append2(sb, _failedUrls,  "\n  failed    : ");
			append2(sb,  _emptyUrls,   "\n  empty     : ");
			append2(sb,  _youtubeUrls, "\n  youtube   : ");
			append2(sb,  _completedUrls, "\n  completed : ");

			return sb.toString();	
		}
	}
	private void append2(StringBuilder sb, Set<String> list, String tag) {
		if(Checker.isNotEmpty(list))
			sb.append(tag).append(list.size());
	}

	public void append(StringBuilder failed, StringBuilder empty) {
		synchronized (LOCK) {
			if(!_failedUrls.isEmpty())
				_failedUrls.forEach(s -> failed.append(s).append('\n'));

			if(!_emptyUrls.isEmpty())
				_emptyUrls.forEach(s -> empty.append(s).append('\n'));
		}
	}
}
