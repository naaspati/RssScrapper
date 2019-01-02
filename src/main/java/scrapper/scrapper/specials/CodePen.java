package scrapper.scrapper.specials;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static scrapper.Utils.DOWNLOAD_DIR;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import scrapper.scrapper.InitializeAs;
import scrapper.scrapper.Settable;
import scrapper.scrapper.UrlFilter;

public class CodePen implements UrlFilter, Closeable, Settable { 
	// extends HandlerImpl {
	private Set<String> existingPens;
	private List<String> newPens;
	private Path existingPensPath;
	private String prefix;

	@Override
	public int priority() {
		return 0;
	}
	@Override
	public void set(InitializeAs initializeAs, String key, Object value) {
		if(initializeAs != InitializeAs.URL_FILTER)
			throw new IllegalStateException("initializeAs: "+initializeAs);

		if(key.equals("prefix"))
			this.prefix = (String)value;
		else
			throw new IllegalArgumentException("unknonwn key: "+key);
	}

	@Override
	public boolean accepts(String url) {
		if(!url.startsWith(prefix))
			return false;

		if(existingPens == null) {
			Path p = existingPensPath  = Paths.get("existing-pens.txt");
			existingPens = Files.notExists(p) ? new HashSet<>() : MyUtilsException.noError(() -> Files.lines(p)).collect(Collectors.toSet());
			newPens = new ArrayList<>();
		}
		if(!existingPens.contains(url)) {
			newPens.add(url);
			existingPens.add(url);
		}
		return true;
	}
	public void close() throws IOException {
		if(Checker.isEmpty(newPens)) 
			return;

		Files.createDirectories(DOWNLOAD_DIR);
		Path p = DOWNLOAD_DIR.resolve("codepen.urls.txt");
		Files.write(p, newPens, CREATE, APPEND);
		Files.write(existingPensPath, newPens, CREATE, APPEND);
		
		newPens.clear();
		System.out.println("created: "+p);
	}
}
