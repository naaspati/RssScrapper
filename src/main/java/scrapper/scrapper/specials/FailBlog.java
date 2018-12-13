package scrapper.scrapper.specials;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;

import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;
@Details(value="failblog", rss="http://feeds.feedburner.com/failblog")
public class FailBlog extends Specials {
    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String urlString = c.getUrl();
            URL url = new URL(urlString);
            Document doc = jsoup(url);
            Elements list = doc.getElementsByClass("resp-media");

            if(list.isEmpty()){
                int count = testYoutube(doc);
                if(count > 0) {
                    urlSucess(urlString, count);
                    return null;
                }

                list = doc.getElementsByTag("video");
                if(!list.isEmpty()){
                    List<String> l = new ArrayList<>(1);
                    for (Element e : list)
                        l.add(e.attr("data-videosrc"));
                    return ScrappingResult.create(urlString, l, getPath().resolve(prepareName(doc, url)));
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
                    .collect(Collectors.toList());

            return ScrappingResult.create(urlString, list2, folder);    

        };
    }
}
