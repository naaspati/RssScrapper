package rss.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.string.StringWriter2;
import scrapper.Utils;

// import sam.logging.LogFilter;

public class LogHandler extends Handler {
	private static final FileWriter WRITER;

	static {
		WRITER = MyUtilsException.noError(() -> new FileWriter(MyUtilsPath.TEMP_DIR.resolve("log-"+MyUtilsPath.pathFormattedDateTime()+".log").toFile(), true));

		Utils.addShutDownTask(() -> {
			try {
				WRITER.flush();
				WRITER.close();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		});
	}
	private final StringBuilder sb = new StringBuilder();
	private final boolean stacktrace; 
	
	public LogHandler() {
		LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();
        setLevel(Optional.ofNullable(manager.getProperty(cname +".level")).map(String::trim).map(Level::parse).orElse(Level.INFO));
        setFilter(Optional.ofNullable(manager.getProperty(cname +".filter")).map(String::trim).<Filter>map(this::parse).orElse(null));
        stacktrace =  Optional.ofNullable(manager.getProperty(cname +".stacktrace")).map(String::trim).map("true"::equals).orElse(false);
        
        try {
        	setEncoding(Optional.ofNullable(manager.getProperty(cname +".encoding")).orElse("utf-8"));
        } catch (Exception ex) {
            try {
                setEncoding("utf-8");
            } catch (Exception ex2) {}
        }
	}
	@SuppressWarnings("unchecked")
	private <E> E parse(String clsname) {
		try {
			return (E) Class.forName(clsname).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			reportError("failed to parse class: "+clsname, e, ErrorManager.GENERIC_FAILURE);
		}
		return null;
	}

	@Override
	public synchronized void publish(LogRecord record) {
		if(!isLoggable(record) || (getFilter() != null && !getFilter().isLoggable(record)))
			return;
		
		sb.setLength(0);
		format(record);

		if(sb.length() == 0)
			return;

		sb.append('\n');

		char[] buffer = new char[sb.length()];
		sb.getChars(0, sb.length(), buffer, 0);

		System.out.print(buffer);

		try {
			WRITER.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void format(LogRecord record) {
		Level l = record.getLevel();
		if(l == Level.WARNING || l == Level.SEVERE)
			error(record);
		else 
			simple(record, l);
	}

	private void simple(LogRecord record, Level l) {
		sb.append(record.getLoggerName()).append(":").append(l).append(": ").append(record.getMessage()).toString();
	}

	private void error(LogRecord record) {

		sb.append(record.getLoggerName()).append('\n')
		.append("  level  : ").append(record.getLevel()).append('\n')
		.append("  msg    : ").append(record.getMessage()).append('\n')
		.append("  class  : ").append(record.getSourceClassName()).append('\n')
		.append("  method : ").append(record.getSourceMethodName()).append('\n');

		Throwable t = record.getThrown();

		if(t == null)
			return;

		if(stacktrace) 
			t.printStackTrace(new PrintWriter(new StringWriter2(sb)));
		else 
			sb.append("  error: ").append(t.getClass().getSimpleName()).append('[').append(t.getMessage()).append('\n');
	}
	@Override
	public void flush() {
		try {
			WRITER.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void close() throws SecurityException {

	}
}
