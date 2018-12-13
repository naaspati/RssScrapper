package scrapper.scrapper.commons;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.json.parsers.JSONParser;

import sam.console.ANSI;
import scrapper.EnvConfig;
import scrapper.scrapper.AbstractScrapper;
import scrapper.scrapper.UrlContainer;
import scrapper.scrapper.commons.Commons.CommonEntry.CommonEntryUrlContainer;
public abstract class Commons extends AbstractScrapper<CommonEntryUrlContainer> {
    public static List<Commons> get(Collection<String> urls) throws IOException {
        return Stream.of(new CommonsJSoup(urls), new CommonsJaunt(urls))
                .filter(c -> !c.commonEntries.isEmpty())
                .collect(toList());
    }

    protected final List<CommonEntry> commonEntries;
    private final Path root = EnvConfig.DOWNLOAD_DIR.resolve("commons");

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
    public  final boolean isEmpty() {
        return commonEntries.isEmpty();
    }
    @Override
    public  final int size() {
        return commonEntries.stream().mapToInt(c -> c.urls.size()).sum();
    }
    @Override    
    public final Collection<String> getUrls() {
        return commonEntries.stream().flatMap(c -> c.urls.stream()).collect(toList());
    }

    @Override
    public final Path getPath() {
        return root;
    }

    @Override
    public final void printCount(String format) {
        logger.info(String.format(format, getClass().getSimpleName(), size()));
        commonEntries.forEach(c -> logger.info(String.format("  "+format, c.name, c.urls.size())));
    }

    @Override
    public Iterator<CommonEntryUrlContainer> iterator() {
        return commonEntries.stream()
                .flatMap(CommonEntry::toUrls)
                .iterator();
    }
    protected class CommonEntry {
        final String name, selector, attr;
        final Path dir;
        final Collection<String> urls;
        final Collection<String> removeIf;

        @SuppressWarnings("unchecked")
        public CommonEntry(Map<String, Object> map) {
            this.name = (String)map.get("name");
            this.selector = (String)map.get("selector");
            this.dir = Optional.ofNullable((String)map.get("dir")).map(EnvConfig.DOWNLOAD_DIR::resolve).orElse(root);
            this.attr = (String)map.getOrDefault("attr", "src");
            this.removeIf = (Collection<String>) map.getOrDefault("remove-if", new ArrayList<>());

            try {
                Files.createDirectories(this.dir);
            } catch (IOException e) {
                warn(ANSI.red("failed to create dir: ")+dir, e);
                System.exit(0);
            }
            urls = new HashSet<>();
        }

        public Stream<CommonEntryUrlContainer> toUrls() {
            return urls.stream().map(CommonEntryUrlContainer::new);
        }
        public class CommonEntryUrlContainer extends UrlContainer {
            public CommonEntryUrlContainer(String url) {
                super(url);
            }
            public CommonEntry getParent(){
                return CommonEntry.this;
            }
        }

        public boolean skipDownload(String url) {
            if(removeIf.isEmpty())
                return false;
            return removeIf.stream().anyMatch(url::contains);
        }
    }
}
