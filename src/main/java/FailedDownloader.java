import static sam.console.ansi.ANSI.red;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ansi.ANSI;
import sam.myutils.myutils.MyUtils;
import scrapper.Config;
import scrapper.scrapper.DataStore;
import scrapper.scrapper.DownloadTask;

public class FailedDownloader {
    
    FailedDownloader() throws IOException {
        Logger logger = LoggerFactory.getLogger(getClass());
        
        if(Files.notExists(Config.DOWNLOAD_DIR)) {
            logger.error(red("file not found: ")+Config.DOWNLOAD_DIR);
            return;
        }
        if(DataStore.FAILED_DOWNLOADS.getData().isEmpty()){
            logger.info(red("no failed found "));
            return;
        }
        
        ExecutorService ex = Executors.newFixedThreadPool(Config.THREAD_COUNT);
        
        DataStore.FAILED_DOWNLOADS_MAP.getData()
        .values().stream()
        .flatMap(m -> m.values().stream())
        .map(DownloadTask::new)
        .forEach(ex::execute);

        ex.shutdown();
        try {
            ex.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            logger.error("pool intrupped", e);
        }

        DataStore.saveClientCopy();
        logger.info(ANSI.FINISHED_BANNER); 
        MyUtils.beep(5);
    }
}
