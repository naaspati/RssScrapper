package scrapper.scrapper.specials;

import java.net.MalformedURLException;
import java.util.ArrayList;

import com.jaunt.Element;
import com.jaunt.NotFound;
import com.jaunt.ResponseException;

import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UserAgentHandler;



@Details(value="pleated-jeans", rss="http://pleated-jeans.com/feed")
public class PLEATED_JEANS extends Specials {
    
    @Override
    protected ScrappingResult _scrap(String url) throws NotFound, ResponseException, InterruptedException, MalformedURLException {
        try(UserAgentHandler agent = getUserAgent()) {
            agent.visit(url);
            Element el = agent.doc().findFirst("<div class='entry-content'>");

            ArrayList<String> list = new ArrayList<>();

            for (Element e: el.findEach("<img>"))  list.add(e.getAtString("src"));

            list.removeIf(s -> s == null || s.isEmpty());

            if(list.isEmpty() && testYoutube(agent.doc()))
                return ScrappingResult.YOUTUBE;

            if(list.size() < 4){
                urlEmpty(url);
                return null;
            }
            urlSucess(url, list.size());
            return new ScrappingResult(url, list, getPath().resolve(prepareName(agent.doc(), url)));
        }   
    }


}
