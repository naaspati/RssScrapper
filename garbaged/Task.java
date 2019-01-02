package sam.rss.threads;

import java.util.concurrent.Callable;

public abstract class Task<V> implements Callable<V>{
	public abstract void failed(Exception e);
	public abstract void success(V v);
}
