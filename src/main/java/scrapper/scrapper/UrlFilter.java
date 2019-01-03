package scrapper.scrapper;

import static scrapper.scrapper.ConfigKeys.RSS;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import sam.myutils.Checker;

public interface UrlFilter {
	public boolean accepts(String url);
	public int priority();

	String STARTS_WITH = "startsWith";
	String CONTAINS = "contains";
	String PATTERN = "pattern";

	static final int BASE_OF_BASE = 10000;

	/**
	 * FIXM E
	 * temp fix
	 */
	static int priority0(int base_priority, String s){
		return base_priority + s.length(); 
	}
	class StartsWithFilter implements UrlFilter {
		final String s1, s2;

		public StartsWithFilter(String prefix, boolean lastSlash) {
			s1 = slash(prefix,lastSlash);

			if(prefix.startsWith("http:")) 
				s2 = slash("https".concat(prefix.substring(4)), lastSlash);
			else if(prefix.startsWith("https:")) 
				s2 = slash("http".concat(prefix.substring(5)), lastSlash);
			else
				s2 = null;
		}

		private String slash(String s, boolean lastSlash) {
			return !lastSlash || s.charAt(s.length() - 1) == '/' ? s : s.concat("/");
		}

		@Override
		public boolean accepts(String url) {
			return url.startsWith(s1) || url.startsWith(s2);
		}
		@Override
		public String toString() {
			return getClass().getSimpleName()+" ["+STARTS_WITH+"=\""+s1+"\"]";
		}
		@Override
		public int priority() {
			return priority0(Integer.MAX_VALUE - BASE_OF_BASE*2, s1);
		}
	}

	class DefaultUrlFilter extends StartsWithFilter {
		final String name;
		final String namefilter;
		final StartsWithFilter swf2;

		public DefaultUrlFilter(Config config) throws MalformedURLException {
			super(parse(config.rss()), true);
			this.name = config.name;
			this.namefilter =  "/~r/"+this.name+"/";
			if(s2 == null) 
				throw new IllegalArgumentException("unknown protocol or url: "+config.rss);
			if(s1.contains("//www."))
				swf2 = new StartsWithFilter(s1.replace("//www.", "//"), true);
			else
				swf2 = null;
		}
		private static String parse(String s) throws MalformedURLException {
			return s.contains("google") ? s : Optional.of(new URL(s)).map(s2 -> s2.getProtocol()+"://"+s2.getHost()).get();
		}
		@Override
		public boolean accepts(String url) {
			return super.accepts(url) || url.contains(namefilter) || (swf2 != null && swf2.accepts(url));
		}
		@Override
		public String toString() {
			return getClass().getSimpleName()+" ["+RSS+"=\""+s1+"\", name=\""+name+"\"]";
		}
	}
	class SpecializedUrlFilter implements UrlFilter, Settable {
		private String toString;
		private Predicate<String> filter;

		@Override
		public boolean accepts(String url) {
			return filter.test(url);
		}
		public boolean isValid() {
			return filter != null;
		}
		@Override
		public int priority() {
			return priority0(Integer.MAX_VALUE - 10 * BASE_OF_BASE, toString);
		}
		@Override
		public String toString() {
			return getClass().getSimpleName()+" ["+toString+"]";
		}
		public void set(Predicate<String> f, String key, String value) {
			this.filter = filter == null ? f : filter.and(f);
			toString = (toString == null ? "" : toString + ", ")+key+"=\""+value+"\"";
		}
		public void pattern(String regex) {
			Pattern p = Pattern.compile(regex);
			set(s -> p.matcher(s).matches(), PATTERN, regex);
		}
		public void startsWith(String prefix) {
			StartsWithFilter s = new StartsWithFilter(prefix, false);
			set(s::accepts, STARTS_WITH, prefix);
		}
		public void contains(String contains) {
			set(s -> s.contains(contains), CONTAINS, contains);
		}
		private String check(String value, String key) {
			if(Checker.isEmptyTrimmed(value))
				throw new IllegalArgumentException("value of \""+key+"\" cannot be empty ");
			return value;
		}

		@Override
		public void set(InitializeAs initializeAs, String key, Object value) {
			if(initializeAs != InitializeAs.URL_FILTER)
				throw new IllegalArgumentException("initializeAs: "+initializeAs);

			switch (key) {
				case PATTERN:
					pattern(check((String)value, PATTERN));
					break;
				case STARTS_WITH:
					startsWith(check((String)value, STARTS_WITH));
					break;
				case CONTAINS:
					contains(check((String)value, CONTAINS));
					break;
				default:
					throw new IllegalArgumentException("unknown key: \""+key+"\", for SpecializedUrlFilter");
			}
		}
	}
}
