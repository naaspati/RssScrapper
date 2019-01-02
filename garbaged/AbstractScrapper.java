package scrapper.scrapper;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.io.fileutils.FileNameSanitizer.*;
import static scrapper.scrapper.DataStore.EMPTY;
import static scrapper.scrapper.DataStore.URL_FAILED;
import static scrapper.scrapper.DataStore.YOUTUBE;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ANSI;
import scrapper.EnvConfig;
import scrapper.InfoBox;
import scrapper.Utils;
public abstract class  AbstractScrapper<E extends UrlContainer> implements Iterable<E> {

    static {
        System.out.println(ANSI.green("AbstractScrapper initiated"));
    }

    protected static boolean testSuperClass(Class<?> clazz, Class<?> superclass) {
        Class<?> c = clazz.getSuperclass();

        while(true) {
            if(c == null)
                return false;
            if(c == superclass)
                return true;
            c = c.getSuperclass();
        }
    }
    private final InfoBox infoBox = new InfoBox(this.getClass().getSimpleName().toLowerCase());
    public InfoBox getInfoBox() {
        return infoBox;
    }
    public final int failedCount() {
        return infoBox.getFailedCount();
    }
    public abstract boolean isEmpty();
    public abstract  int size();
    public abstract Collection<String> getUrls();
    public abstract Path getPath();
    public final void fillTasks(List<ScrappingResult> completedTasks, List<Callable<ScrappingResult>> remainingTasks) {
        int completed = 0;
        int remaining = 0;

        for (E c : this) {
            ScrappingResult sr = ScrappingResult.get(c.getUrl());
            if(sr == null) {
                Callable<ScrappingResult> s = makeTask(c);
                if(s == null) continue;
                remainingTasks.add(s);
                remaining++;
            }
            else {
                completedTasks.add(sr);
                completed++;
            }
        }

        infoBox.setTotal(completed + remaining);
        infoBox.setCompleted(completed);

    }
    private Callable<ScrappingResult> makeTask(E c) {
        Callable<ScrappingResult> task = toTask(c);
        
        if(task == null) return null;

        return () -> {
            ScrappingResult sr = null; 
            try {
                sr = task.call();
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                urlFailed(c.getUrl(), e);
            } finally {
                progress(sr);
            }
            return sr;
        };
    }
    protected abstract Callable<ScrappingResult> toTask(E c);
    public abstract void printCount(String format);
    protected final void progressCompleted() {
        infoBox.progress(true);
    }
    protected final ScrappingResult progress(ScrappingResult result) {
        infoBox.progress(result != null);
        return result;
    }
    /* FIXME
    protected Document jsoup(URL url) throws IOException {
        return Jsoup.parse(url, EnvConfig.CONNECT_TIMEOUT);
    }
    protected Document jsoup(String url) throws IOException {
        return jsoup(new URL(url));
    }
    
    protected final String prepareName(org.jsoup.nodes.Document doc, URL url) {
        return Optional.ofNullable(doc.title())
                .map(RemoveInValidCharFromString::removeInvalidCharsFromFileName)
                .orElseGet(() -> RemoveInValidCharFromString.removeInvalidCharsFromFileName(new File(url.getFile()).getName()));
    }
    protected final String prepareName(com.jaunt.Document doc, String url) throws MalformedURLException {
        try {
            String name = doc.findFirst("<title>").innerText();
            if(name == null)
                throw new NullPointerException();
            return  removeInvalidCharsFromFileName(name);
        } catch (NotFound|NullPointerException e) {
            return removeInvalidCharsFromFileName(Utils.getFileName(new URL(url)));
        }
    }
    protected final int testYoutube(org.jsoup.nodes.Document doc) {
        return testYoutube(doc.getElementsByTag("iframe").stream()
                .map(e -> e.attr("src")));
    }
    protected final int testYoutube(com.jaunt.Document doc) {
        Stream.Builder<String> b = Stream.builder();

        for (Element e : doc.findEach("<iframe>")) {
            try {
                b.accept(e.getAt("src"));
            } catch (NotFound e2) {}
        }
        return testYoutube(b.build());
    }
     */
    
    private final int testYoutube(Stream<String> stream) {
        int[] count = {0};

        stream
        .filter(Objects::nonNull)
        .filter(s -> {
            URL u;
            try {
                u = new URL(s);
                return u.getHost().contains(".youtu");
            } catch (MalformedURLException e) {}
            return false;
        })
        .forEach(s -> {
            YOUTUBE.add(s);
            count[0]++;
        });

        return count[0];
    }
    /**
     * 
     * @param urls
     * @param testAgainst
     * @param actionOnMatch calls the consumer as (url, name) -> {}
     */
    protected static void urlsFilter(Class<? extends AbstractScrapper<?>> cls, Collection<String> urls, Collection<String> testAgainst, BiConsumer<String, String> actionOnMatch) {
        Iterator<String> itr = urls.iterator();

        while(itr.hasNext()){
            String url = itr.next();
            int start = -1;
            try {
                start = url.contains("feedproxy.google.com/~r/") ? url.indexOf("~r/") + 3 : url.indexOf("//") + 2;   
            } catch (Exception e) {
                continue;
            }
            Logger logger = Utils.logger(cls);

            if(start + 1 < url.length()){
                String part = url.substring(start, url.indexOf('/', start + 1));

                for (String name : testAgainst) {
                    if(part.contains(name)){
                        if(EnvConfig.DISABLED_SCRAPPERS.contains(name)) {
                            logger.info(red("disabled: ")+url);
                            DataStore.DISABLED.add(url);
                        }
                        else
                            actionOnMatch.accept(url, name);
                        itr.remove();
                        break;
                    }
                }               
            }
        }
    }
}
