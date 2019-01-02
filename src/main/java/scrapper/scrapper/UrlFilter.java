package scrapper.scrapper;

import static scrapper.scrapper.ConfigKeys.RSS;
import static scrapper.scrapper.UrlFilter.priority0;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import sam.myutils.Checker;
import sam.nopkg.Junk;

public interface UrlFilter {
	public boolean accepts(String url);
	public int priority();
	
	String STARTS_WITH = "startsWith";
	String CONTAINS = "contains";
	String PATTERN = "pattern";
	
	static final int BASE_OF_BASE = 10000;
	
	static int priority0(int base_priority, String s){
		return base_priority + s.length(); //FIXME temp fix
	} 

	class DefaultUrlFilter implements UrlFilter {
		final Config config;

		public DefaultUrlFilter(Config config) {
			this.config = config;
		}

		@Override
		public boolean accepts(String url) {
			// FIXME Auto-generated method stub
			return Junk.notYetImplemented();
		}
		@Override
		public String toString() {
			return getClass().getSimpleName()+" ["+RSS+"=\""+config.rss()+"\", name=\""+config.name()+"\"]";
		}
		@Override
		public int priority() {
			return priority0(Integer.MAX_VALUE - BASE_OF_BASE*2, config.name().concat(config.rss()));
		}
	}
	class SpecializedUrlFilter implements UrlFilter, Settable {
		private String toString;
		private Predicate<String> filter;

		@Override
		public boolean accepts(String url) {
			// TODO Auto-generated method stub
			return false;
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
			set(s -> s.startsWith(prefix), STARTS_WITH, prefix);
		}
		public void contains(String contains) {
			set(s -> s.contains(contains), CONTAINS, contains);
		}
		private String check(String key, String value) {
			if(Checker.isEmptyTrimmed(value))
				throw new IllegalArgumentException(key+" cannot be empty ");
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
					throw new IllegalArgumentException("unknown key: \""+key+"\", for GeneralUrlFilter");
			}
		}
	}
}
