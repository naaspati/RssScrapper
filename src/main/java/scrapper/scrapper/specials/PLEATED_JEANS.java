package scrapper.scrapper.specials;

import java.net.URL;
import java.util.ArrayList;





import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;



@Details(value="pleated-jeans", rss="http://pleated-jeans.com/feed")
public class PLEATED_JEANS extends Specials {

    @Override
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        return () -> {
            String url = c.getUrl();

            Document doc = jsoup(url); 
            Element el = doc.getElementById("content");

            ArrayList<String> list = null;

            if(el != null) {
                list = new ArrayList<>();

                for (Element e : el.getElementsByTag("img")) 
                    list.add(e.attr("src"));
                
                list.removeIf(s -> s == null || s.isEmpty());
            }


            if(list == null || list.isEmpty()) {
                int count = testYoutube(doc);
                if(count > 0)
                    urlSucess(url, count);
                else
                    urlEmpty(url);
                return null;
            }
            
            if(list.size() < 4){
                urlEmpty(url);
                return null;
            }
            urlSucess(url, list.size());
            return ScrappingResult.create(url, list, getPath().resolve(prepareName(doc, new URL(url))));
        }; 
    }


}
