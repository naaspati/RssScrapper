package scrapper.scrapper;

import java.nio.file.Path;
import java.util.List;

public class ScrappingResult {
	public static final ScrappingResult COMPLETED = new PredefinedScrappingResult("COMPLETED");
	public static final ScrappingResult EMPTY = new PredefinedScrappingResult("EMPTY");
	public static final ScrappingResult FAILED = new PredefinedScrappingResult("FAILED");

	private static class PredefinedScrappingResult extends ScrappingResult {
		private final String s;
		public PredefinedScrappingResult(String name) {
			super(null, null);
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
	} 

	private final Path path;
	private final List<String> urls;

	public Path getPath() {
		return path;
	}
	public List<String> getUrls() {
		return urls;
	}
	public ScrappingResult(Path path, List<String> urls) {
		this.path = path;
		this.urls = urls;
	}
}
