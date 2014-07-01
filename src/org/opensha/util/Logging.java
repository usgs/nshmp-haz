package org.opensha.util;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.base.Strings;

/**
 * Logging utilities.
 * @author Peter Powers
 */
public class Logging {

	private static final String LF = LINE_SEPARATOR.value();
	public static final String WARN_INDENT = "          ";

	static {
		try {
			InputStream is = new FileInputStream(
				"/Users/pmpowers/projects/git/nshmp-sha/lib/logging.properties");
			LogManager.getLogManager().readConfiguration(is);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}	
	
	/**
	 * Creates a new {@code Logger} for the supplied class.
	 * @param clazz to create {@code Logger} for
	 * @return a new {@code Logger}
	 */
	@Deprecated
	public static Logger create(Class<?> clazz) {
		Logger log = Logger.getLogger(clazz.getName());
//		log.setUseParentHandlers(true);
		// TODO clean
//		log.setUseParentHandlers(true);
//		Handler[] handlers = log.getHandlers();
//		System.out.println("hello " + handlers.length);
//		System.out.println(log.getLevel());
//		System.out.println(log.getParent().getLevel());
//		System.out.println(log.getParent().getHandlers()[0].getFormatter().getClass());
//		for (Handler handler : handlers) {
//			
//			System.out.println("  " + handler);
//		}
//		Handler ch = new ConsoleHandler();
//		ch.setFormatter(consoleFormatter);
//		System.out.println(log);
//		System.out.println(log.getLevel());
//		ch.setLevel(log.getLevel());
//		log.addHandler(ch);
		return log;
	}
	
	/**
	 * Custom console formatter.
	 * @author Peter Powers
	 */
	public final static class ConsoleFormatter extends Formatter {

		@Override
		public String format(LogRecord record) {
			// @formatter:off
			StringBuilder b = new StringBuilder();
			Level l = record.getLevel();
			b.append(Strings.padStart(l.toString(), 7, ' '));
			if (l == Level.SEVERE || l == Level.WARNING) {
				String cName = record.getSourceClassName();
				b.append(" \u0040 ")
					.append(cName.substring(cName.lastIndexOf(".") + 1))
					.append(".")
					.append(record.getSourceMethodName())
					.append("()");
				if (record.getThrown() != null) {
					b.append(" ==> ");
					b.append(record.getThrown());
				}
				b.append(LF).append(record.getMessage());
			} else {
				b.append(" ").append(record.getMessage());
			}
			b.append(LF);
			return b.toString();
			// @formatter:on
		}
		
	}

}
