package rss.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.string.StringWriter2;
import scrapper.Utils;

public class Handler2 extends Handler {
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
	private final boolean debug, disable;
	public Handler2() {
		debug = "true".equals(LogManager.getLogManager().getProperty(getClass().getName().concat(".debug")));
		disable = "true".equals(LogManager.getLogManager().getProperty(getClass().getName().concat(".disable")));
	}

	@Override
	public synchronized void publish(LogRecord record) {
		if(disable)
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

		if(debug) 
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
