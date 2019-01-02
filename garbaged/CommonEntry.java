import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import sam.console.ANSI;
import scrapper.EnvConfig;
import scrapper.scrapper.UrlContainer;
import scrapper.scrapper.commons.Commons.CommonEntry;
import scrapper.scrapper.commons.Commons.CommonEntry.CommonEntryUrlContainer;

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
