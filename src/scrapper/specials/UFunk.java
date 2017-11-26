package scrapper.specials;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import sam.myutils.renamer.RemoveInValidCharFromString;

@Details(value="ufunk", rss="http://www.ufunk.net/en/feed")
public class UFunk extends Specials {
    @Override
    protected Store scrap(String url) {
        try {
            Document doc = Jsoup.parse(new URL(url), (int) CONNECT_TIMEOUT);
            List<String> list = doc.select(".entry-content p a img").stream().filter(e -> !e.attr("src").endsWith("grey.gif"))
                    .map(e -> e.attr("src"))
                    .collect(Collectors.toList());

            if(list.isEmpty()){
                print(url, -1, null);
                addFailed(url);
                return null;
            }

            Path folder = getPath().resolve(RemoveInValidCharFromString.removeInvalidCharsFromFileName(doc.getElementsByClass("entry-title").text()));
            print(url, list.size(), null);

            if(Files.exists(folder) && folder.toFile().list().length == list.size())
                return Store.SUCCESSFUL;
            
            return new Store(url, list, folder);
        } catch (Exception e1) {
            addFailed(url);
            printError("url: "+url, e1, "UFunk.scrap");
            return null;
        }        
    }
}
