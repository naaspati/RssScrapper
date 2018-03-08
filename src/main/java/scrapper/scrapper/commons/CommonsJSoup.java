package scrapper.scrapper.commons;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import scrapper.Config;
import scrapper.scrapper.ScrappingResult;
public class CommonsJSoup extends Commons {
    protected CommonsJSoup(Collection<String> urls) throws IOException {
        super(urls, Paths.get("commons-jsoup.json"));
    }
    @Override
    protected ScrappingResult _scrap(CommonEntry c, String urlString) throws IOException {
        URL url = new URL(urlString);
        Document doc = Jsoup.parse(url, (int) Config.CONNECT_TIMEOUT);
        Elements list = doc.select(c.selector);

        if(list.isEmpty()){
            if(testYoutube(doc))
                return ScrappingResult.YOUTUBE;

            urlEmpty(urlString);
            return null;
        }

        urlSucess(urlString, list.size());

        List<String> list2 = list.stream().map(e -> e.absUrl(c.attr))
                .filter(s -> !c.skipDownload(s))
                .collect(toList());

        return new ScrappingResult(urlString, list2, c.dir.resolve(prepareName(doc, url)));
    }

}
