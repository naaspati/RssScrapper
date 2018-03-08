package rss.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class ConsoleHandler2 extends ConsoleHandler {

    @Override
    public void publish(LogRecord record) {
        if(record.getLevel() == Level.INFO)
            System.out.println(record.getMessage());
        else if(record.getLevel() == Level.WARNING)
            System.out.println(error(record));
        else
            super.publish(record);
    }

    private String error(LogRecord record) {
        Throwable t = record.getThrown();
        String msg = record.getMessage();
        
        if(t == null && msg == null)
            return "";

        StringBuilder sb = new StringBuilder();
         
        if(msg != null)
            sb.append(msg).append(t == null ? "" : "\n  ");
        
        if(t != null) {
            sb.append("error: [type  ")
            .append(t.getClass().getSimpleName());
            
            sb.append(", where: ")
            .append(record.getSourceClassName()).append('.')
            .append(record.getSourceMethodName()).append("(...)");
            
            if(t.getMessage() != null) {
                sb.append(", msg: ")
                .append(t.getMessage());
            }
            sb.append("] ");
        }
        return sb.toString();
    }

}
