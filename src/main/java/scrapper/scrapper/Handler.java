package scrapper.scrapper;

import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static scrapper.scrapper.ScrappingResult.COMPLETED;
import static scrapper.scrapper.ScrappingResult.EMPTY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
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

	private static final Logger LOGGER = Utils.logger(Handler.class);
	private static final Path MY_DIR = Paths.get(Utils.APP_DATA, Handler.class.getName());

	private final Config config;

	private final Set<String> failedUrls, emptyUrls, youtubeUrls, completedUrls;
	private final Map<String, Set<String>> downloadStatus;
	private final Map<String, Integer> downloadUrlCount = Collections.synchronizedMap(new HashMap<>());
	private final AtomicInteger remaining = new AtomicInteger();
	private final AtomicInteger total = new AtomicInteger();

	public Handler(Config config) throws IOException {
		this.config = config;
		Path path = cachePath();

		if(Files.notExists(path)) {
			failedUrls = set(); 
			emptyUrls = set(); 
			youtubeUrls = set(); 
			completedUrls  = set();
			downloadStatus = new ConcurrentHashMap<>();
		} else {
			HashMap<String, Set<String>> map = loadCache(path);

			failedUrls = sync(map.get(FAILED_MARKER)); 
			emptyUrls = sync(map.get(EMPTY_MARKER)); 
			youtubeUrls = sync(map.get(YOUTUBE_MARKER));; 
			completedUrls  = sync(map.get(COMPLETED_MARKER));

			Set<String> set = map.get(DOWNLOAD_STATUS_MARKER);
			if(set.isEmpty())
				downloadStatus = new ConcurrentHashMap<>();
			else {
				Map<String, Set<String>> temp = set.stream()
						.map(Temp117::new)
						.filter(t -> t.n > 0)
						.collect(Collectors.groupingBy(t -> t.front(), Collectors.mapping(t -> t.back(), Collectors.toSet())));

				downloadStatus = temp.isEmpty() ? new ConcurrentHashMap<>() : Collections.synchronizedMap(temp);
			}
		}
	}

	private Path cachePath() {
		return MY_DIR.resolve(config.name()+".txt");
	}

	private static class Temp117 {
		final int n;
		final String s;

		public Temp117(String s) {
			this.n = s.indexOf('\t');
			this.s = s;
		}
		String front()  {
			return s.substring(0, n);
		}
		String back()  {
			return s.substring(n+1);
		}
	}

	private Set<String> sync(Set<String> set) {
		return Collections.synchronizedSet(set);
	}
	private Set<String> set() {
		return sync(new HashSet<>());
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

	@Override
	public void close() throws IOException {
		StringBuilder sb = new StringBuilder();

		append(failedUrls, FAILED_MARKER, sb); 
		append(emptyUrls, EMPTY_MARKER, sb); 
		append(youtubeUrls, YOUTUBE_MARKER, sb);

		downloadStatus.values().removeIf(Set::isEmpty);
		if(!downloadStatus.isEmpty()) {
			downloadStatus.forEach((url, list) -> {
				if(Objects.equals(list.size(), downloadUrlCount.get(url)))
					completedUrls.add(url);
			});

			append(completedUrls, COMPLETED_MARKER, sb);

			sb.append(DOWNLOAD_STATUS_MARKER).append('\n');
			downloadStatus.forEach((url,list) -> {
				if(!Objects.equals(list.size(), downloadUrlCount.get(url)))
					list.forEach(x -> sb.append(url).append('\t').append(x).append('\n'));
			});	
		} else {
			append(completedUrls , COMPLETED_MARKER, sb);
		}

		Path p = cachePath();
		if(sb.length() == 0)
			Files.deleteIfExists(p);
		else
			StringWriter2.setText(p, sb);
	}
	private void append(Set<String> list, String marker, StringBuilder sb) {
		if(list.isEmpty())
			return;

		sb.append(marker).append('\n');
		for (String s : list) 
			sb.append(s).append('\n');

		sb.append('\n');
	}

	private static final String formatEmpty = "{}  " + red(" EMPTY");
	private static final String success_format = "{}  " + yellow("({})");
	private int totalUrls = 0;

	public AtomicInteger handle(List<String> urls, ExecutorService executorService) {
		if(urls.isEmpty() || config.disable)
			throw new IllegalStateException(urls.isEmpty() ? "urls.isEmpty()" : "disabled");

		this.totalUrls = urls.size();
		AtomicInteger urls_remaining = new AtomicInteger(urls.size());

		for (String url : urls) {
			if(completedUrls.contains(url)){
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

				if(v == ScrappingResult.FAILED){
					config.selectorLogger.warn(red(url));
					failedUrls.add(url);
				} else if(v != COMPLETED && (v == EMPTY || v.getUrls().isEmpty())) {
					config.selectorLogger.warn(formatEmpty, url);
					emptyUrls.add(url);
				} else {
					config.selectorLogger.info(success_format, url, v == COMPLETED ? "--" : v.getUrls().size());
					failedUrls.remove(url);
					emptyUrls.remove(url);

					List<String> list = v.getUrls(); 

					if(list instanceof YoutubeList)
						youtubeUrls.addAll(list);
					else if(v != COMPLETED) 
						download(url, v.getPath(), list, executorService);
				}
			} catch (ScrappingException|IOException e) {
				config.selectorLogger.warn(red(url), e);
				failedUrls.add(url);
			} finally {
				urls_remaining.decrementAndGet();
			}
		});	
	}

	private void download(final String url0, Path path, List<String> list, ExecutorService executorService) {
		if(Checker.isEmpty(list))
			return ;

		int size = list.size();
		total.addAndGet(size);
		Set<String> completed = downloadStatus.computeIfAbsent(url0, u -> Collections.synchronizedSet(new HashSet<>(size+5)));
		downloadUrlCount.put(url0, size);

		//download 
		for (String u : list) {
			if(completed.contains(u)) {
				LOGGER.debug("SKIPPED: {}", u);
				remaining.decrementAndGet();
			} else {
				executorService.execute(() -> {
					try {
						if(config.downloader.download(u, path))
							completed.add(u);
					} catch (IOException|ScrappingException e) {
						LOGGER.warn("FAILED: "+u+", error: "+e);
					} finally {
						remaining.decrementAndGet();
					}
				});	
			}
		}
	}
	public int remaining() {
		return remaining.get();
	}
	public int total() {
		return total.get();
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(config.name);

		if(totalUrls != 0)
			sb.append("\n  urls      : ").append(totalUrls);
		append2(sb, failedUrls,  "\n  failed    : ");
		append2(sb, emptyUrls,   "\n  empty     : ");
		append2(sb, youtubeUrls, "\n  youtube   : ");
		append2(sb, completedUrls, "\n  completed : ");

		return sb.toString();
	}
	private void append2(StringBuilder sb, Set<String> list, String tag) {
		if(!list.isEmpty())
			sb.append(tag).append(list.size());
	}

	public void append(StringBuilder failed, StringBuilder empty) {
		if(!failedUrls.isEmpty())
			failedUrls.forEach(s -> failed.append(s).append('\n'));
		
		if(!emptyUrls.isEmpty())
			emptyUrls.forEach(s -> empty.append(s).append('\n'));
	}
}
