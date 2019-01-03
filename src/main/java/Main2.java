import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.json.JSONException;

import sam.console.ANSI;
import scrapper.Utils;
import scrapper.scrapper.Config;

/**
 * 
 * xx checked config
 * xx checked check urlfilter
 * 
 * -- check selector
 * -- check downloader
 * -- check whole app
 * 
 * @author Sameer
 *
 */

public class Main2 {
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, JSONException, IOException {
		Config[] config = Utils.configs();
		
		IdentityHashMap<Config, List<String>> map = new IdentityHashMap<>();
		List<String> anath = new ArrayList<>();
		
		Files.lines(Paths.get("D:\\Downloads\\rss.txt"))
		.forEach(s -> {
			for (Config c : config) {
				if(c.accepts(s)) {
					map.computeIfAbsent(c, cc -> new ArrayList<>()).add(s);
					return;
				}
			}
			// System.err.println(s);
			anath.add(s);
		});
		
		System.out.println("\n");
		
		System.out.println(ANSI.createBanner("anath"));
		System.out.println(String.join("\n", anath));
		
		System.out.println("anath  "+anath.size());
		
		map.forEach((c,list) -> {
			System.out.println(c.name+"  "+list.size());
			// System.out.println(ANSI.createBanner(c.name));
			// System.out.println(String.join("\n", list));
		});
	}

}
