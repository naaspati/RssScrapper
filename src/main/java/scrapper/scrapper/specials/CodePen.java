package scrapper.scrapper.specials;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static scrapper.Utils.DOWNLOAD_DIR;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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
			Set<String> existing = Files.lines(exist_path)
					.filter(s -> !s.isEmpty() && s.charAt(0) != '#')
					.collect(Collectors.toSet());

			newPens.removeIf(existing::contains);
		}
		
		if(newPens.isEmpty()) {
			Utils.logger(getClass()).info("nothing to save");
			return;
		}

		Files.createDirectories(DOWNLOAD_DIR);
		write(DOWNLOAD_DIR.resolve("codepen.urls.txt"), newPens, logger);
		
		newPens.add(0, "# "+LocalDateTime.now());
		newPens.add("");
		write(exist_path, newPens, logger);
		
		newPens.clear();
	}

	private void write(Path path, List<String> list, Logger logger) throws IOException {
		Files.write(path, list, CREATE, APPEND);
		logger.info("created:("+list.size()+") "+path);
	}
}
