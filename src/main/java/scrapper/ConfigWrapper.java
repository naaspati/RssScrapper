package scrapper;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import javafx.application.Platform;
import scrapper.scrapper.Config;
import scrapper.scrapper.Handler;

class ConfigWrapper implements Closeable {
	private static final Logger LOGGER = Utils.logger(ConfigWrapper.class);
	
	final Config config;
	private final List<String> urls = new ArrayList<>();
	private InfoBox box;
	private Handler handler;
	private boolean closed;
	private AtomicInteger remaining_urls;
	private int total;

	ConfigWrapper(Config config) {
		this.config = config;
	}
	boolean accepts(String url) {
		if(config.accepts(url)) {
			urls.add(url);
			return true;
		}
		return false;
	}
	InfoBox start(ExecutorService ex) throws IOException {
		if(urls.isEmpty() || config.disable || box != null) 
			throw new IllegalStateException();

		box = new InfoBox(name());
		this.total = urls.size();
		box.main.total.setText(Utils.toString(total));
		handler = new Handler(config);

		remaining_urls = handler.handle(urls, ex);
		Platform.runLater(this::update);

		return box;
	}
	String name () {
		return config.name();
	}
	boolean isUrlsCompleted() {
		return remaining_urls.get() == 0;
	}

	@Override
	public void close() {
		if(closed) 
			throw new IllegalStateException();
		closed = true;
		
		try {
			config.close();
		} catch (IOException e) {
			LOGGER.error("failed to close config: "+config, e);
		}
		if(handler != null) {
			try {
				handler.close();
			} catch (IOException e) {
				LOGGER.error("failed to close handler: "+handler, e);
			}
		}
	}

	@Override
	public String toString() {
		return config.toString();
	}
	public boolean isEmpty() {
		return urls.isEmpty();
	}
	public boolean isDisabled() {
		return config.disable;
	}
	public Object size() {
		return urls.size();
	}
	public void append(StringBuilder failed, StringBuilder empty) {
		if(handler != null) 
			handler.append(failed, empty);
	}
	
	private int currentTotal, progress;
	
	public int getCurrentTotal() {
		return currentTotal;
	}
	public int getProgress() {
		return progress;
	}
	
	public void update() {
		int t0 = total;
		int f0 = handler.getScrapFailedCount();
		int c0 = total - remaining_urls.get() - f0;
		
		box.main.completed.setText(Utils.toString(c0));
		box.main.failed.setText(Utils.toString(f0));
		
		int t = handler.totalSubdownload();
		int f = handler.failedSubdownload();
		int c = handler.completedSubdownload();
		
		box.sub.total.setText(Utils.toString(t));
		box.sub.failed.setText(Utils.toString(f));
		box.sub.completed.setText(Utils.toString(c));
		
		currentTotal = t0 + t;
		progress = f0 + c0 + f + c;
	}
}