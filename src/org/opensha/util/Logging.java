package org.opensha.util;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;

/**
 * Logging utilities.
 * @author Peter Powers
 */
public class Logging {

	private static final String LF = LINE_SEPARATOR.value();

	/**
	 * Initialize logging from {@code lib/logging.properties}.
	 */
	public static void init() {
		try {
			InputStream is = new FileInputStream("lib/logging.properties");
			LogManager.getLogManager().readConfiguration(is);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	public static void init(InputStream is) {
		try {
			LogManager.getLogManager().readConfiguration(is);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
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
			if (l == Level.WARNING) {
				String cName = record.getSourceClassName();
				b.append(" @ ")
					.append(cName.substring(cName.lastIndexOf(".") + 1))
					.append(".")
					.append(record.getSourceMethodName())
					.append("()");
				b.append(record.getMessage());
			} else if (l == Level.SEVERE) {
				String cName = record.getSourceClassName();
				b.append(" @ ")
					.append(cName.substring(cName.lastIndexOf(".") + 1))
					.append(".")
					.append(record.getSourceMethodName())
					.append("()");
				b.append(LF);
				b.append(record.getMessage());
				if (record.getThrown() != null) {
					b.append("Error trace:").append(LF);
					b.append(Throwables.getStackTraceAsString(record.getThrown()));
				}
			} else {
				b.append(" ").append(record.getMessage());
			}
			b.append(LF);
			return b.toString();
			// @formatter:on
		}
		
	}

}
