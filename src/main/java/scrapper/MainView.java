package scrapper;
import static sam.console.ANSI.FINISHED_BANNER;
import static scrapper.Utils.DOWNLOAD_DIR;
import static scrapper.Utils.THREAD_COUNT;
import static scrapper.Utils.configs;
import static scrapper.Utils.logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.slf4j.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.collection.ArraysUtils;
import sam.console.ANSI;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.dialog.TextAreaDialog;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsCmd;
import sam.string.StringBuilder2;
import sam.string.StringUtils;
import sam.thread.MyUtilsThread;

public final class MainView extends Application {
	private Logger logger = logger(getClass());

	private final TilePane infoBoxes = new TilePane(10,  10);
	private final  ProgressBar progressBar = new ProgressBar();
	private final VBox progressBox = new VBox(5);
	private static Stage stage;
	private final Text status = new Text();
	private final BorderPane root = new BorderPane();

	public static Stage getStage() {
		return stage;
	}
	@Override
	public void start(Stage stage) throws Exception {
		MainView.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);

		final Collection<String> urls = getUrls();

		if(Checker.isEmpty(urls)) {
			logger.warn(ANSI.red("no input"));
			System.exit(0);
		}

		infoBoxes.setPrefColumns(3);
		infoBoxes.setAlignment(Pos.CENTER);
		progressBar.setMaxWidth(Double.MAX_VALUE);

		progressBox.setPadding(new Insets(2));

		ScrollPane sp = new ScrollPane(progressBox);
		progressBox.maxHeight(Double.MAX_VALUE);
		sp.setFitToWidth(true);
		sp.setFitToHeight(true);
		sp.setPrefHeight(200);

		BorderPane.setMargin(status, new Insets(0, 5, 0, 10));
		BorderPane.setAlignment(status, Pos.CENTER);
		root.setTop(new BorderPane(progressBar, null, status, null, null));
		root.setCenter(infoBoxes);
		Scene scene = new Scene(root);
		stage.setScene(scene);

		scene.getStylesheets().add("style.css");

		start2(urls);

