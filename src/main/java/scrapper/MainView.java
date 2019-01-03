package scrapper;
import static sam.console.ANSI.FINISHED_BANNER;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsCmd;
import sam.string.StringBuilder2;
import sam.string.StringUtils;

public final class MainView extends Application {
	private Logger logger = Utils.logger(getClass());

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

		boolean isDownload = getParameters().getRaw().contains("--download");
		final Collection<String> urls =  isDownload ? null : System.getProperty("urls-file") == null ? inputDialog() : Files.lines(Paths.get(System.getProperty("urls-file"))).distinct().collect(Collectors.toList());

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
	private ThreadPoolExecutor ex;

	private void start2(final Collection<String> urls) throws IOException, InstantiationException, IllegalAccessException, URISyntaxException, ClassNotFoundException, JSONException {
		if(urls.isEmpty()){
			logger.error(ANSI.red("no urls specified"));
			System.exit(0);
		}

		this.configs = ArraysUtils.map(Utils.configs(), ConfigWrapper[]::new, ConfigWrapper::new);

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
			Files.createDirectories(Utils.DOWNLOAD_DIR);
			Files.write(Utils.DOWNLOAD_DIR.resolve("bad-urls.txt"), unhandled, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			System.exit(0);
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
			Platform.exit();
			return;
		}

		StringBuilder2 sb = new StringBuilder2();
		sb.yellow("configs: ").append(configs.length).append('\n');
		int max = Arrays.stream(configs).mapToInt(s -> s.name().length()).max().getAsInt();
		String format =  ANSI.yellow("%"+(max+5)+"s")+": %s\n";

		for (ConfigWrapper c : configs) 
			sb.format(format, c.name(), c.size());

		sb.ln().ln();
		logger.info(sb.toString());
		sb = null;

		ex = (ThreadPoolExecutor) Executors.newFixedThreadPool(Utils.THREAD_COUNT);
		addShutdownHook();

		synchronized(CONFIGS_LOCK) {
			for (int i = 0; i < configs.length; i++) {
				ConfigWrapper c = configs[i];

				try {
					infoBoxes.getChildren().add(c.start(ex));
				} catch (IOException e) {
					logger.error("failed add tasks: "+c.toString(), e);
					configs[i] = null;
				}
			}
			configs = ArraysUtils.removeIf(configs, Objects::isNull);
		}

		stage.setTitle("Running");
		ex.execute(new CheckRemainingUrls());

		Thread progressThread = new Thread(new ProgressUpdater());
		progressThread.start();

		stage.setOnCloseRequest(e -> {
			if(!FxAlert.showConfirmDialog(null, "sure to close"))
				e.consume();
			else {
				ex.shutdownNow();
				try {
					ex.awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				System.exit(0);
			}
		});
	}

	private void addShutdownHook() {
		Utils.setLastTask(() -> {
			synchronized(CONFIGS_LOCK) {
				StringBuilder failed = new StringBuilder();
				StringBuilder empty = new StringBuilder();

				for (ConfigWrapper c : configs) {
					c.close();
					c.append(failed, empty);
				}

				write(Utils.DOWNLOAD_DIR.resolve("failed.txt"), failed);
				write(Utils.DOWNLOAD_DIR.resolve("empty.txt"), empty);

				FileOpenerNE.openFile(Utils.DOWNLOAD_DIR.toFile());
				MyUtilsCmd.beep(5);
				logger.info("\n\n"+FINISHED_BANNER);
			}
		});
	}

	private class ProgressUpdater implements Runnable {
		AtomicInteger mod = new AtomicInteger(0); 

		@Override
		public void run() {
			while(true) {
				if(ex.isTerminated())
					break;

				int n = mod.incrementAndGet();

				try {
					Thread.sleep(1000);
					Platform.runLater(() -> {
						if(n != mod.get())
							return;

						synchronized (CONFIGS_LOCK) {
							if(n != mod.get())
								return;

							int total = 0;
							int progress = 0;

							for (ConfigWrapper c : configs) {
								c.update();
								total += c.getCurrentTotal();
								progress += c.getProgress();
							}
							progressBar.setProgress(((double)total)/progress);
							status.setText(Utils.toString(progress)+"/"+Utils.toString(progress));
						}
					});
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

	private class CheckRemainingUrls implements Runnable {
		private final boolean sleep;
		
		CheckRemainingUrls() {
			sleep = false;
		}
		CheckRemainingUrls(boolean sleep) {
			this.sleep = sleep;
		}

		@Override
		public void run() {
			if(sleep) {
				logger.debug("sleeping: CheckRemainingUrls");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					logger.warn("Interrupted: CheckRemainingUrls, skipping startWaitTask();");	
					return;
				}
			}
			
			synchronized (CONFIGS_LOCK) {
				logger.debug("check if to startWaitTask()");

				for (ConfigWrapper c : configs) {
					if(!c.isUrlsCompleted()) {
						ex.execute(new CheckRemainingUrls(ex.getActiveCount() < ex.getMaximumPoolSize()));
						return;
					}
				}	
			}
			startWaitTask();
		}
	}

	private void startWaitTask() {
		logger.info(ANSI.createBanner("URLS SCRAPPING COMPLETED"));
		logger.info("shutting down ExecutorService");
		ex.shutdown();

		Thread thread = new Thread(() -> {
			try {
				logger.info("waiting to shutdown ExecutorService");
				ex.awaitTermination(3, TimeUnit.DAYS);
				logger.info("shutdown ExecutorService");
				System.exit(0);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.exit(0);
		});
		thread.start();
	} 


	private Set<String> inputDialog() {
		TextAreaDialog tt = new TextAreaDialog();
		tt.setTitle("RSS OWL");

		Optional.ofNullable(FxClipboard.getString())
		.filter(s -> !Checker.isEmptyTrimmed(s))
		.ifPresent(tt::setContent);

		tt.setHeaderText("Enter Urls seperated by newline");
		return tt.showAndWait().map(StringUtils::splitAtNewlineStream).map(s -> s.collect(Collectors.toSet())).orElse(null);
	}
}
