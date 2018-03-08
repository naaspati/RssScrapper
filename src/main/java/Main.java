import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jaunt.UserAgent;

import javafx.application.Application;
import sam.console.ansi.ANSI;
import sam.myutils.fileutils.FilesUtils;
import scrapper.MainView;
public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length == 1 && args[0].equals("-v")) {
            System.out.println("1.004");
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
                        "download   download links in failed-downlods.txt"); 
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
                FilesUtils.openFile(new File("."));
            if(args[0].equals("download"))
                new FailedDownloader();

            System.exit(0);
        }

        checkVersion();
        Application.launch(MainView.class, args);
    }
    private static void checkVersion() {
        String s = UserAgent.getVersionInfo();
        s = s.substring(s.lastIndexOf("Expiry")+"Expiry".length()+1);
        LocalDate date = LocalDate.parse(s, DateTimeFormatter.ofPattern("MMM dd, yyyy"));

        if(LocalDate.now().isAfter(date)) {
            LoggerFactory.getLogger(Main.class).error(red("Jaunt Expired: ")+yellow(UserAgent.getVersionInfo()));
            JOptionPane.showMessageDialog(null, UserAgent.getVersionInfo(), "Jaunt Expired", JOptionPane.ERROR_MESSAGE, null);
            System.exit(-1);
        }
    }

}
