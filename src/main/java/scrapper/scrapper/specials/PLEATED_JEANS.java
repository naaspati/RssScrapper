package scrapper.scrapper.specials;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import com.jaunt.Element;

import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;
import scrapper.scrapper.UserAgentHandler;



@Details(value="pleated-jeans", rss="http://pleated-jeans.com/feed")
public class PLEATED_JEANS extends Specials {

    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String url = c.getUrl();

            try(UserAgentHandler agent = getUserAgent()) {
                agent.visit(url);
                Element el = agent.doc().findFirst("<div class='entry-content'>");

                ArrayList<String> list = new ArrayList<>();

                for (Element e: el.findEach("<img>"))  list.add(e.getAtString("src"));

                list.removeIf(s -> s == null || s.isEmpty());

                int count = 0;
                if(list.isEmpty() && (count = testYoutube(agent.doc())) > 0) {
                    urlSucess(url, count);
                    return null;
                }

                if(list.size() < 4){
                    urlEmpty(url);
                    return null;
                }
                urlSucess(url, list.size());
                return ScrappingResult.create(url, list, getPath().resolve(prepareName(agent.doc(), url)));
            }
        }; 
    }


}
