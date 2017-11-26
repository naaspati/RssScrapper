package scrapper.specials;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import scrapper.abstractscrapper.AbstractScrapper;

public abstract class Specials extends AbstractScrapper {

    public static Collection<? extends AbstractScrapper> get(Collection<String> urls)  throws InstantiationException, IllegalAccessException, URISyntaxException, IOException{
        Map<String, Class<? extends Specials>> map = Stream.of(CodePen.class, FailBlog.class, UFunk.class)//, PLEATED_JEANS.class)
                .collect(Collectors.toMap(c -> ((Details)c.getAnnotation(Details.class)).value(), Function.identity())); 
                
        Map<String, Specials> map2 = new LinkedHashMap<>();

        urlsFilter(urls, map.keySet(), (url, name) -> {
            Specials spl = map2.get(name);

            if(spl == null){
                try {
                    spl = (Specials) map.get(name).newInstance();
                    map2.put(name, spl);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
            }
            spl.urls.add(url);
        });

        return map2.values().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private final Path path;
    private final Collection<String> urls;

    @Override
    public void printCount(String format) {
        System.out.printf(format, getClass().getSimpleName(), size());
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
        this.path = rootDir.resolve(getClass().getSimpleName().toLowerCase());
        this.urls = Collections.synchronizedSet(new HashSet<>());
    }
    @Override
    protected Stream<Callable2> _tasks() {
        return urls.stream()
                .map(url -> {
                    Callable2 s = checkSuccessful(url);
                    if(s != null)
                        return s;
                    return new Callable2(() -> progress(scrap(url)));
                });
    }
    protected abstract Store scrap(String url);

    @Override
    public Path getPath() {
        return path;
    }
    @Override
    public  Collection<String> getUrls() {
        return urls;
    }
}