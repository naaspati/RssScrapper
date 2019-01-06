package scrapper;
import static sam.console.ANSI.red;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import sam.console.ANSI;
import sam.myutils.Checker;
import sam.myutils.MyUtilsCmd;
import sam.nopkg.Junk;
import scrapper.Handler.DUrls;

public class FailedDownloader {
	private final Logger logger = Utils.logger(getClass());
	private class Failed implements Runnable {
		final DUrls durl;
		final String url;
		
		boolean success;
		
		public Failed(DUrls durl, String url) {
			this.durl = durl;
			this.url = url;
			//FIXME fix run
			Junk.notYetImplemented();
			
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
		}
	}
    
    public FailedDownloader() throws IOException {
        if(Files.notExists(Utils.DOWNLOAD_DIR)) {
            logger.error(red("file not found: ")+Utils.DOWNLOAD_DIR);
            return;
        }
        
        Handler[] handlers = Handler.loadCached();
        
        if(Checker.isEmpty(handlers)){
            logger.info(red("no failed found "));
            return;
        }
        
        List<Failed> failed = new ArrayList<>();
        
        for (Handler h : handlers) 
			h.getDurls().forEach(d -> d.eachFailed(s -> failed.add(new Failed(d, s))));
        
        if(failed.isEmpty()){
            logger.info(red("no failed found "));
            return;
        }
        
        ExecutorService ex = Executors.newFixedThreadPool(Math.min(Utils.THREAD_COUNT, failed.size()));
        
        System.out.println(ANSI.yellow("total: ")+failed.size());
        failed.forEach(ex::execute);
        
        ex.shutdown();
        
        try {
            ex.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            logger.error("pool intrupped", e);
        }
        
        for (Handler h : handlers) {
        	try {
        		h.close();
			} catch (Exception e) {
				logger.error("failed to close handler: "+ h);
			}
        }
        
        System.out.println("failed: "+failed.stream().filter(s -> !s.success).count());
        
        logger.info(ANSI.FINISHED_BANNER); 
        MyUtilsCmd.beep(5);
    }
}
