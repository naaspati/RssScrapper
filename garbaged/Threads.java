package sam.rss.threads;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import scrapper.Utils;
import scrapper.Utils;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Threads {
	private static final Logger LOGGER = Utils.logger(Threads.class);

	private static final LinkedBlockingQueue<Task> TASKS = new LinkedBlockingQueue<>();
	private static final AtomicInteger nth_thread = new AtomicInteger(1);
	private static final List<Thread> activeThreads = Collections.synchronizedList(new LinkedList<>());
	private static final AtomicInteger activeThreadsCount = new AtomicInteger(0);

	public static final int POOL_SIZE = Utils.THREAD_COUNT;
	public static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

	public static <E> void execute(Task<E> task) {
		if(SHUTDOWN.get())
			throw new IllegalStateException("pool shutdown");

		if(activeThreadsCount.get() < POOL_SIZE)
			initThread();
		
		TASKS.add(task);
	}
	private static void initThread() {
		String name = "Thread: ".concat(Integer.toString(nth_thread.getAndIncrement()));
		activeThreadsCount.incrementAndGet();

		Thread th = new Thread(() -> {
			LOGGER.debug("Thread started: {}, activeThreads: ", name);

			try {
				int attempts = 0;
				while(attempts < 3) {
					Task t = null;
					attempts++;
					
					try {
						t = TASKS.poll(1, TimeUnit.SECONDS);
						if(t == null) continue;
						attempts = 0;
						t.success(t.call());
					} catch (Exception e) {
						if(t != null)
							t.failed(e);
						if(e instanceof InterruptedException) {
							LOGGER.debug("Thread Interrupted: {}", name);
							
							if(SHUTDOWN.get())
								break;
						}	
					}
				}
			} finally {
				activeThreadsCount.decrementAndGet();
				activeThreads.remove(Thread.currentThread());
				LOGGER.debug("Thread stop: {}", name);
			}
		}, name);
		
		activeThreads.add(th);
		th.start();
	} 

}
