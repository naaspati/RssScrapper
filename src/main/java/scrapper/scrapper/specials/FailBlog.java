package scrapper.scrapper.specials;

import static java.util.stream.Collectors.toList;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import scrapper.Config;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;
@Details(value="failblog", rss="http://feeds.feedburner.com/failblog")
public class FailBlog extends Specials {
    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String urlString = c.getUrl();
            URL url = new URL(urlString);
            Document doc = Jsoup.parse(url,(int) Config.CONNECT_TIMEOUT);
            Elements list = doc.getElementsByClass("resp-media");

            if(list.isEmpty()){
                int count = testYoutube(doc);
                if(count > 0) {
                    urlSucess(urlString, count);
                    return null;
                }

                list = doc.getElementsByTag("video");
                if(!list.isEmpty()){
                    for (Element e : list) {
                        try {
                            return ScrappingResult.create(urlString, Arrays.asList(e.attr("data-videosrc")), getPath().resolve(prepareName(doc, url)));
                        } catch (Exception e2) {
                            urlFailed(e.absUrl("src"), e2);
                        }
                    }
                    return null;
                }
                urlEmpty(urlString);
                return null;
            }

            Path folder = list.size() < 2 ? getPath() : getPath().resolve(prepareName(doc, url));
            if(folder != getPath()){
                if(Files.exists(folder) && folder.toFile().list().length == list.size())
                    return null;
            }

            urlSucess(urlString, list.size());

            List<String> list2 = 
                    list.stream()
                    .map(e -> e.attr(e.classNames().contains("lazyload") ? "data-src" :  "src"))
                    .filter(Objects::nonNull)
                    .collect(toList());

            return ScrappingResult.create(urlString, list2, folder);

        };
    }
}
