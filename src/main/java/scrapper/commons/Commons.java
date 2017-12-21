package scrapper.commons;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.json.parsers.JSONParser;

import scrapper.abstractscrapper.AbstractScrapper;

public abstract class Commons extends AbstractScrapper {
    public static List<Commons> get(Collection<String> urls) throws IOException {
        return Stream.of(new CommonsJSoup(urls), new CommonsJaunt(urls))
                .filter(c -> !c.commonEntries.isEmpty())
                .collect(Collectors.toList());
    }

    protected final List<CommonEntry> commonEntries;
    private final Path root = rootDir.resolve("commons");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Commons(Collection<String> list, Path path) throws IOException {
        commonEntries =  (List<CommonEntry>) ((List)new JSONParser().parseJson(Files.newInputStream(path), "utf-8")
                .get("entries")
                ).stream()
                .map(o -> new CommonEntry((Map)o))
                .collect(Collectors.toList());

        Map<String, CommonEntry> map = commonEntries.stream().collect(Collectors.toMap(c -> c.name, c -> c));

        urlsFilter(list, map.keySet(), (url, name) -> map.get(name).urls.add(url));
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
        return commonEntries.stream().flatMap(c -> c.urls.stream()).collect(Collectors.toList());
    }

    @Override
    public Path getPath() {
        return root;
    }
    
    @Override
    public void printCount(String format) {
        System.out.printf(format, getClass().getSimpleName(), size());
        commonEntries.forEach(c -> System.out.printf("  "+format, c.name, c.urls.size()));
        System.out.println();
    }
    @Override
    protected Stream<Callable2> _tasks() {
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
    protected abstract Store scrap(CommonEntry c, String url);

    protected class CommonEntry {
        final String name, selector, attr;
        final Path dir;
        final Collection<String> urls;
        final Collection<String> removeIf;

        @SuppressWarnings("unchecked")
        public CommonEntry(Map<String, Object> map) {
            this.name = (String)map.get("name");
            this.selector = (String)map.get("selector");
            this.dir = Optional.ofNullable((String)map.get("dir")).map(rootDir::resolve).orElse(root);
            this.attr = (String)map.getOrDefault("attr", "src");
            this.removeIf = (Collection<String>) map.getOrDefault("remove-if", new ArrayList<>());

            try {
                Files.createDirectories(this.dir);
            } catch (IOException e) {
                printError("failed to create dir ", e, "Commons.CommonEntry.constructor"); 
                e.printStackTrace();
                System.exit(0);
            }
            urls = Collections.synchronizedCollection(new HashSet<>());
        }

        public boolean skipDownload(String url) {
            if(removeIf.isEmpty())
                return false;
            return removeIf.stream().anyMatch(s -> url.contains(s));
        }
    }
}
