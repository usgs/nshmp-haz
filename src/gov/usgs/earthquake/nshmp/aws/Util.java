package gov.usgs.earthquake.nshmp.aws;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Util {
  
  static final String CURVES_FILE = "curves.csv";
  static final String MAP_FILE = "map.csv";

  /**
   * Parse the Lambda function {@code InputStream} into an {@code JsonObject}.
   */
  static class LambdaHelper {
    JsonObject requestJson;
    Context context;
    LambdaLogger logger;
    OutputStream output;

    LambdaHelper(InputStream input, OutputStream output, Context context)
        throws UnsupportedEncodingException {
      logger = context.getLogger();
      this.context = context;
      this.output = output;

      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      JsonParser parser = new JsonParser();

      requestJson = parser.parse(reader).getAsJsonObject();
    }
  }

}
