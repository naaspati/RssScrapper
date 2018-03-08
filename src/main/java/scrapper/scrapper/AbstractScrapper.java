package scrapper.scrapper;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.myutils.fileutils.renamer.RemoveInValidCharFromString.removeInvalidCharsFromFileName;
import static scrapper.scrapper.DataStore.EMPTY;
import static scrapper.scrapper.DataStore.FAILED;
import static scrapper.scrapper.DataStore.URL_SCRAPPING_RESULT_MAP;
import static scrapper.scrapper.DataStore.YOUTUBE;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
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
public abstract class AbstractScrapper {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

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
    protected UserAgentHandler getUserAgent() throws InterruptedException {
        return new UserAgentHandler();
    }
    private final InfoBox infoBox = new InfoBox(this.getClass().getSimpleName().toLowerCase());
    public InfoBox getInfoBox() {
        return infoBox;
    }
    public int failedCount() {
        return infoBox.getFailedCount();
    }
    public abstract boolean isEmpty();
    public abstract  int size();
    public abstract Collection<String> getUrls();
    public abstract Path getPath();
    public Stream<Callable2> tasks() {
        infoBox.setTotal(size());
        return tasksCreate();
    }
    protected abstract Stream<Callable2>  tasksCreate();
    public abstract void printCount(String format);
    protected void progressCompleted() {
        infoBox.progress(true);
    }
    protected Callable2 checkSuccessful(String url) {
        ScrappingResult s = URL_SCRAPPING_RESULT_MAP.getData().get(url);
        if(s == null)
            return null;

        logger.info(url +"  "+green("SKIPPED"));
        progressCompleted();
        return new Callable2(s);
    }
    protected ScrappingResult progress(ScrappingResult store) {
        infoBox.progress(store != null);

        if(store != null)
            URL_SCRAPPING_RESULT_MAP.getData().put(store.getUrl(), store);
        return store;
    }
    protected String prepareName(org.jsoup.nodes.Document doc, URL url) {
        return Optional.ofNullable(doc.title())
                .map(RemoveInValidCharFromString::removeInvalidCharsFromFileName)
                .orElseGet(() -> RemoveInValidCharFromString.removeInvalidCharsFromFileName(new File(url.getFile()).getName()));
    }
    protected String prepareName(com.jaunt.Document doc, String url) throws MalformedURLException {
        try {
            String name = doc.findFirst("<title>").getText();
            if(name == null)
                throw new NullPointerException();
            return  removeInvalidCharsFromFileName(name);
        } catch (NotFound|NullPointerException e) {
            return removeInvalidCharsFromFileName(Utils.getFileName(new URL(url)));
        }
    }
    protected boolean testYoutube(org.jsoup.nodes.Document doc) {
        return testYoutube(doc.getElementsByTag("iframe").stream()
                .map(e -> e.attr("src")));
    }
    protected boolean testYoutube(com.jaunt.Document doc) {
        Stream.Builder<String> b = Stream.builder();

        for (Element e : doc.findEach("<iframe>")) {
            try {
                b.accept(e.getAt("src"));
            } catch (NotFound e2) {}
        }
        return testYoutube(b.build());
    }
    private boolean testYoutube(Stream<String> stream) {
        int size = YOUTUBE.getData().size();

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
        .forEach(YOUTUBE.getData()::add);

        return size != YOUTUBE.getData().size();
    }
    /**
     * 
     * @param urls
     * @param testAgainst
     * @param actionOnMatch calls the consumer as (url, name) -> {}
     */
    protected static void urlsFilter(Class<? extends AbstractScrapper> cls, Collection<String> urls, Collection<String> testAgainst, BiConsumer<String, String> actionOnMatch) {
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
                            DataStore.DISABLED.getData().add(url);
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
    protected void warn(String msg, Throwable e) {
        logger.warn(msg, e);
    }
    private static final String format = "{}  " + yellow("({})"); 
    protected void urlSucess(String url, int size) {
        if(size > 0)
            logger.info(format, url, size);
        else
            urlEmpty(url);
    }
    /**
     * report urlerror and add to failed
     * @param url
     * @param error
     */
    protected void urlFailed(String url, Throwable error) {
        urlError(red(url), error);
        FAILED.getData().add(url);
    }
    private static final String formatEmpty = "{}  " + red(" EMPTY");
    protected void urlEmpty(String url) {
        logger.warn(formatEmpty, url);
        EMPTY.getData().add(url);
    }
    private void urlError(String url, Throwable error) {
        warn("url: "+url, error);
    }
}
