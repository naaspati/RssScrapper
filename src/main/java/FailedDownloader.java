import static sam.console.ANSI.red;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;



import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.console.ANSI;
import sam.myutils.MyUtilsCmd;
import scrapper.EnvConfig;
import scrapper.scrapper.DataStore;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.ScrappingResult.DownloadTask;

public class FailedDownloader {
    
    FailedDownloader() throws IOException {
        Logger logger = LoggerFactory.getLogger(getClass());
        
        if(Files.notExists(EnvConfig.DOWNLOAD_DIR)) {
            logger.error(red("file not found: ")+EnvConfig.DOWNLOAD_DIR);
            return;
        }
        if(!ScrappingResult.hasFailed()){
            logger.info(red("no failed found "));
            return;
        }
        
        List<DownloadTask> list = ScrappingResult.keys()
                .stream()
                .flatMap(ScrappingResult::failedTasks)
                .collect(Collectors.toList());
        
        ExecutorService ex = Executors.newFixedThreadPool(Math.min(EnvConfig.THREAD_COUNT, list.size()));
        
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
