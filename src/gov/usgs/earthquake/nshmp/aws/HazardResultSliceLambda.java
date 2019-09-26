package gov.usgs.earthquake.nshmp.aws;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.aws.Util.CURVES_FILE;
import static gov.usgs.earthquake.nshmp.aws.Util.MAP_FILE;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import gov.usgs.earthquake.nshmp.aws.Util.LambdaHelper;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.ServletUtil;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * AWS Lambda function to read in a curves file from AWS S3 and create slices at
 * return periods interest. <br>
 * 
 * The results are written to S3 as map.csv bucket.
 */
@SuppressWarnings("unused")
public class HazardResultSliceLambda implements RequestStreamHandler {

  private static final AmazonS3 S3 = AmazonS3ClientBuilder.defaultClient();

  private static final String RATE_FMT = "%.8e";
  private static final Function<Double, String> FORMATTER = Parsing.formatDoubleFunction(RATE_FMT);

  private static final int NUMBER_OF_HEADERS = 3;
  private static final String CONTENT_TYPE = "text/csv";

  private static final Interpolator INTERPOLATOR = Interpolator.builder()
      .logx()
      .logy()
      .decreasingX()
      .build();

  @Override
  public void handleRequest(
      InputStream input,
      OutputStream output,
      Context context) throws IOException {
    LambdaHelper lambdaHelper = new LambdaHelper(input, output, context);
    String requestBucket = "";

    try {
      RequestData request = GSON.fromJson(lambdaHelper.requestJson, RequestData.class);
      lambdaHelper.logger.log("Request Data: " + GSON.toJson(request) + "\n");
      requestBucket = request.bucket + "/" + request.key;
      checkRequest(request);
      Response response = processRequest(request);
      String json = GSON.toJson(response, Response.class);
      lambdaHelper.logger.log("Result: " + json + "\n");
      output.write(json.getBytes());
      output.close();
    } catch (Exception e) {
      lambdaHelper.logger.log("\nError: " + Throwables.getStackTraceAsString(e) + "\n\n");
      String message = Metadata.errorMessage(requestBucket, e, false);
      output.write(message.getBytes());
    }
  }

  private static Response processRequest(RequestData request) throws IOException {
    List<InterpolatedData> data = readCurveFile(request);
    String outputBucket = request.bucket + "/" + request.key;
    StringBuilder csv = new StringBuilder();
    createHeaderString(csv, request);
    createDataString(csv, data);
    writeResults(request, outputBucket, csv.toString().getBytes(Charsets.UTF_8));
    return new Response(request, outputBucket);
  }

  private static List<InterpolatedData> readCurveFile(RequestData request) throws IOException {
    S3Object object = S3.getObject(request.bucket, request.key + "/" + CURVES_FILE);
    S3ObjectInputStream input = object.getObjectContent();
    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    List<String> lines = reader.lines().collect(Collectors.toList());
    reader.close();

    Optional<List<String>> header = lines.stream()
        .filter(line -> !line.startsWith("#"))
        .findFirst()
        .map(line -> Parsing.splitToList(line, Delimiter.COMMA));

    checkState(header.isPresent(), "Curve file is empty");

    List<String> keys = header.get().subList(0, NUMBER_OF_HEADERS);
    List<Double> imls = header.get().subList(NUMBER_OF_HEADERS, header.get().size())
        .stream()
        .map(iml -> Double.parseDouble(iml))
        .collect(Collectors.toList());

    List<InterpolatedData> data = new ArrayList<>();
    lines.stream()
        .filter(line -> !line.startsWith("#"))
        .skip(1)
        .forEach(line -> {
          data.add(curveToInterpolatedData(request, line, keys, imls));
        });

    return data;
  }

