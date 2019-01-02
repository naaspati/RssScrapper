package scrapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public final class EnvConfig {

	public static final String APP_DATA = "app_data";
    public static final Path DOWNLOAD_DIR;
    public static final int CONNECT_TIMEOUT;
    public static final int READ_TIMEOUT;
    public static final int THREAD_COUNT;

    static {
    	
        System.out.println("Config: initiated");
        ResourceBundle rb = ResourceBundle.getBundle("config");

        CONNECT_TIMEOUT = Integer.parseInt(rb.getString("connect.timeout"));
        READ_TIMEOUT = Integer.parseInt(rb.getString("read.timeout"));
        DOWNLOAD_DIR = Paths.get(rb.getString("download.dir"));
        THREAD_COUNT = Integer.parseInt(rb.getString("thread.count"));
        
        ResourceBundle.clearCache();
    }
    
    private EnvConfig() {}
}
