package scrapper;

import static java.util.stream.Collectors.toSet;
import static sam.console.ANSI.red;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

import org.slf4j.LoggerFactory;

public final class EnvConfig {

    public static final Path DOWNLOAD_DIR;
    public static final int CONNECT_TIMEOUT;
    public static final int READ_TIMEOUT;
    public static final int THREAD_COUNT;

    public static final Set<String> DISABLED_SCRAPPERS;

    static {
        System.out.println("Config: initiated");
        ResourceBundle rb = ResourceBundle.getBundle("config");

        CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect.timeout"));
        READ_TIMEOUT = Integer.parseInt(rb.getString("read.timeout"));
        DOWNLOAD_DIR = Paths.get(rb.getString("download.dir"));
        THREAD_COUNT = Integer.parseInt(rb.getString("thread.count"));

        Path p = Paths.get("disabled.txt");
        Set<String> temp = null;
        try {
            temp = Files.exists(p) ? Files.lines(p).collect(toSet()) : new HashSet<>();
        } catch (IOException e1) {
            throw new RuntimeException("failed reading "+p, e1);
        }
        DISABLED_SCRAPPERS = temp;
        if(!DISABLED_SCRAPPERS.isEmpty())
            LoggerFactory.getLogger(EnvConfig.class).info(red("Disabled: ")+DISABLED_SCRAPPERS);

        ResourceBundle.clearCache();
    }
    
    private EnvConfig() {}

}
