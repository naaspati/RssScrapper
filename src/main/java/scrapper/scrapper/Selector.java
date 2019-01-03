package scrapper.scrapper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import sam.io.fileutils.FileNameSanitizer;
import sam.myutils.MyUtilsException;
import scrapper.ScrappingException;

@FunctionalInterface
public interface Selector {
	public ScrappingResult select(String url, Config config) throws ScrappingException, IOException;

	public static Function<Document, List<Element>> specilized(String key, String value) {
		String s = (String)value;

		if(key.equals("by-id")) 
			return (doc -> Collections.singletonList(doc.getElementById(s)));
		if(key.equals("by-class")) 
			return (doc -> doc.getElementsByClass(s));
		if(key.equals("by-tag")) 
			return (doc -> doc.getElementsByTag(s));

		return null;
	}

	default String prepareName(Document doc, String url) {
		return Optional.ofNullable(doc.title())
				.map(FileNameSanitizer::sanitize)
				.orElseGet(() -> FileNameSanitizer.sanitize(new File(MyUtilsException.noError(() -> new URL(url)).getFile()).getName()));
	}
	public static class YoutubeList extends ArrayList<String> {
		private static final long serialVersionUID = -4319788591840035978L;
	}
	default YoutubeList testYoutube(Document doc) {
		return doc.getElementsByTag("iframe").stream()
				.map(e -> e.attr("src"))
				.filter(Objects::nonNull)
				.filter(s -> {
					URL u;
					try {
						u = new URL(s);
						return u.getHost().contains(".youtu");
					} catch (MalformedURLException e) {}
					return false;
				})
				.collect(Collectors.toCollection(YoutubeList::new));
	}
}

class DefaultSelector implements Selector {
	final Function<Document, List<Element>> func;

	public DefaultSelector(Function<Document, List<Element>> func) {
		this.func = func;
	}

	@Override
	public ScrappingResult select(String urlString, Config config) throws IOException {
		Document doc = config.parse(urlString);
		List<Element> list = func.apply(doc);

		if(list.isEmpty()) 
			return ScrappingResult.EMPTY;

		List<String> list2 = list.stream()
				.map(e -> e.absUrl(config.attr))
				.collect(Collectors.toList());
		
		Path p = config.dir.resolve(prepareName(doc, urlString));
		return new ScrappingResult(urlString, p, list2);
	}
}

class QuerySelector extends DefaultSelector {
	public QuerySelector(String query) {
		super(doc -> doc.select(query));
	}
}
