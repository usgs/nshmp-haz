package gov.usgs.earthquake.nshmp.www.meta;

import com.google.common.collect.ImmutableMap;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import gov.usgs.earthquake.nshmp.HazardCalc;

/*
 * Application and model version data. References are string-based as opposed to
 * enum-based (e.g. Edition) to avoid circular references in enum
 * initializations.
 */
class Versions {

  
  static final String NSHMP_HAZ_VERSION = HazardCalc.VERSION;
  static final String NSHMP_HAZ_WS_VERSION;
  private static final Map<String, String> MODEL_VERSIONS;
  private static final String UNKNOWN = "unknown";

  static {
    String nshmpHazWsVersion = UNKNOWN;
    ImmutableMap.Builder<String, String> modelMap = ImmutableMap.builder();

    /* Always runs from a war (possibly unpacked). */
    try (InputStream in = Metadata.class.getResourceAsStream("/service.properties")){
      Properties props = new Properties();
      props.load(in);
      in.close();

      for (String key : props.stringPropertyNames()) {
        String value = props.getProperty(key);
        /* Web-services version. */
        if (key.equals("app.version")) {
          nshmpHazWsVersion = value;
        }
        /* Model versions. */
        modelMap.put(key, value);
      }
    } catch (Exception e) {
      /* Do nothing; probably running outside standard build. */
    }
    
    NSHMP_HAZ_WS_VERSION = nshmpHazWsVersion;
    MODEL_VERSIONS = modelMap.build();
  }

  static String modelVersion(String id) {
    return MODEL_VERSIONS.getOrDefault(id + ".version", UNKNOWN);
  }

}
