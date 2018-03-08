package scrapper.scrapper.commons;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.json.parsers.JSONParser;

import sam.console.ansi.ANSI;
import scrapper.Config;
import scrapper.scrapper.AbstractScrapper;
import scrapper.scrapper.Callable2;
import scrapper.scrapper.ScrappingResult;
public abstract class Commons extends AbstractScrapper {
    public static List<Commons> get(Collection<String> urls) throws IOException {
        return Stream.of(new CommonsJSoup(urls), new CommonsJaunt(urls))
                .filter(c -> !c.commonEntries.isEmpty())
                .collect(toList());
    }

    protected final List<CommonEntry> commonEntries;
    private final Path root = Config.DOWNLOAD_DIR.resolve("commons");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Commons(Collection<String> list, Path path) throws IOException {
        commonEntries =  (List<CommonEntry>) ((List)new JSONParser().parseJson(Files.newInputStream(path), "utf-8")
                .get("entries")
                ).stream()
                .map(o -> new CommonEntry((Map)o))
                .collect(toList());

        Map<String, CommonEntry> map = commonEntries.stream().collect(toMap(c -> c.name, c -> c));

        urlsFilter(getClass(), list, map.keySet(), (url, name) -> map.get(name).urls.add(url));
        commonEntries.removeIf(c -> c.urls.isEmpty());
    }

    @Override
    public  boolean isEmpty() {
        return commonEntries.isEmpty();
    }
    @Override
    public  int size() {
        return commonEntries.stream().mapToInt(c -> c.urls.size()).sum();
    }
    @Override    
    public Collection<String> getUrls() {
        return commonEntries.stream().flatMap(c -> c.urls.stream()).collect(toList());
    }

    @Override
    public Path getPath() {
        return root;
    }

    @Override
    public void printCount(String format) {
        logger.info(String.format(format, getClass().getSimpleName(), size()));
        commonEntries.forEach(c -> logger.info(String.format("  "+format, c.name, c.urls.size())));
    }
    @Override
    protected Stream<Callable2> tasksCreate() {
        return commonEntries.stream()
                .flatMap(c ->
                c.urls.stream()
                .map(url -> {
                    Callable2 s = checkSuccessful(url);
                    if(s != null)
                        return s;

                    return new Callable2(() -> progress(scrap(c, url)));
                }));
    }
    private ScrappingResult scrap(CommonEntry c, String url) {
        try {
            return _scrap(c, url);
        } catch (Exception e) {
            urlFailed(url, e);
        }
        return null;
    }
    protected abstract ScrappingResult _scrap(CommonEntry c, String url) throws Exception ;

    protected class CommonEntry {
        final String name, selector, attr;
        final Path dir;
        final Collection<String> urls;
        final Collection<String> removeIf;

        @SuppressWarnings("unchecked")
        public CommonEntry(Map<String, Object> map) {
            this.name = (String)map.get("name");
            this.selector = (String)map.get("selector");
            this.dir = Optional.ofNullable((String)map.get("dir")).map(Config.DOWNLOAD_DIR::resolve).orElse(root);
            this.attr = (String)map.getOrDefault("attr", "src");
            this.removeIf = (Collection<String>) map.getOrDefault("remove-if", new ArrayList<>());

            try {
                Files.createDirectories(this.dir);
            } catch (IOException e) {
                warn(ANSI.red("failed to create dir: ")+dir, e);
                System.exit(0);
            }
            urls = Collections.synchronizedCollection(new HashSet<>());
        }

        public boolean skipDownload(String url) {
            if(removeIf.isEmpty())
                return false;
            return removeIf.stream().anyMatch(url::contains);
        }
    }
}
