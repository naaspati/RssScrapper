package scrapper.scrapper;

import static scrapper.Utils.CONNECT_TIMEOUT;
import static scrapper.Utils.DOWNLOAD_DIR;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scrapper.Utils;
import scrapper.scrapper.UrlFilter.DefaultUrlFilter;
import scrapper.scrapper.UrlFilter.SpecializedUrlFilter;
import static scrapper.scrapper.ConfigKeys.*;

public final class Config implements Closeable {
	private static final Logger LOGGER = Utils.logger(Config.class);
	private static final Set<String> query_selectors = new HashSet<>();
	
	public final String name, attr, rss;
	public final Path dir;
	public final int timeout;
	public final Boolean followRedirects;
	public final boolean disable;

	public final UrlFilter urlFilter;
	public final Selector selector; 
	public final Downloader downloader;

	final Logger selectorLogger;

	private final Map<String, String> cookies, headers;

	private static final Path COMMONS_DIR = DOWNLOAD_DIR.resolve("commons");
	private static final Set<String> ALL_KEYS;

	static {
		Set<String> set = new HashSet<>();
		ALL_KEYS = Collections.unmodifiableSet(set);
		Path config_query_selectors = Utils.APP_DATA.resolve("config_query_selectors");
		
		try {
			if(Files.exists(config_query_selectors))
				Files.lines(config_query_selectors).forEach(query_selectors::add);
			for (Field f : ConfigKeys.class.getDeclaredFields())
				set.add((String)f.get(null));
		} catch (IOException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		
		int size = query_selectors.size();
		Utils.addShutDownTask(() -> {
			if(query_selectors.size() != size) {
				try {
					Files.write(config_query_selectors, query_selectors, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
				
		});
	}

	public Config(String name, JSONObject json) throws InstantiationException, IllegalAccessException, ClassNotFoundException{
		this.name = Objects.requireNonNull(name);
		this.attr =  Optional.ofNullable(json.opt(ATTR)).map(s -> (String)s).orElse("src");
		this.dir = Optional.ofNullable((String)json.opt(DIR)).map(DOWNLOAD_DIR::resolve).orElse(COMMONS_DIR);
		this.cookies = map(json.optJSONObject(COOKIES));
		this.headers = map(json.optJSONObject(HEADERS));
		this.rss = (String)json.opt(RSS);
		this.timeout = json.optInt(TIMEOUT, CONNECT_TIMEOUT);
		this.followRedirects = (Boolean) json.opt(FOLLOW_REDIRECTS);
		this.disable = json.optBoolean(DISABLE);
		
		try {
			urlFilter = urlFilter(json);

			selector = disable ? null : selector(json);
			selectorLogger = disable ? null : LoggerFactory.getLogger(name);
			downloader = disable ? null : downloader(json); 
		} catch (Exception e) {
			throw new IllegalStateException("failed init: \n"+name+": "+json.toString(4), e);
		}

		String[] not_contains = json.keySet().stream().filter(s -> !ALL_KEYS.contains(s)).toArray(String[]::new); 

		if(not_contains.length != 0){
			json = new JSONObject(json, not_contains);
			throw new IllegalArgumentException("json contains unknown keys: "+json.toString(4));
		}
	}

	private Downloader downloader(JSONObject json) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		Objects.requireNonNull(urlFilter);
		Objects.requireNonNull(selector);

		Downloader dw = parseClass(json, DOWNLOADER, InitializeAs.DOWNLOADER);
		return dw != null ? dw : getDefaultDownloader(json);
	}
	private Downloader getDefaultDownloader(JSONObject json) {
		return new DefaultDownloader();
	}

	private Selector selector(JSONObject json) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		Objects.requireNonNull(urlFilter);
		Object value = json.opt(SELECTOR);
		if(value == null)
			throw new IllegalArgumentException("no \""+SELECTOR+"\" specified");

		if(value.getClass() == String.class) {
			String s = (String)value;
			if(s.indexOf(' ') >= 0)
				return new QuerySelector(s);

			if(query_selectors.contains(s))
				return new QuerySelector(s);
			try {
				return object(s);
			} catch (ClassNotFoundException e) {
				LOGGER.warn("class not found: \""+s+"\", creating as QuerySelector");
				query_selectors.add(s);
				return new QuerySelector(s);
			}  
		} else {
			Selector s = parseClass(json, SELECTOR, InitializeAs.SELECTOR);
			if(s != null) return s;
			json = (JSONObject) value;

			if(json.length() == 0)
				throw new IllegalArgumentException("not setting specified for SpecilizedSelector");
			if(json.length() != 1)
				throw new IllegalArgumentException("only one setting allowed for SpecilizedSelector");


			String key = json.keys().next();

			Function<Document, List<Element>> selector = Selector.specilized(key, json.getString(key));
			if(selector == null)
				throw new IllegalArgumentException("failed to create selector for: "+json);

			return new DefaultSelector(selector);
		}
	}

	private UrlFilter urlFilter(JSONObject json) throws InstantiationException, IllegalAccessException, ClassNotFoundException, MalformedURLException {
		if(!json.has(URL_FILTER))
			return new DefaultUrlFilter(this);

		UrlFilter s = parseClass(json, URL_FILTER, InitializeAs.URL_FILTER);
		if(s != null) return s;

		Object jsonb = json.get(URL_FILTER);
		if(!(jsonb instanceof JSONObject))
			throw new IllegalStateException("expected a JsonObject but found : "+jsonb);

		json = (JSONObject) jsonb;
		if(json.length() == 0)
			throw new IllegalArgumentException("not settings specified for GeneralUrlFilter");

		SpecializedUrlFilter g = new SpecializedUrlFilter();
		set(g, InitializeAs.URL_FILTER, json, Collections.emptyList());
		if(!g.isValid())
			throw new IllegalArgumentException("no filter setting specified");
		return g;
	}
	private <E> E parseClass(JSONObject json, String jsonKey, InitializeAs initializeAs) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		if(json.has(jsonKey)) {
			Object o = json.get(jsonKey);
			if(o.getClass() == String.class) {
				return object((String)o);
			} else {
				JSONObject n = (JSONObject)o;
				Object clsName = n.opt("class");
				if(clsName == null)
					return null;

				E e = object((String)clsName);
				if(n.length() != 1) {
					Settable s = (Settable)e;
					set(s, initializeAs, n, Collections.singleton("class"));
				}
				return e;
			}
		}
		return null;
	}

	private void set(Settable s, InitializeAs initializeAs, JSONObject json, Collection<String> ignore) {
		json.keySet().forEach(key -> {
			if(!ignore.contains(key))
				s.set(initializeAs, key, json.get(key));
		});
	}

	@SuppressWarnings("unchecked")
	private <E> E object(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Class<?> cls = Class.forName(className);
		Object result = null;

		for (Object o : new Object[]{urlFilter,selector,downloader}) {
			if(o != null && o.getClass() == cls) {
				result = o;
				break;
			}
		}

		if(result == null)
			result = cls.newInstance();

		return (E)result;
	}
	private Map<String, String> map(JSONObject json) {
		if(json == null) return null;

		Map<String, String> map = new HashMap<>();
		json.keySet() .forEach(s -> map.put(s, json.getString(s)));

		return Collections.unmodifiableMap(map);
	}

	public Connection connection(String url) {
		Connection c = HttpConnection.connect(url);
		c.timeout(timeout);

		if(cookies != null) 
			c.cookies(cookies);
		if(followRedirects != null)
			c.followRedirects(followRedirects);
		if(headers != null)
			c.headers(headers);

		return c;
	}

	public JSONObject toJsonObject() {
		JSONObject o = new JSONObject();
		o.put("name", name);
		o.put(ATTR, attr);
		o.put(RSS, rss);
		o.put(DIR, dir.toString());
		o.put(URL_FILTER, string(urlFilter));
		o.put(SELECTOR, string(selector));
		o.put(DOWNLOADER, string(downloader));
		o.put(COOKIES, cookies);
		o.put(HEADERS, headers);
		o.put(TIMEOUT, timeout);
		o.put(FOLLOW_REDIRECTS, followRedirects == null ? false : followRedirects);

		return o;
	}
	private String string(Object o) {
		return o == null ? null : o.toString();
	}
	@Override
	public String toString() {
		return toJsonObject().toString(4);
	}
	public Path getDir() {
		return dir;
	}
	public Document parse(String url) throws IOException {
		return connection(url).get();
	}
	public String name() {
		return name;
	}
	public String rss() {
		return rss;
	}
	public boolean accepts(String url) {
		return urlFilter.accepts(url);
	}

	@Override
	public void close() throws IOException {
		IdentityHashMap<Object, Void> map = new IdentityHashMap<>();
		map.put(urlFilter,null);
		map.put(selector,null);
		map.put(downloader,null);

		map.remove(null);

		map.keySet().forEach(s -> {
			try {
				if(s instanceof Closeable)
					((Closeable)s).close();
				if(s instanceof AutoCloseable)
					((AutoCloseable)s).close();
			} catch (Exception e) {
				LOGGER.error(s.toString(), e);
			}
		});
	}

}
