package scrapper;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

private class WaitTask implements Runnable {
	@Override
	public void run() {
		while(true) {
			Thread.sleep(10000);
			if(completedConfigs.get() == total)
				break;
		}

		String format2 = "remaining: %d  | total: "+total;

		AbstractUpdatable scrapperUpdate = new AbstractUpdatable() {
			@Override
			public void update() {
				long i = latch.getCount();
				status.setText(String.format(format2, i));
				progressBar.setProgress((total - i) / total);
			}
		};

		AtomicInteger totalDownload = new AtomicInteger(0);
		AtomicInteger completedDownload = new AtomicInteger(0);
		String format3 = "completed: %d  | total: %d";

		AbstractUpdatable downloadUpdate = new AbstractUpdatable() {
			@Override
			public void update() {
				status.setText(String.format(format3, completedDownload.get(), totalDownload.get()));
				progressBar.setProgress(completedDownload.get() / (double)totalDownload.get());
			}
		};

		threadPool = new ThreadPoolExecutor(Utils.THREAD_COUNT, Utils.THREAD_COUNT, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()) {
			@Override
			protected void afterExecute(Runnable r, Throwable t) {
				super.afterExecute(r, t);

				if(r instanceof DownloadTask) {
					completedDownload.incrementAndGet();
					downloadUpdate.setChanged();
				} else if(r instanceof FutureTask){
					latch.countDown();

					@SuppressWarnings("unchecked")
					FutureTask<DownloadTasks> f = (FutureTask<DownloadTasks>)r;
					DownloadTasks s = null;
					try {
						s = f.get();
					} catch (InterruptedException | ExecutionException e) {
						logger.error("Future.get() failed", e);
					}
					if(s == null)
						return;
					int count = s.startDownload(threadPool);

					totalDownload.addAndGet(count);

					scrapperUpdate.setChanged();
					downloadUpdate.setChanged();
				}
			};
		};

		scrapperUpdate.update();
		Updatables.add(scrapperUpdate);

		info("\n");
		info(ANSI.createBanner("SCRAPPING"));

		Collections.shuffle(remainingTasks);
		remainingTasks.forEach(threadPool::submit);

		threadPool.execute(() -> {
			info(red("Downloader waiting"));
			try {
				latch.await();
			} catch (InterruptedException e1) {
				logger.warn(red("latch Interrupted"), e1);
				System.exit(0);
			}

			Platform.runLater(() -> {
				stage.setTitle("Downloading");
				stage.getScene().setRoot(new VBox(progressBar, status));
				progressBar.setProgress(0);
				status.setPadding(new Insets(10));
				stage.setWidth(200);
				stage.setHeight(100);

				info("\n");
				info(ANSI.createBanner("DOWNLOADING"));

				downloadStarted = true;
				Updatables.remove(scrapperUpdate);
				Updatables.add(downloadUpdate);
			});

			downloadStarted = true;

			int count = 
					completedTasks.stream()
					.mapToInt(sr -> sr.startDownload(threadPool))
					.sum();

			totalDownload.addAndGet(count);
			downloadUpdate.setChanged();

			Runnable finish = () -> {
				try {
					save();
				} catch (IOException e) {
					logger.error("failed to save", e);
					System.exit(0);
				}
			};

			Utils.addShutDownTask(finish);

			threadPool.execute(() -> {
				Thread t = new Thread(() -> {
					System.out.println(ANSI.yellow("finishing"));
					threadPool.shutdown();
					try {
						System.out.println(red("waiting to terminate: ")+threadPool.getActiveCount());
						threadPool.awaitTermination(3, TimeUnit.DAYS);
					} catch (InterruptedException e) {
						logger.error("ex.awaitTermination(3, TimeUnit.DAYS); Interrupted", e);
					}

					Utils.removeShutdownHook(shutdown);
					finish.run();
					Platform.runLater(stage::hide);
				});
				t.start();
			});
		});


		//FIXME possible wait until finishes

		System.out.println("\n\n");

		info("\n"+
				yellow("total urls: {}\n")+
				green("good urls: {}\n")+
				red("bad urls: {}\n"), total, urls.size(), unhandled.size());


		info(cyan("Total: ")+(urls.size() - unhandled.size()));
		info(cyan("---------------------------------------------"));
		int len = Stream.of(configs).map(ConfigWrapper::name).mapToInt(String::length).max().getAsInt();
		len = Math.max(len, "method_name".length());

		String format = "%-"+(len+5)+"s%s\n";
		info(yellow(String.format(format, "method_name", "count")));
		for (ConfigWrapper c : configs) 
			System.out.printf(format, c.name(), c.urls.size());
		info(cyan("---------------------------------------------"));
		info("\n");

		// TODO Auto-generated method stub

	}
}