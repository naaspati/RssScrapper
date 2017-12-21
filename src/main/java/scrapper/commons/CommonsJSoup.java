package scrapper.commons;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class CommonsJSoup extends Commons {
   protected CommonsJSoup(Collection<String> urls) throws IOException {
        super(urls, Paths.get(System.getenv("APP_HOME"), "commons-jsoup.json"));
    }
    @Override
    protected Store scrap(CommonEntry c, String urlString) {
        try {
            URL url = new URL(urlString);
            Document doc = Jsoup.parse(url, (int) CONNECT_TIMEOUT);
            Elements list = doc.select(c.selector);

            if(list.isEmpty()){
                if(testYoutube(doc))
                    return Store.YOUTUBE;
                
                print(urlString, -1, null);
                addFailed(urlString);
                return null;
            }
            
            print(urlString, list.size(), null);
            
            List<String> list2 = list.stream().map(e -> e.absUrl(c.attr))
                    .filter(s -> !c.skipDownload(s))
                    .collect(Collectors.toList());
            
            return new Store(urlString, list2, c.dir.resolve(prepareName(doc, url)));
        } catch (Exception e) {
            addFailed(urlString);
            printError("url: "+urlString, e, "CommonsJSoup.scrap");
            return null;
        }
    }
    
}
