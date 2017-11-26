import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import com.jaunt.UserAgent;

import javafx.application.Application;
import sam.console.ansi.ANSI;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import scrapper.MainView;

public class Main {

    public static void main(String[] args) throws Exception {
        if(args.length == 1) {
            if(args[0].equals("h") || args[0].equals("-h") || args[0].equals("help") || args[0].equals("-help")) {
                System.out.println("clean     clean cache");
                System.out.println("open      open app dir");
                System.out.println("download   download links in failed-downlods.txt"); 

            }

            if(args[0].equals("clean")) {
                Stream.of("successfuls.dat",
                        "urlStoreMap.dat",
                        "urlSavePathMap.dat")
                .forEach(s -> System.out.println(s+"  "+new File(s).delete()));
            }
            if(args[0].equals("open"))
                FilesUtils.openFile(new File("."));
            if(args[0].equals("download"))
                download();

            System.exit(0);
        }

        checkVersion();
        ResourceBundle rb = ResourceBundle.getBundle("config");
        Path rootDir = Paths.get(rb.getString("root.dir"));

        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            System.out.println("failed to create dir: "+rootDir+"  "+e);
            JOptionPane.showMessageDialog(null, "failed to create dir");
            System.exit(0);
        }
        Application.launch(MainView.class, args);
    }
    private static void download() throws IOException {
        final Path path = Paths.get("D:\\Downloads\\scrapper\\failed-downlods.txt");

        if(Files.notExists(path)) {
            System.out.println(red("file not found")+path);
            return;
        }

        List<String> lines = Files.lines(path).filter(s -> !s.trim().isEmpty() && s.indexOf('\t') > 0).collect(Collectors.toList());
        if(lines.isEmpty()){
            System.out.println(red("nothing found in\n")+path);
            return;
        }

        ResourceBundle rb = ResourceBundle.getBundle("config");

        int CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect_timeout"));
        int READ_TIMEOUT = Integer.parseInt(rb.getString("read_timeout"));

        Map<String, String> fileext_mimeMap = new ConcurrentHashMap<>();

        try(InputStream is = ClassLoader.getSystemResourceAsStream("1509617391333-file.ext-mime.tsv");
                InputStreamReader reader = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(reader)) {

            br.lines()
            .filter(s -> !s.startsWith("#") && s.indexOf('\t') > 0).map(s -> s.split("\t"))
            .collect(Collectors.toMap(s -> s[1], s -> s[2], (o, n) -> n, () -> fileext_mimeMap));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        ExecutorService ex = Executors.newFixedThreadPool(Integer.parseInt(rb.getString("thread_count")));

        rb = null;
        ResourceBundle.clearCache();
        AtomicInteger failed = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<String> failedList = Collections.synchronizedList(new ArrayList<>());
        List<String> successList = Collections.synchronizedList(new ArrayList<>());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(ANSI.red("failed: ")+failed.get());

            try {
                if(completed.get()) {
                    if(failedList.isEmpty())
                        Files.deleteIfExists(path);
                    else
                        Files.write(path, failedList, StandardOpenOption.TRUNCATE_EXISTING);
                    return;
                }

                Files.write(path, 
                        Files.lines(path)
                        .filter(s -> !successList.contains(s))
                        .collect(Collectors.toList()), StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                System.out.println(red("failed to save file: ")+MyUtils.exceptionToString(e));
            }
        }));

        lines.stream()
        .map(s -> {
            return runnable(() -> {
                try {
                    URL url = new URL(s.substring(0, s.indexOf('\t')));

                    URLConnection con = url.openConnection();
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");
                    con.setConnectTimeout(CONNECT_TIMEOUT);
                    con.setReadTimeout(READ_TIMEOUT);
                    con.connect();

                    String ext = Optional.ofNullable(con.getContentType())
                            .map(fileext_mimeMap::get)
                            .orElse(".jpg");

                    Path target = Paths.get(s.substring(s.indexOf('\t')+1) + (s.endsWith(ext) ? "" : ext));
                    Path temp = Files.createTempFile("rss", "");
                    if(Files.exists(target)) {
                        System.out.println(url+"\n"+yellow(target));
                        return;
                    }
                    InputStream is = con.getInputStream();
                    Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
                    is.close();
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);

                    System.out.println(url+"\n"+yellow(target));
                    successList.add(s);
                } catch (IOException e) {
                    System.out.println(red("failed: ")+s+"  "+MyUtils.exceptionToString(e));
                    failed.incrementAndGet();
                    failedList.add(s);
                }
            });
        })
        .forEach(ex::execute);

        ex.shutdown();
        while(!ex.isTerminated()) {}
        
        completed.getAndSet(true);

        System.out.println(ANSI.FINISHED_BANNER); 
        MyUtils.beep(5);
    }
    private static Runnable runnable(Runnable r) {
        return r;
    }
    private static void checkVersion() {
        String s = UserAgent.getVersionInfo();
        s = s.substring(s.lastIndexOf("Expiry")+"Expiry".length()+1);
        LocalDate date = LocalDate.parse(s, DateTimeFormatter.ofPattern("MMM dd, yyyy"));

        if(LocalDate.now().isAfter(date)) {
            System.out.println(red("Jaunt Expired: ")+yellow(UserAgent.getVersionInfo()));
            JOptionPane.showMessageDialog(null, UserAgent.getVersionInfo(), "Jaunt Expired", JOptionPane.ERROR_MESSAGE, null);
            System.exit(-1);
        }
    }

}
