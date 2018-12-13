import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import sam.console.ANSI;
import scrapper.MainView;
import scrapper.Utils;
public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length == 1 && args[0].equals("-v")) {
            System.out.println("1.015");
            System.exit(0);
        }
        
        new File("app_data/logs").mkdirs();
        System.setProperty("java.util.logging.config.file", "logging.properties");

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LoggerFactory.getLogger(Main.class).error("Thread: "+t.getName(), e);
            }
        });
        if(args.length == 1) {
            if(args[0].equals("h") || args[0].equals("-h") || args[0].equals("help") || args[0].equals("-help")) {
                LoggerFactory.getLogger(Main.class)
                .info(  "clean     clean cache\n"+
                        "open      open app dir\n"+
                        "--download   download links in failed-downlods.txt\n"
                        + "--file[File] load urls from a file"); 
            }

            if(args[0].equals("clean")) {
                Logger l = LoggerFactory.getLogger(Main.class);
                
                Path p = Paths.get("app_data");
                if(Files.exists(p)) {
                    Files.walk(p)
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(s -> l.info(s + "  " + (s.toFile().delete() ? ANSI.green("deleted") : ANSI.red("delete failed"))));
                }
                    
            }
            if(args[0].equals("open"))
                Utils.openFile(new File("."));
            if(args[0].equals("download"))
                new FailedDownloader();

            System.exit(0);
        }
        
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if("--file".equals(s)) {
                if(args.length < i + 2) {
                    System.out.println(ANSI.red("no file specified for: --file"));
                    System.exit(0);
                }
                String file = args[i+1];
                if(!new File(file).exists()){
                    System.out.println(ANSI.red("file not found: ")+file);
                    System.exit(0);
                }
                System.setProperty("urls-file", file);
            }
        }
        
        Application.launch(MainView.class, args);
    }
}
