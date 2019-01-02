package scrapper.scrapper.specials;

import static scrapper.scrapper.ScrappingResult.EMPTY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sam.myutils.Checker;
import scrapper.ScrappingException;
import scrapper.scrapper.Config;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.Selector;
public class FailBlog implements Selector {
	@Override
	public ScrappingResult select(String url, Config config) throws ScrappingException, IOException {
		Document doc = config.parse(url);
		Elements list = doc.getElementsByClass("resp-media");
		Path dir = config.getDir();

		if(list.isEmpty()){
			List<String> result = testYoutube(doc);
			if(Checker.isNotEmpty(result))
				return new ScrappingResult(null, result);
			
			list = doc.getElementsByTag("video");
			if(!list.isEmpty()) {
				List<String> l = new ArrayList<>(1);
				for (Element e : list)
					l.add(e.attr("data-videosrc"));
				return new ScrappingResult(dir.resolve(prepareName(doc, url)), l);
			}
			return EMPTY;
		}

		Path folder = list.size() < 2 ? dir : dir.resolve(prepareName(doc, url));
		
		List<String> list2 = 
				list.stream()
				.map(e -> e.attr(e.classNames().contains("lazyload") ? "data-src" :  "src"))
				.collect(Collectors.toList());

		return new ScrappingResult(folder, list2);    
	}
}
