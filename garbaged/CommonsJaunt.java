package scrapper.scrapper.commons;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UserAgentHandler;
import scrapper.scrapper.commons.Commons.CommonEntry;
import scrapper.scrapper.commons.Commons.CommonEntry.CommonEntryUrlContainer;

public class CommonsJaunt extends Commons {
    protected CommonsJaunt(Collection<String> urls) throws IOException {
        super(urls, Paths.get("commons-jaunt.json"));
    }

    @Override
    protected Callable<ScrappingResult> toTask(CommonEntryUrlContainer ce) {
        return () -> {
            try(UserAgentHandler agent = getUserAgent()) {
                String url = ce.getUrl();
                CommonEntry c = ce.getParent();
                
                agent.visit(url);
                Elements els = agent.doc().findEvery(c.selector);
                // urlSucess(url, els.size());

                if(els.size() == 0) {
                    // urlSucess(url, testYoutube(agent.doc()));
                    return null;
                }

                List<String> list = new ArrayList<>();
                for (Element e : els) list.add(e.getAtString(c.attr));

                return ScrappingResult.create(url, list, c.dir.resolve(prepareName(agent.doc(), url)));
            }
        };
    }
}