  private static InterpolatedData curveToInterpolatedData(
      RequestData request,
      String line,
      List<String> keys,
      List<Double> imls) {
    List<String> values = Parsing.splitToList(line, Delimiter.COMMA);
    List<Double> gms = values.subList(NUMBER_OF_HEADERS, values.size())
        .stream()
        .map(gm -> Double.parseDouble(gm))
        .collect(Collectors.toList());
    values = values.subList(0, NUMBER_OF_HEADERS);

    Site site = buildSite(keys, values);
    List<Double> interpolatedValues = request.slices.stream()
        .map(returnPeriod -> INTERPOLATOR.findX(imls, gms, returnPeriod))
        .collect(Collectors.toList());

    return new InterpolatedData(site, interpolatedValues);
  }

  private static Site buildSite(List<String> keys, List<String> values) {
    Double lat = null;
    Double lon = null;
    String name = null;

    for (int index = 0; index < keys.size(); index++) {
      String key = keys.get(index);
      String value = values.get(index);

      switch (key) {
        case Keys.LAT:
          lat = Double.parseDouble(value);
          break;
        case Keys.LON:
          lon = Double.parseDouble(value);
          break;
        case Keys.NAME:
          name = value;
          break;
        default:
          throw new IllegalStateException("Unsupported site key: " + key);
      }
    }

    return Site.builder()
        .location(lat, lon)
        .name(name)
        .build();
  }

  private static void checkRequest(RequestData request) {
    if (request.bucket == null) {
      throw new RuntimeException("Request does not contain a S3 bucket");
    }

    if (request.key == null) {
      throw new RuntimeException("Request does not contain a S3 key");
    }

    if (request.slices == null) {
      throw new RuntimeException("Request does not contain returnPeriods");
    }
  }

  private static void createDataString(StringBuilder builder, List<InterpolatedData> data) {
    data.forEach(datum -> {
      List<String> locData = Lists.newArrayList(
          datum.site.name,
          String.format("%.5f", datum.site.location.longitude),
          String.format("%.5f", datum.site.location.latitude));
      builder.append(toLine(locData, datum.values) + "\n");
    });
  }

  private static String toLine(
      Iterable<String> strings,
      Iterable<Double> values) {
    return Parsing.join(
        Iterables.concat(strings, Iterables.transform(values, FORMATTER::apply)),
        Delimiter.COMMA);
  }

  private static void createHeaderString(StringBuilder builder, RequestData request) {
    List<String> header = Lists.newArrayList(Keys.NAME, Keys.LON, Keys.LAT);
    builder.append(toLine(header, request.slices) + "\n");
  }

  private static void writeResults(
      RequestData request,
      String outputBucket,
      byte[] result) throws IOException {
    ObjectMetadata metadata = new ObjectMetadata();

    InputStream input = new ByteArrayInputStream(result);
    metadata.setContentType(CONTENT_TYPE);
    metadata.setContentLength(result.length);
    PutObjectRequest putRequest = new PutObjectRequest(
        request.bucket,
        request.key + "/" + MAP_FILE,
        input,
        metadata);
    S3.putObject(putRequest);
    input.close();
  }

  static class RequestData {
    String bucket;
    String key;
    List<Double> slices;

    private RequestData(Builder builder) {
      bucket = builder.bucket;
      key = builder.key;
      slices = builder.slices;
    }

    static Builder builder() {
      return new Builder();
    }

    static class Builder {
      private String bucket;
      private String key;
      private List<Double> slices;

      Builder bucket(String bucket) {
        this.bucket = bucket;
        return this;
      }

      Builder key(String key) {
        this.key = key;
        return this;
      }

      Builder slices(List<Double> slices) {
        this.slices = slices;
        return this;
      }

      RequestData build() {
        return new RequestData(this);
      }

    }

  }

  private static class Response {
    final String status;
    final String date;
    final RequestData request;
    final String csv;

    Response(RequestData request, String outputBucket) {
      status = Status.SUCCESS.toString();
      date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
      this.request = request;
      this.csv = outputBucket + "/" + MAP_FILE;
    }

  }

  private static class InterpolatedData {
    Site site;
    List<Double> values;

    InterpolatedData(Site site, List<Double> values) {
      this.site = site;
      this.values = values;
    }
  }

  private static class Keys {
    static final String LAT = "lat";
    static final String LON = "lon";
    static final String NAME = "name";
  }

}
