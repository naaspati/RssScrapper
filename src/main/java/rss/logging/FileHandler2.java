package rss.logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class FileHandler2 extends FileHandler {

    public FileHandler2() throws IOException, SecurityException {
        super();
    }
    public FileHandler2(String pattern, boolean append) throws IOException, SecurityException {
        super(pattern, append);
    }
    public FileHandler2(String pattern, int limit, int count, boolean append) throws IOException, SecurityException {
        super(pattern, limit, count, append);
    }
    public FileHandler2(String pattern, int limit, int count) throws IOException, SecurityException {
        super(pattern, limit, count);
    }
    public FileHandler2(String pattern) throws IOException, SecurityException {
        super(pattern);
    }
    
    @Override
    public boolean isLoggable(LogRecord record) {
        if(record.getLevel() == Level.INFO)
            return false;
        return super.isLoggable(record);
    }
}
