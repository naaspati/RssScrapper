package scrapper.scrapper;

import java.nio.file.Path;

public class PathResolverWrap {
	private final PathResolver resolver;
	private final Path parent;
	private final int parentChildrenCount;
	
	public PathResolverWrap(PathResolver resolver, Path parent, int parentChildrenCount) {
		this.resolver = resolver;
		this.parent = parent;
		this.parentChildrenCount = parentChildrenCount;
	}
	
	public Path resolve(String name, String url) {
		return resolver.resolve(parent, name, url, parentChildrenCount);
	}
}
