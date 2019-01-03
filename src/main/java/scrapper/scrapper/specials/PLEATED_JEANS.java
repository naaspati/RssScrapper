package scrapper.scrapper.specials;

import static scrapper.scrapper.ScrappingResult.EMPTY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import sam.myutils.Checker;
import scrapper.ScrappingException;
import scrapper.scrapper.Config;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.Selector;

public class PLEATED_JEANS implements  Selector {
	@Override
	public ScrappingResult select(String url, Config config) throws ScrappingException, IOException {
		Document doc = config.parse(url); 
		Element el = doc.getElementById("content");

		ArrayList<String> list = null;

		if(el != null) {
			list = new ArrayList<>();

			for (Element e : el.getElementsByTag("img")) 
				list.add(e.attr("src"));

			list.removeIf(s -> s == null || s.isEmpty());
		}

		if(Checker.isEmpty(list)) {
			List<String> result = testYoutube(doc);
			if(Checker.isNotEmpty(result))
				return new ScrappingResult(null, null, result);
			
			return EMPTY;
		}
		if(list.size() < 4)
			return EMPTY;
		
		return new ScrappingResult(url, config.getDir().resolve(prepareName(doc, url)), list);
	}


}
