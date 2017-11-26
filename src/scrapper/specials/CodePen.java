package scrapper.specials;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

@Details(value="codepen", rss="http://codepen.io/picks/feed")
public class CodePen extends Specials {
    private Set<String> existingPens;

    @Override
    protected Store scrap(String urlString) {
        if(existingPens == null) {
            try {
                existingPens = Files.exists(getPath()) ? Stream.of(getPath().toFile().list()).map(s -> s.substring(0, s.lastIndexOf('_'))).collect(Collectors.toSet()) : new HashSet<>();
            } catch (Exception e) {
                existingPens = new HashSet<>();
            }
        }

        try{
            URL url = new URL(urlString.replace("/pen/", "/share/zip/"));
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");

            String name = con.getHeaderField("Content-Disposition");
            name = name.substring(22, name.length() - 5);

            if(existingPens.contains(name))
                return Store.SUCCESSFUL;

            Files.createDirectories(getPath());
            Path target = getPath().resolve(name+"_"+System.currentTimeMillis()+".zip");
            Files.copy(con.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            print(urlString, 1, null);
        } catch (Exception e) {
            addFailed(urlString);
            printError("url: "+urlString, e, "CodePen.scrap");
            return null;
        }
        return Store.SUCCESSFUL;
    }
}
