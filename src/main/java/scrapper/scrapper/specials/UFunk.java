package scrapper.scrapper.specials;

import static sam.io.fileutils.FileNameSanitizer.sanitize;
import static scrapper.scrapper.ScrappingResult.EMPTY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import sam.myutils.Checker;
import scrapper.ScrappingException;
import scrapper.scrapper.Config;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.Selector;

public class UFunk implements Selector {
	@Override
	public ScrappingResult select(String url, Config config) throws ScrappingException, IOException {
		Document doc = config.parse(url);
		
		Elements els = doc.getElementsByClass("entry-content");
		List<String> list = null;

		if(els.size() != 0) {
			list = els.get(0).getElementsByTag("img")
					.stream()
					.map(img -> {
						String s = img.attr("srcset");
						if(s == null || s.trim().isEmpty())
							return img.attr("src");

						int n = s.indexOf(' ');
						return n < 0 ? s : s.substring(0, n);
					})
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		}

		if(Checker.isEmpty(list))
			return EMPTY;

		Path folder = config.getDir().resolve(sanitize(doc.getElementsByClass("entry-title").text()));
		return new ScrappingResult(url, folder, list);
	}
}
