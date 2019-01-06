package rss.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Optional;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import sam.myutils.MyUtilsPath;
import sam.string.StringWriter2;


public class LogHandler extends Handler {
	private final StringBuilder sb = new StringBuilder();
	private final boolean stacktrace;
	private volatile Writer writer;

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
		if(writer == null) {
			try {
				writer = new FileWriter(MyUtilsPath.TEMP_DIR.resolve("log-"+MyUtilsPath.pathFormattedDateTime()+".log").toFile(), true);
			} catch (IOException e) {
				reportError("failed to open logfile", e, ErrorManager.OPEN_FAILURE);
			}
		}
		
		Level l = record.getLevel();
		if(isLoggable(record) || l == Level.SEVERE && l == Level.WARNING)
			publish0(record);
	}
	private void publish0(LogRecord record) {
		sb.setLength(0);
		format(record);

		if(sb.length() == 0)
			return;

		sb.append('\n');

		char[] buffer = new char[sb.length()];
		sb.getChars(0, sb.length(), buffer, 0);

		System.out.print(buffer);

		try {
			writer.write(buffer);
			flush();
		} catch (IOException e) {
			reportError("failed to write", e, ErrorManager.WRITE_FAILURE);
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
			sb.append("  error: ").append(t.getClass().getSimpleName()).append('[').append(t.getMessage()).append("]\n");
	}
	@Override
	public void flush() {
		try {
			writer.flush();
		} catch (IOException e) {
			reportError("failed to flush", e, ErrorManager.FLUSH_FAILURE);
		}
	}
	@Override
	public void close() throws SecurityException {
		try {
			writer.close();
		} catch (IOException e) {
			reportError("failed to close", e, ErrorManager.CLOSE_FAILURE);
		}
	}
}
