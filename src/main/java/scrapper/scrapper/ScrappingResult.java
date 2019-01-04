package scrapper.scrapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ScrappingResult {
	public static final ScrappingResult COMPLETED = new PredefinedScrappingResult("COMPLETED");
	public static final ScrappingResult EMPTY = new PredefinedScrappingResult("EMPTY");
	public static final ScrappingResult FAILED = new PredefinedScrappingResult("FAILED");

	private static class PredefinedScrappingResult extends ScrappingResult {
		private final String s;
		public PredefinedScrappingResult(String name) {
			super();
			this.s = ScrappingResult.class.getSimpleName()+"("+name+")";
		}
		@Override
		public String toString() {
			return s;
		}
		@Override
		public Path getPath() {
			throw new IllegalAccessError();
		} 
		@Override
		public List<String> getUrls() {
			throw new IllegalAccessError();
		}
		@Override
		public String getUrl() {
			throw new IllegalAccessError();
		}
	} 

	private final String url;
	private final Path path;
	private final List<String> urls;

	public Path getPath() {
		return path;
	}
	public List<String> getUrls() {
		return urls;
	}
	public String getUrl() {
		return url;
	}
	
	private ScrappingResult() {
		this.path = null;
		this.urls = null;
		this.url = null;
	}
	
	public ScrappingResult(String url, Path path, List<String> urls) {
		this.path = Objects.requireNonNull(path);
		this.urls = Objects.requireNonNull(urls);
		this.url = Objects.requireNonNull(url);
	}
	
}
