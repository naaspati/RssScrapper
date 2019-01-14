package scrapper.scrapper.specials;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static scrapper.Utils.DOWNLOAD_DIR;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import scrapper.Utils;
import scrapper.scrapper.InitializeAs;
import scrapper.scrapper.Settable;
import scrapper.scrapper.UrlFilter;

public class CodePen implements UrlFilter, Closeable, Settable { 
	private final List<String> newPens = new ArrayList<>();
	private StartsWithFilter prefix;

	@Override
	public void set(InitializeAs initializeAs, String key, Object value) {
		if(initializeAs != InitializeAs.URL_FILTER)
			throw new IllegalStateException("initializeAs: "+initializeAs);

		if(key.equals("prefix"))
			this.prefix = new StartsWithFilter((String)value, true);
		else
			throw new IllegalArgumentException("unknonwn key: "+key);
	}

	@Override
	public boolean accepts(String url) {
		if(!prefix.accepts(url))
			return false;
		
		newPens.add(url);
		return true;
	}
	public void close() throws IOException {
		Logger logger = Utils.logger(getClass()); 
		
		if(newPens.isEmpty()) {
			logger.info("nothing to save");
			return;
		}

		Path exist_path = Utils.APP_DATA.resolve("existing-pens.txt");

		if(Files.exists(exist_path)) {
			Set<String> existing = Utils.lines(exist_path).collect(Collectors.toSet());
			newPens.removeIf(existing::contains);
		}
		
		if(newPens.isEmpty()) {
			Utils.logger(getClass()).info("nothing to save");
			return;
		}

		Files.createDirectories(DOWNLOAD_DIR);
		Path cpath = DOWNLOAD_DIR.resolve("codepen.urls.txt");
		Files.write(cpath, newPens, CREATE, APPEND);
		logger.info("created:("+newPens.size()+") "+cpath);
		
		Utils.writeWithDate(newPens, exist_path, logger);
		newPens.clear();
	}
}
