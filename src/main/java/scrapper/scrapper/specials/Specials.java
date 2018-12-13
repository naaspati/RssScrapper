package scrapper.scrapper.specials;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import scrapper.EnvConfig;
import scrapper.scrapper.AbstractScrapper;
import scrapper.scrapper.UrlContainer;
public abstract class Specials extends AbstractScrapper<UrlContainer> {

    public static Collection<AbstractScrapper<UrlContainer>> get(Collection<String> urls)  throws InstantiationException, IllegalAccessException, URISyntaxException, IOException{
        Map<String, Class<? extends Specials>> map = Stream.of(CodePen.class, FailBlog.class, UFunk.class)//, PLEATED_JEANS.class)
                .collect(toMap(c -> ((Details)c.getAnnotation(Details.class)).value(), Function.identity())); 

        Map<String, Specials> map2 = new LinkedHashMap<>();

        urlsFilter(Specials.class, urls, map.keySet(), (url, name) -> {
            Specials spl = map2.get(name);

            if(spl == null){
                try {
                    spl = map.get(name).newInstance();
                    map2.put(name, spl);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
            }
            spl.urls.add(url);
        });
        
        return map2.values().stream().filter(s -> !s.isEmpty()).collect(toList());
    }

    private final Path path;
    final Collection<String> urls;

    @Override
    public void printCount(String format) {
        logger.info(String.format(format, getClass().getSimpleName(), size()));
    }
    @Override
    public  boolean isEmpty() {
        return urls.isEmpty();
    }
    @Override
    public  int size() {
        return urls.size();
    }
    protected Specials() {
        this.path = EnvConfig.DOWNLOAD_DIR.resolve(getClass().getSimpleName().toLowerCase());
        this.urls = Collections.synchronizedSet(new HashSet<>());
    }
    @Override
    public Iterator<UrlContainer> iterator() {
        return getUrls().stream().map(UrlContainer::new).iterator();
    }

    @Override
    public Path getPath() {
        return path;
    }
    @Override
    public  Collection<String> getUrls() {
        return urls;
    }
}