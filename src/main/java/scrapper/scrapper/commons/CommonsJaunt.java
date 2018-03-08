package scrapper.scrapper.commons;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jaunt.Element;
import com.jaunt.Elements;
import com.jaunt.ResponseException;

import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UserAgentHandler;

public class CommonsJaunt extends Commons {
    protected CommonsJaunt(Collection<String> urls) throws IOException {
        super(urls, Paths.get("commons-jaunt.json"));
    }

    @Override
    protected ScrappingResult _scrap(CommonEntry c, String url) throws InterruptedException, ResponseException, MalformedURLException {
        try(UserAgentHandler agent = getUserAgent()) {
            agent.visit(url);
            Elements els = agent.doc().findEvery(c.selector);
            urlSucess(url, els.size());

            if(els.size() == 0) 
                return testYoutube(agent.doc()) ? ScrappingResult.YOUTUBE : null;

            List<String> list = new ArrayList<>();
            for (Element e : els) list.add(e.getAtString(c.attr));

            return new ScrappingResult(url, list, c.dir.resolve(prepareName(agent.doc(), url)));
        }
    }
}
