import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.slf4j.Logger;

import javafx.application.Application;
import sam.console.ANSI;
import sam.io.fileutils.FileOpener;
import sam.myutils.System2;
import scrapper.MainView;
import scrapper.ScrappingException;
import scrapper.Utils;
public class Main {
    public static void main(String[] args) throws ScrappingException, IOException {
    	Logger logger = Utils.logger(Main.class);
    	
        if(args.length == 1 && args[0].equals("-v")) {
            logger.info(System2.lookup("APP_VERSION"));
            System.exit(0);
        }

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error("Thread: "+t.getName(), e);
            }
        });
        if(args.length == 1) {
            if(args[0].equals("h") || args[0].equals("-h") || args[0].equals("help") || args[0].equals("-help")) {
                logger
                .info(  "clean     clean cache\n"+
                        "open      open app dir\n"+
                        "--download   download links in failed-downlods.txt\n"
                        + "--file[File] load urls from a file"); 
            }

            if(args[0].equals("clean")) {
                Path p = Paths.get("app_data");
                if(Files.exists(p)) {
                    Files.walk(p)
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(s -> logger.info(s + "  " + (s.toFile().delete() ? ANSI.green("deleted") : ANSI.red("delete failed"))));
                }
                    
            }
            if(args[0].equals("open"))
            	FileOpener.openFile(new File("."));
            if(args[0].equals("download"))
                new FailedDownloader();

            System.exit(0);
        }
        
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if("--file".equals(s)) {
                if(args.length < i + 2) {
                	logger.error(ANSI.red("no file specified for: --file"));
                    System.exit(0);
                }
                String file = args[i+1];
                if(!new File(file).exists()){
                    logger.error(ANSI.red("file not found: ")+file);
                    System.exit(0);
                }
                System.setProperty("urls-file", file);
            }
        }
        
        Application.launch(MainView.class, args);
    }
}
