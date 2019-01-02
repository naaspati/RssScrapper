package scrapper.scrapper;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

import sam.console.ANSI;
import scrapper.Utils;

public class DownloadTasks {
	private static final ConcurrentHashMap<String, DownloadTasks> DATA = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Boolean> EXISTING = new ConcurrentHashMap<>();

	public static DownloadTasks get(String url) {
		return DATA.get(url);
	}
	public static DownloadTasks create(String pageUrl, List<String> list, Path folder) {
		list.removeIf(t -> t == null || t.trim().isEmpty() || EXISTING.putIfAbsent(t, true) != null);

		DownloadTasks sr = new DownloadTasks(pageUrl, list, folder);
		DATA.put(pageUrl, sr);
		return sr;
	}
	public static Collection<DownloadTasks> keys() {
		return Collections.unmodifiableCollection(DATA.values());
	}

	static {
		read();
		addShutdownHook();
		System.out.println(ANSI.yellow("ScrappingResult initilized"));
	}

	private static void read() {
		Path p = Paths.get("app_data/ScrappingResult.dat");
		if(Files.exists(p)) {
			try(InputStream is = Files.newInputStream(p, READ);
					DataInputStream dis = new DataInputStream(is)) {

				int size = dis.readInt();
				for (int i = 0; i < size; i++) {
					DownloadTasks sr = new DownloadTasks(dis);
					DATA.put(sr.pageUrl, sr);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static void addShutdownHook() {
		Utils.addShutDownTask(() -> {
			Path p = Paths.get("app_data/ScrappingResult.dat");
			try {
				if(DATA.isEmpty())
					Files.deleteIfExists(p);
				else {
					Files.createDirectories(p.getParent());

					try(OutputStream os = Files.newOutputStream(p, CREATE, WRITE, TRUNCATE_EXISTING);
							DataOutputStream dos = new DataOutputStream(os)) {

						dos.writeInt(DATA.size());
						for (DownloadTasks sr : DATA.values()) 
							sr.write(dos);

						Utils.logger(DownloadTasks.class)
						.info(ANSI.green("created: ")+p);
					}

				}
			} catch (IOException e) {
				Utils.logger(DownloadTasks.class)
				.error("failed to save:"+p, e);
			}
		});
	}

	private void write(DataOutputStream dos) throws IOException {
		synchronized (tasks) {
			dos.writeUTF(pageUrl);
			dos.writeUTF(folder.toString());
			dos.writeBoolean(toParent);

			int count = 0;
			for (DownloadTask d : tasks) {
				if(d != null)
					count++;
			}

			dos.writeInt(count);

			if(count == 0)
				return;
			
			for (DownloadTask d : tasks) {
				if(d != null) {
					dos.writeUTF(d.urlString);
					dos.writeUTF(d.name == null ? "" : d.name);
				}
			}
		}
	} 
	private DownloadTasks(DataInputStream dis) throws IOException {
		this.pageUrl = dis.readUTF();
		this.folder = Paths.get(dis.readUTF());
		this.toParent = dis.readBoolean();

		int size = dis.readInt();
		this.tasks = new DownloadTask[size];

		if( size != 0) {
			for (int n = 0; n < size; n++) {
				String url = dis.readUTF();
				String name = dis.readUTF();
				tasks[n] = new DownloadTask(url, name.isEmpty() ? null : name, n, this);
			}
		}
	}
	static String failedTaskLog() {
		StringBuilder sb = new StringBuilder();
		DATA.values().forEach(sr -> sr.appendFailedLog(sb));
		return sb.length() == 0 ? null : sb.toString();
	}

	public static boolean hasFailed() {
		return DATA.values().stream().anyMatch(DownloadTasks::_hasFailed);
	}
	private void appendFailedLog(StringBuilder sb) {
		int length = sb.length();
		sb.append(folder).append('\n');
		int length2 = sb.length();
		
		forEach(d -> sb.append("  ").append(d.urlString).append('\n'));
		
		if(sb.length() == length2)
			sb.setLength(length);
	}

	private final String pageUrl;
	final boolean toParent;
	private final DownloadTask[] tasks;
	final Path folder;

	private DownloadTasks(String pageUrl, List<String> childrenUrls, Path folder) {
		Objects.requireNonNull(pageUrl, "url cannot be null");
		Objects.requireNonNull(childrenUrls, "childrenUrls cannot be null");
		Objects.requireNonNull(folder, "folder cannot be null");

		if(childrenUrls.isEmpty())
			throw new IllegalArgumentException("childrenUrls cannot be empty");

		this.pageUrl = pageUrl;
		this.folder = folder;
		this.toParent = childrenUrls.size() == 1;
		this.tasks = createTasks(childrenUrls);
	}
	private boolean _hasFailed() {
		return Stream.of(tasks).anyMatch(Objects::nonNull);
	}
	private DownloadTask[] createTasks(List<String> urls) {
		DownloadTask[] ts = new DownloadTask[urls.size()];

		for (int i = 0; i < ts.length; i++)
			ts[i] = new DownloadTask(urls.get(i), i, this);
		return ts;
	}

	public DownloadTasks(String pageUrl, boolean toParent, DownloadTask[] tasks, Path folder) {
		this.pageUrl = pageUrl;
		this.toParent = toParent;
		this.tasks = tasks;
		this.folder = folder;
	}
	public String getUrl() { return pageUrl; }
	public int size() { return tasks.length; }
	public int startDownload(ExecutorService ex) {
		int count[] = {0};
		
		forEach(d -> {
			ex.execute(d);
			count[0]++;
		});
		
		return count[0];
	}
	public List<DownloadTask> failedTasks() {
		synchronized(tasks) {
			ArrayList<DownloadTask> list = new ArrayList<>();
			forEach(list::add);
			return list;	
		}
	}
	private void forEach(Consumer<DownloadTask> consumer) {
		synchronized (tasks) {
			for (DownloadTask d : tasks) {
				if(d != null)
					consumer.accept(d);
			}
		}
	}

	public static final DownloadTasks EMPTY = constant("EMPTY");
	public static final DownloadTasks COMPLETED = constant("COMPLETED");

	private DownloadTasks() {
		this.pageUrl = null;
		this.folder = null;
		this.toParent = false;

		tasks = null;
	}

	private static DownloadTasks constant(String name) {
		String s = "ScrappingResult"+name;
		return new DownloadTasks() {
			@Override public String getUrl() { throw new IllegalAccessError(); }
			@Override public int size() { throw new IllegalAccessError(); }
			@Override public int startDownload(ExecutorService ex) { throw new IllegalAccessError(); }
			@Override public List<DownloadTask> failedTasks() { throw new IllegalAccessError(); }
			@Override
			public String toString() {
				return s;
			}
		};
	}
	
	void remove(int index) {
		synchronized(tasks) {
			tasks[index] = null;
		}
	}
}
