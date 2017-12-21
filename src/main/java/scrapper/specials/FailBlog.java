package scrapper.specials;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Details(value="failblog", rss="http://feeds.feedburner.com/failblog")
public class FailBlog extends Specials {
    @Override
    protected Store scrap(String url) {
        try {
            URL _url = new URL(url);
            Document doc = Jsoup.parse(_url,(int) CONNECT_TIMEOUT);
            Elements list = doc.getElementsByClass("resp-media");

            if(list.isEmpty()){
                if(testYoutube(doc))
                    return Store.YOUTUBE;

                list = doc.getElementsByTag("video");
                if(!list.isEmpty()){
                    for (Element e : list) {
                        try {
                            return new Store(url, Arrays.asList(e.attr("data-videosrc")), getPath().resolve(prepareName(doc, _url)));
                        } catch (Exception e2) {
                            printError("url"+(e.absUrl("src") == null ? e : e.absUrl("src")), e2, "FailBlog.scrap");
                            addFailed(e.toString());
                        }
                    }
                    return Store.SUCCESSFUL;
                }
                print(url, -1, null);
                addFailed(url);
                return null;
            }

            Path folder = list.size() < 2 ? getPath() : getPath().resolve(prepareName(doc, _url));
            if(folder != getPath()){
                if(Files.exists(folder) && folder.toFile().list().length == list.size())
                    return Store.SUCCESSFUL;
            }
            
            print(url, list.size(), null);
            
            List<String> list2 = 
                    list.stream()
                    .map(e -> e.attr(e.classNames().contains("lazyload") ? "data-src" :  "src"))
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
            
                return new Store(url, list2, folder);
        } catch (Exception e) {
            addFailed(url);
            printError("url"+url, e, "FailBlog.scrap");
            return null;
        }
    }
}
