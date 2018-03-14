package scrapper.scrapper.specials;

import static java.util.stream.Collectors.toList;
import static sam.myutils.fileutils.renamer.RemoveInValidCharFromString.removeInvalidCharsFromFileName;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import scrapper.Config;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;

@Details(value="ufunk", rss="http://www.ufunk.net/en/feed")
public class UFunk extends Specials {

    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String url = c.getUrl();
            Document doc = Jsoup.parse(new URL(url), (int) Config.CONNECT_TIMEOUT);
            List<String> list = doc.select(".entry-content p a img").stream().filter(e -> !e.attr("src").endsWith("grey.gif"))
                    .map(e -> e.attr("src"))
                    .collect(toList());

            if(list.isEmpty()){
                urlEmpty(url);
                return null;
            }

            Path folder = getPath().resolve(removeInvalidCharsFromFileName(doc.getElementsByClass("entry-title").text()));
            urlSucess(url, list.size());

            int count = 0;
            if(Files.exists(folder) && (count = folder.toFile().list().length) == list.size()) {
                urlSucess(url, count);
                return null;
            }

            return ScrappingResult.create(url, list, folder);

        };
    }
}
