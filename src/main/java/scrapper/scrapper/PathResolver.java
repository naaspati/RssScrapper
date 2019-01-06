package scrapper.scrapper;

import java.nio.file.Path;

@FunctionalInterface
public interface PathResolver {
	public Path resolve(Path parent, String name, String url, int parentChildrenCount) ;
}

class DefaultPathResolver implements PathResolver {

	@Override
	public Path resolve(Path parent, String name, String url, int parentChildrenCount) {
		return parentChildrenCount == 1 ? parent.resolveSibling(parent.getFileName()+ext(name)) : parent.resolve(name);
	}
	private String ext(String name) {
		int n = name.lastIndexOf('.');
		return n < 0 ? "" : name.substring(n);
	}
	
}
