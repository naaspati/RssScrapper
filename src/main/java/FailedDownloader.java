import static sam.console.ANSI.red;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sam.console.ANSI;
import sam.myutils.MyUtilsCmd;
import scrapper.Utils;
import scrapper.scrapper.DownloadTasks;

public class FailedDownloader {
    
    FailedDownloader() throws IOException {
        Logger logger = Utils.logger(getClass());
        
        if(Files.notExists(Utils.DOWNLOAD_DIR)) {
            logger.error(red("file not found: ")+Utils.DOWNLOAD_DIR);
            return;
        }
        if(!DownloadTasks.hasFailed()){
            logger.info(red("no failed found "));
            return;
        }
        
        List<DownloadTask> list = DownloadTasks.keys()
                .stream()
                .flatMap(DownloadTasks::failedTasks)
                .collect(Collectors.toList());
        
        ExecutorService ex = Executors.newFixedThreadPool(Math.min(Utils.THREAD_COUNT, list.size()));
        
        System.out.println(ANSI.yellow("total: ")+list.size());
        list.forEach(ex::execute);
        
        ex.shutdown();
        
        try {
            ex.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            logger.error("pool intrupped", e);
        }
        DataStore.saveClientCopy();
        logger.info(ANSI.FINISHED_BANNER); 
        MyUtilsCmd.beep(5);
    }
}
