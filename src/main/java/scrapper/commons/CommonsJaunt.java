package scrapper.commons;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jaunt.Element;
import com.jaunt.Elements;
import com.jaunt.ResponseException;
import com.jaunt.UserAgent;



public class CommonsJaunt extends Commons {
    protected CommonsJaunt(Collection<String> urls) throws IOException {
        super(urls, Paths.get(System.getenv("APP_HOME"), "commons-jaunt.json"));
    }

    @Override
    protected Store scrap(CommonEntry c, String url) {
        UserAgent agent = null;
        try {
            agent = takeUserAgent();
            agent.visit(url);
            Elements els = agent.doc.findEvery(c.selector);
            print(url, els.size(), null);

            if(els.size() == 0) {
                putUserAgent(agent);    
                return testYoutube(agent.doc) ? Store.YOUTUBE : null;
            }


            List<String> list = new ArrayList<>();
            for (Element e : els) list.add(e.getAtString(c.attr));

            putUserAgent(agent);
            return new Store(url, list, c.dir.resolve(prepareName(agent.doc, url)));
        } catch (ResponseException | InterruptedException e) {
            addFailed(url);
            printError("url: "+url, e, "CommonsJaunt.scrap");
            putUserAgent(agent);
            return null;
        }
    }
}
