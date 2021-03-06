package scrapper;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

import sam.myutils.ThrowException;
import scrapper.scrapper.Config;

class ConfigWrapper implements Closeable {
	private final Logger LOGGER;
	
	final Config config;
	private final ArrayList<String> urls = new ArrayList<>();
	private InfoBox box;
	private Handler handler;
	private boolean closed;
	private int total;
	private boolean noMoreUrls;
	private volatile boolean started;

	ConfigWrapper(Config config) {
		this.config = config;
		this.LOGGER = Utils.logger(config.name, "ConfigWrapper");
	}
	boolean accepts(String url) {
		checkNoMoreUrls();
			
		if(config.accepts(url)) {
			urls.add(url);
			return true;
		}
		return false;
	}
	private void checkNoMoreUrls() {
		if(noMoreUrls)
			ThrowException.illegalStateException("set: no more urls");
	}
	public InfoBox noMoreUrls() {
		checkNoMoreUrls();
		this.noMoreUrls = true;
		
		if(!noMoreUrls && urls.isEmpty() || config.disable || box != null) 
			throw new IllegalStateException();
		
		urls.trimToSize();
		box = new InfoBox(name());
		this.total = urls.size();
		box.main.total.setText(Utils.toString(total));
		
		return box;
	}
	CountDownLatch start(ExecutorService ex) throws IOException {
		if(!noMoreUrls || started) 
			throw new IllegalStateException("!noMoreUrls:"+(!noMoreUrls)+" || started:"+started);
		
		this.started = true;
		handler = new Handler(config);
		
		return handler.handle(urls, ex);
	}
	String name () {
		return config.name();
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
	public int size() {
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
		if(!started)
			return;
		
		int t0 = total;
		int f0 = handler.getScrapFailedCount();
		int c0 = handler.getScrapCompletedCount();
		
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
	public Handler handler() {
		return handler;
	}
}