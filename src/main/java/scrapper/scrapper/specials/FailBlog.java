package scrapper.scrapper.specials;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import scrapper.Config;
import scrapper.scrapper.ScrappingResult;
@Details(value="failblog", rss="http://feeds.feedburner.com/failblog")
public class FailBlog extends Specials {
    @Override
    protected ScrappingResult _scrap(String urlString) throws IOException {
        URL url = new URL(urlString);
        Document doc = Jsoup.parse(url,(int) Config.CONNECT_TIMEOUT);
        Elements list = doc.getElementsByClass("resp-media");

        if(list.isEmpty()){
            if(testYoutube(doc))
                return ScrappingResult.YOUTUBE;

            list = doc.getElementsByTag("video");
            if(!list.isEmpty()){
                for (Element e : list) {
                    try {
                        return new ScrappingResult(urlString, Arrays.asList(e.attr("data-videosrc")), getPath().resolve(prepareName(doc, url)));
                    } catch (Exception e2) {
                        urlFailed(e.absUrl("src"), e2);
                    }
                }
                return ScrappingResult.SUCCESSFUL;
            }
            urlEmpty(urlString);
            return null;
        }

        Path folder = list.size() < 2 ? getPath() : getPath().resolve(prepareName(doc, url));
        if(folder != getPath()){
            if(Files.exists(folder) && folder.toFile().list().length == list.size())
                return ScrappingResult.SUCCESSFUL;
        }

        urlSucess(urlString, list.size());

        List<String> list2 = 
                list.stream()
                .map(e -> e.attr(e.classNames().contains("lazyload") ? "data-src" :  "src"))
                .filter(Objects::nonNull)
                .collect(toList());

        return new ScrappingResult(urlString, list2, folder);

    }
}
