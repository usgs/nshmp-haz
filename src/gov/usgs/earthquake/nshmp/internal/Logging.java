package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.util.logging.Level.SEVERE;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Logging utilities.
 * @author Peter Powers
 */
public class Logging {

  private static final String LF = LINE_SEPARATOR.value();

  /**
   * Initialize logging from {@code logging.properties}.
   */
  public static void init() {
    try {
      InputStream is = Logging.class.getResourceAsStream("/logging.properties");
      LogManager.getLogManager().readConfiguration(is);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * Log a resource loading error and exit.
   *
   * @param clazz for which resource is required
   * @param e the exception that was thrown
   */
  public static void handleResourceError(Class<?> clazz, Exception e) {
    Logger log = Logger.getLogger(clazz.getName());
    StringBuilder sb = new StringBuilder(LF);
    sb.append("** Error loading resource: ").append(e.getMessage()).append(LF);
    log.log(SEVERE, sb.toString(), e);
    throw new RuntimeException(e);
  }

  /**
   * Custom console formatter.
   */
  public final static class ConsoleFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
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
        b.append(record.getMessage());
        if (record.getThrown() != null) {
          b.append(LF).append(LF);
          b.append(Throwables.getStackTraceAsString(record.getThrown()));
        }
      } else {
        b.append(" ").append(record.getMessage());
      }
      b.append(LF);
      return b.toString();
    }

  }

}
