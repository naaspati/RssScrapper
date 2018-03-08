package scrapper.scrapper.specials;

import static java.util.stream.Collectors.toList;
import static sam.myutils.fileutils.renamer.RemoveInValidCharFromString.removeInvalidCharsFromFileName;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import scrapper.Config;
import scrapper.scrapper.ScrappingResult;

@Details(value="ufunk", rss="http://www.ufunk.net/en/feed")
public class UFunk extends Specials {

    @Override
    protected ScrappingResult _scrap(String url) throws MalformedURLException, IOException {
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

        if(Files.exists(folder) && folder.toFile().list().length == list.size())
            return ScrappingResult.SUCCESSFUL;

        return new ScrappingResult(url, list, folder);

    }
}
