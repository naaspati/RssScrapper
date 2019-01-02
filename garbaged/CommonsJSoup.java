package scrapper.scrapper.commons;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.jsoup.Jsoup;

import scrapper.EnvConfig;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.commons.Commons.CommonEntry;
import scrapper.scrapper.commons.Commons.CommonEntry.CommonEntryUrlContainer;
public class CommonsJSoup extends Commons {
    protected CommonsJSoup(Collection<String> urls) throws IOException {
        super(urls, Paths.get("commons-jsoup.json"));
    }
    @Override
    protected Callable<ScrappingResult> toTask(CommonEntryUrlContainer ce) {
        return () -> {
            String urlString = ce.getUrl();
            CommonEntry c = ce.getParent();
            
            URL url = new URL(urlString);
            Document doc = Jsoup.parse(url, (int) EnvConfig.CONNECT_TIMEOUT);
            Elements list = doc.select(c.selector);

            if(list.isEmpty()){
                // urlSucess(urlString, testYoutube(doc));
                return null;
            }

            // urlSucess(urlString, list.size());

            List<String> list2 = list.stream().map(e -> e.absUrl(c.attr))
                    .filter(s -> !c.skipDownload(s))
                    .collect(toList());

            return ScrappingResult.create(urlString, list2, c.dir.resolve(prepareName(doc, url)));
        };
    }



}
