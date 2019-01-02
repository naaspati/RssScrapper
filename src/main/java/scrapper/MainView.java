package scrapper;
import static java.nio.file.StandardOpenOption.READ;
import static sam.console.ANSI.FINISHED_BANNER;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sam.console.ANSI;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsCmd;
import sam.string.StringBuilder2;
import scrapper.scrapper.Config;
import scrapper.scrapper.Handler;

public final class MainView extends Application {
	private Logger logger = Utils.logger(getClass());

	private final TilePane infoBoxes = new TilePane(10,  10);
	private final  ProgressBar progressBar = new ProgressBar();
	private final VBox progressBox = new VBox(5);
	private static Stage stage;
	private final Label status = new Label();
	private final BorderPane root = new BorderPane();

	{
		status.setMaxWidth(Double.MAX_VALUE);
		status.setStyle("-fx-background-color:black;-fx-text-fill:white;-fx-font-width:bold;-fx-font-size:10;-fx-font-family:'Courier New';-fx-padding:2");
	}

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

		root.setCenter(new VBox(10, progressBar, infoBoxes, sp));
		root.setBottom(status);
		Scene scene = new Scene(root);
		stage.setScene(scene);

		scene.getStylesheets().add(ClassLoader.getSystemResource("style.css").toString());

		start2(urls);

