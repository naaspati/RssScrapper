package scrapper.specials;

import java.util.ArrayList;

import com.jaunt.Element;
import com.jaunt.UserAgent;



@Details(value="pleated-jeans", rss="http://pleated-jeans.com/feed")
public class PLEATED_JEANS extends Specials {
    @Override
    protected Store scrap(String url) {
        UserAgent agent = null;
        
            try {
                agent = takeUserAgent();
                agent.visit(url);
                Element el = agent.doc.findFirst("<div class='entry-content'>");

                ArrayList<String> list = new ArrayList<>();

                for (Element e: el.findEach("<img>"))  list.add(e.getAtString("src"));

                list.removeIf(s -> s == null || s.isEmpty());

                if(list.isEmpty() && testYoutube(agent.doc)) {
                    putUserAgent(agent);
                    return Store.YOUTUBE;
                }

                if(list.size() < 4){
                    print(url, -1, null);
                    addFailed(url);
                    putUserAgent(agent);
                    return null;
                }
                print(url, list.size(), null);
                
                putUserAgent(agent);
                return new Store(url, list, getPath().resolve(prepareName(agent.doc, url)));
            } catch (Exception e) {
                addFailed(url);
                printError("url: "+url, e, "PLEATED_JEANS.scrap");
                putUserAgent(agent);
                return null;
            }   
    }


}
