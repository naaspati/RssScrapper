package scrapper.scrapper.specials;

import static java.util.stream.Collectors.toList;
import static sam.fileutils.RemoveInValidCharFromString.removeInvalidCharsFromFileName;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;






import scrapper.EnvConfig;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;

@Details(value="ufunk", rss="http://www.ufunk.net/en/feed")
public class UFunk extends Specials {

    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String url = c.getUrl();
            Document doc = Jsoup.parse(new URL(url), (int) EnvConfig.CONNECT_TIMEOUT);
            Elements els = doc.getElementsByClass("entry-content");
            List<String> list = null;

            if(els.size() != 0) {
                list = els.get(0).getElementsByTag("img")
                        .stream()
                        .map(img -> {
                            String s = img.attr("srcset");
                            if(s == null || s.trim().isEmpty())
                                return img.attr("src");

                            int n = s.indexOf(' ');
                            return n < 0 ? s : s.substring(0, n);
                        })
                        .filter(Objects::nonNull)
                        .collect(toList());
            }

            if(list == null || list.isEmpty()){
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