		stage.setWidth(520);
		stage.setHeight(400);
		stage.show();
	}

	private ConfigWrapper[] _configs;
	private static final Object CONFIGS_LOCK = new Object(); 
	private ExecutorService ex;

	private void start2(final Collection<String> urls) throws IOException, InstantiationException, IllegalAccessException, URISyntaxException, ClassNotFoundException, JSONException {
		if(urls.isEmpty()){
			System.out.println(ANSI.red("no urls specified"));
			System.exit(0);
		}

		JSONObject json = new JSONObject(new JSONTokener(Files.newInputStream(Paths.get("configs.json"), READ)));
		this._configs = new ConfigWrapper[json.length()];

		int n = 0;
		for (String s : json.keySet()) 
			_configs[n++] = new ConfigWrapper(s, json.getJSONObject(s));	

		List<String> unhandled = new ArrayList<>();
		urls.forEach(url -> {
			for (ConfigWrapper c : _configs) {
				if(c.accepts(url)) 
					return;
			}
			unhandled.add(url);			
		});

		if(unhandled.size() == urls.size()) {
			System.out.println(ANSI.red("ALL BAD URLS"));
			Files.createDirectories(Utils.DOWNLOAD_DIR);
			Files.write(Utils.DOWNLOAD_DIR.resolve("bad-urls.txt"), unhandled, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			System.exit(0);
		}

		ConfigWrapper[] temp = new ConfigWrapper[_configs.length];
		n = 0;
		for (ConfigWrapper c : _configs) {
			if(c.config.disable || c.urls.isEmpty())
				c.close();
			else
				temp[n++] = c;
		}

		if(n == 0) {
			logger.info(ANSI.red("NOTHING TO PROCESS"));
			Platform.exit();
			return;
		}

		if(n != _configs.length)
			_configs = Arrays.copyOf(temp, n);

		temp = null;

		StringBuilder2 sb = new StringBuilder2();
		sb.yellow("configs: ").append(_configs.length).append('\n');
		int max = Arrays.stream(_configs).mapToInt(s -> s.name().length()).max().getAsInt();
		String format =  ANSI.yellow("%"+(max+5)+"s")+": %s\n";

		for (ConfigWrapper c : _configs) 
			sb.format(format, c.name(), c.urls.size());

		sb.ln().ln();
		logger.info(sb.toString());
		sb = null;

		ex = Executors.newFixedThreadPool(Utils.THREAD_COUNT);
		addShutdownHook();

		synchronized (CONFIGS_LOCK) {
			for (ConfigWrapper c : _configs) {
				try {
					c.start(ex);
				} catch (IOException e) {
					logger.error("failed add tasks: "+c.toString(), e);
					c.remaining_urls = new AtomicInteger();
				}
			}	
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
	
	private class ProgressUpdater implements Runnable {
		@Override
		public void run() {
			//TODO
			
			while(true) {
				try {
					Thread.sleep(1000);
					// codes in WaitTask
				} catch (InterruptedException e) {
					logger.info("killing updater thread");
					break;
				}
			}
		}
	}
	
	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized(CONFIGS_LOCK) {
				StringBuilder failed = new StringBuilder();
				StringBuilder empty = new StringBuilder();

				for (ConfigWrapper c : _configs) {
					if(c.handler != null)
						c.handler.append(failed, empty);
				}

				write(Utils.DOWNLOAD_DIR.resolve("failed.txt"), failed);
				write(Utils.DOWNLOAD_DIR.resolve("empty.txt"), empty);

				FileOpenerNE.openFile(Utils.DOWNLOAD_DIR.toFile());
				MyUtilsCmd.beep(5);
				logger.info("\n\n"+FINISHED_BANNER);
			}
		}));
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

		@Override
		public void run() {

			synchronized (CONFIGS_LOCK) {
				for (ConfigWrapper c : _configs) {
					if(!c.isUrlsCompleted()) {
						ex.execute(new CheckRemainingUrls());
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
		});
		thread.start();
	} 

	private class ConfigWrapper implements Closeable {
		final Config config;
		final List<String> urls = new ArrayList<>();
		InfoBox box;
		Handler handler;
		boolean closed;
		AtomicInteger remaining_urls;

		ConfigWrapper(String name, JSONObject json) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
			this.config = new Config(name, json);
		}
		boolean accepts(String url) {
			if(config.accepts(url)) {
				urls.add(url);
				return true;
			}
			return false;
		}
		void start(ExecutorService ex) throws IOException {
			if(urls.isEmpty() || config.disable) {
				remaining_urls = new AtomicInteger(0);
				return;
			}

			if(box != null)
				throw new IllegalStateException();

			box = new InfoBox(name());
			handler = new Handler(config);
			infoBoxes.getChildren().add(box);

			remaining_urls = handler.handle(urls, ex);
		}
		String name () {
			return config.name();
		}
		boolean isUrlsCompleted() {
			return remaining_urls.get() == 0;
		}

		@Override
		public void close() throws IOException {
			if(closed) 
				throw new IllegalStateException();
			closed = true;
			config.close();
			if(handler != null)
				handler.close();
		}

		@Override
		public String toString() {
			return config.toString();
		}
	}
	private Collection<String> inputDialog() {
		Dialog<ButtonType> tt = new Dialog<>();
		tt.setTitle("RSS OWL");
		TextArea ta = new TextArea();
		ta.setPrefRowCount(20);

		Optional.ofNullable(FxClipboard.getString())
		.filter(s -> !Checker.isEmptyTrimmed(s))
		.ifPresent(ta::setText);

		tt.getDialogPane().setExpandableContent(ta);
		tt.getDialogPane().setExpanded(true);
		tt.setHeaderText("Enter Urls seperated by newline");
		tt.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Set<String> ret = new LinkedHashSet<>();

		ta.textProperty().addListener(i -> Platform.runLater(() -> {
			String strs = ta.getText();
			ret.clear();

			if(strs != null && !(strs = strs.trim()).isEmpty()) {
				Stream.of(strs.split("\r?\n"))
				.map(s -> s.replace('"', ' ').trim())
				.filter(s -> !s.isEmpty())
				.forEach(ret::add);                
			}
			tt.setContentText("Total: "+ret.size());
		}));

		tt.setResultConverter(b -> b);
		Optional<ButtonType> bt = tt.showAndWait();
		if(!bt.isPresent() || bt.get() != ButtonType.OK)
			ret.clear();
		else if(ret.isEmpty()) {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setHeaderText("no input");
			alert.showAndWait();
		}

		if(ret.isEmpty())
			System.exit(0);

		return ret;
	}
}
