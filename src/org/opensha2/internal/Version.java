package org.opensha2.internal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application version tracking.
 * 
 * @author Peter Powers
 */
public class Version {

  /** Application version. */
  public static final String APP_VERSION;

  /** Build date. */
  public static final String BUILD_DATE;

  static {

    String appVersion = "unknown";
    String buildDate = "now";

    /* Assume we're running from a jar. */
    try {
      InputStream is = Version.class.getResourceAsStream("/app.properties");
      Properties props = new Properties();
      props.load(is);
      is.close();
      appVersion = props.getProperty("app.version");
      buildDate = props.getProperty("build.date");
    } catch (Exception e1) {
      /* Otherwise check for a repository. */
      Path gitDir = Paths.get(".git");
      if (Files.exists(gitDir)) {
        try {
          Process pr = Runtime.getRuntime().exec("git describe --tags");
          BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
          appVersion = br.readLine();
          br.close();
          /* Detached from repository. */
        } catch (Exception e2) {}
      }
    }

    APP_VERSION = appVersion;
    BUILD_DATE = buildDate;
  }

}