		stage.setWidth(520);
		stage.setHeight(400);
		stage.show();
	}

	private ConfigWrapper[] configs;
	private static final Object CONFIGS_LOCK = new Object(); 
	private ThreadPoolExecutor exs;

	private void start2(final Collection<String> urls) throws IOException, InstantiationException, IllegalAccessException, URISyntaxException, ClassNotFoundException, JSONException {
		if(urls.isEmpty()){
			logger.error(ANSI.red("no urls specified"));
			exit();
		}

		this.configs = ArraysUtils.map(configs(), ConfigWrapper[]::new, ConfigWrapper::new);

		List<String> unhandled = new ArrayList<>();
		urls.forEach(url -> {
			for (ConfigWrapper c : configs) {
				if(c.accepts(url)) 
					return;
			}
			unhandled.add(url);			
		});

		if(unhandled.size() == urls.size()) {
			logger.error(ANSI.red("ALL BAD URLS"));
			Files.createDirectories(DOWNLOAD_DIR);
			Files.write(DOWNLOAD_DIR.resolve("bad-urls.txt"), unhandled, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			exit();
		}

		StringBuilder2 sb = new StringBuilder2();
		int max = 0;
		int count = 0;
		for (ConfigWrapper c : configs) {
			if(c.isEmpty())
				continue;
			count++;
			max = Math.max(max, c.name().length());
		}
		if(count != 0) {
			sb.yellow("configs: ").append(count).append('\n');
			String format =  ANSI.yellow("%"+(max+5)+"s")+": %s\n";

			for (ConfigWrapper c : configs) {
				if(!c.isEmpty()) {
					sb.format(format, c.name(), c.size());
					if(c.isDisabled()) {
						sb.setLength(sb.length() - 1);
						sb.red(" (DISABLED)\n");
					}
				}
			} 

			sb.ln().ln();
			logger.info(sb.toString());
			sb = null;	
		}

		configs = ArraysUtils.removeIf(configs, c -> {
			if(c.isDisabled() || c.isEmpty()) {
				c.close();
				return true;
			}
			return false;
		});

		if(configs.length == 0) {
			logger.info(ANSI.red("NOTHING TO PROCESS"));
			exit();
			return;
		}

		for (ConfigWrapper c : configs) 
			infoBoxes.getChildren().add(c.noMoreUrls());

		exs = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_COUNT);
		Runtime.getRuntime().addShutdownHook(new Thread(this::exit));

		stage.setTitle("Running");
		exs.execute(new Next(-1));

		MyUtilsThread.run(false, new ProgressUpdater());

		stage.setOnCloseRequest(e -> {
			if(!FxAlert.showConfirmDialog(null, "sure to close"))
				e.consume();
			else {
				stage.hide();
				shutDownNow();
			}
		});
	}

	private void shutDownNow() {
		logger.info("ExecutorService.shutdownNow(): start");
		exs.shutdownNow();

		try {
			logger.info("ExecutorService.shutdownNow(): waiting");
			exs.awaitTermination(1, TimeUnit.MINUTES);
			logger.info("ExecutorService.shutdownNow(): complete");
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		exit();
	}
	private static final Object OBJECT = new Object();

	private class ProgressUpdater implements Runnable {
		final SynchronousQueue<Object> queue = new SynchronousQueue<>(); 

		@Override
		public void run() {
			while(true) {
				if(exs.isTerminating() || exs.isTerminated())
					break;

				try {
					Thread.sleep(1000);
					Platform.runLater(() -> {
						synchronized (CONFIGS_LOCK) {
							int total = 0;
							int progress = 0;

							for (ConfigWrapper c : configs) {
								c.update();
								total += c.getCurrentTotal();
								progress += c.getProgress();
							}

							progressBar.setProgress(progress/((double)total));
							status.setText(Utils.toString(progress)+"/"+Utils.toString(total));

							try {
								queue.put(OBJECT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					});
					queue.take();
				} catch (InterruptedException e) {
					logger.info("killing updater thread");
					break;
				}
			}
		}
	}

	private void write(Path path, StringBuilder sb) {
		try {
			if(sb.length() != 0) {
				StringWriter2.setText(path, sb);
				logger.info("saved: "+path);
			}
		} catch (IOException e) {
			logger.error("failed to save: "+path, e);
		}
	}

	private class Next implements Runnable {
		private final int index;

		Next(int index) {
			this.index = index;
		}

		@Override
		public void run() {
			CountDownLatch latch = null;
			int n = index+1;
			ConfigWrapper c = null;

			synchronized (CONFIGS_LOCK) {
				while(n < configs.length) {
					try {
						c = configs[n];
						latch = c.start(exs);
						logger.info("started: "+c.name());
						break;
					} catch (IOException e) {
						logger.error("failed to start: "+c, e);
					}
					n++;
				}
			}

			if(n >= configs.length)
				startWaitTask();
			else {
				ConfigWrapper cw = c;
				CountDownLatch cd = latch;
				int n2 = n;

				exs.execute(() -> {
					try {
						logger.debug("waiting to finish: {}", cw.name());
						cd.await();
						exs.execute(new Next(n2));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				});
			}
		}
	}

	private void startWaitTask() {
		logger.info(ANSI.createBanner("URLS SCRAPPING COMPLETED"));
		logger.info("ExecutorService.shutdown() : start");

		startWaitTask0();
	} 
	private void startWaitTask0() {
		if(exs.getActiveCount() != 1)
			exs.execute(this::startWaitTask0);
		else {
			MyUtilsThread.run(false, () -> {
				try {
					exs.shutdown();
					logger.info("ExecutorService.shutdown() : waiting");
					exs.awaitTermination(3, TimeUnit.DAYS);
					logger.info("ExecutorService.shutdown() : complete");
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					exit();	
				}
				
			});
		}
	}
	private AtomicBoolean exiting = new AtomicBoolean();

	private void exit() {
		if(exiting.get())
			return;

		exiting.set(true);
		Utils.exit();

		if(configs != null) {
			synchronized(CONFIGS_LOCK) {
				StringBuilder failed = new StringBuilder();
				StringBuilder empty = new StringBuilder();
				StringBuilder2 status = new StringBuilder2();
				status.append("\n\n").append(FINISHED_BANNER).append("\n\n");
				
				status.yellow("configs: ").append(configs.length).append('\n');
				String format =  ANSI.yellow("%"+(Arrays.stream(configs).mapToInt(c -> c.name().length()).max().orElse(0)+5)+"s")+": "+ANSI.yellow("T:")+" %-3s"+ANSI.green(" C:")+" %-3s "+ANSI.red(" F:")+" %-3s -> (t: %-3s  c: %-3s  f: %-3s)\n";

				for (ConfigWrapper c : configs) {
					c.close();
					c.append(failed, empty);
					Handler h = c.handler();
					if(h == null)
						continue;
					
					status.format(format, c.name(), c.size(), h.getScrapCompletedCount(), h.getScrapFailedCount(), h.totalSubdownload(), h.completedSubdownload(), h.failedSubdownload());
				}

				try {
					Files.createDirectories(DOWNLOAD_DIR);
				} catch (IOException e) {
					logger.error("failed to create dir: "+DOWNLOAD_DIR, e);
				}
				write(DOWNLOAD_DIR.resolve("failed.txt"), failed);
				write(DOWNLOAD_DIR.resolve("empty.txt"), empty);

				FileOpenerNE.openFile(DOWNLOAD_DIR.toFile());
				MyUtilsCmd.beep(5);
				
				logger.info(status.toString());
			}
		}

		System.exit(0);
	}
	private Set<String> getUrls() {
		TextAreaDialog tt = new TextAreaDialog();
		tt.setTitle("RSS OWL");

		Optional.ofNullable(FxClipboard.getString())
		.filter(s -> !Checker.isEmptyTrimmed(s))
		.ifPresent(tt::setContent);

		Path p = DOWNLOAD_DIR;
		if(Files.exists(p))
			tt.setImportDir(p.toFile());
		else if(Files.exists(p = DOWNLOAD_DIR.getParent()))
			tt.setImportDir(p.toFile());

		tt.setHeaderText("Enter Urls seperated by newline");
		return tt.showAndWait().map(StringUtils::splitAtNewlineStream).map(s -> s.collect(Collectors.toSet())).orElse(null);
	}
}
