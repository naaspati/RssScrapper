package scrapper.scrapper;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.myutils.fileutils.renamer.RemoveInValidCharFromString.removeInvalidCharsFromFileName;
import static scrapper.scrapper.DataStore.EMPTY;
import static scrapper.scrapper.DataStore.FAILED;
import static scrapper.scrapper.DataStore.YOUTUBE;

import java.io.File;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jaunt.Element;
import com.jaunt.NotFound;

import sam.console.ansi.ANSI;
import sam.myutils.fileutils.renamer.RemoveInValidCharFromString;
import scrapper.Config;
import scrapper.InfoBox;
import scrapper.Utils;
public abstract class  AbstractScrapper<E extends UrlContainer> implements Iterable<E> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    static {
        // LoggerFactory.getLogger(AbstractScrapper.class).
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
    protected UserAgentHandler getUserAgent() throws InterruptedException {
        return new UserAgentHandler();
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
                remainingTasks.add(makeTask(c));
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
    protected final String prepareName(org.jsoup.nodes.Document doc, URL url) {
        return Optional.ofNullable(doc.title())
                .map(RemoveInValidCharFromString::removeInvalidCharsFromFileName)
                .orElseGet(() -> RemoveInValidCharFromString.removeInvalidCharsFromFileName(new File(url.getFile()).getName()));
    }
    protected final String prepareName(com.jaunt.Document doc, String url) throws MalformedURLException {
        try {
            String name = doc.findFirst("<title>").getText();
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
            Logger logger = LoggerFactory.getLogger(cls);

            if(start + 1 < url.length()){
                String part = url.substring(start, url.indexOf('/', start + 1));

                for (String name : testAgainst) {
                    if(part.contains(name)){
                        if(Config.DISABLED_SCRAPPERS.contains(name)) {
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
    protected final void warn(String msg, Throwable e) {
        logger.warn(msg, e);
    }
    private static final String format = "{}  " + yellow("({})"); 
    protected final void urlSucess(String url, int size) {
        if(size > 0) {
            logger.info(format, url, size);
            DataStore.FAILED.remove(url);
            DataStore.EMPTY.remove(url);
        }
        else
            urlEmpty(url);
    }
    /**
     * report urlerror and add to failed
     * @param url
     * @param error
     */
    protected final void urlFailed(String url, Throwable error) {
        warn(red(url), error);
        FAILED.add(url);
    }
    private static final String formatEmpty = "{}  " + red(" EMPTY");
    protected final void urlEmpty(String url) {
        logger.warn(formatEmpty, url);
        EMPTY.add(url);
    }
}
