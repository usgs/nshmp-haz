package gov.usgs.earthquake.nshmp.aws;

import static gov.usgs.earthquake.nshmp.aws.Util.CURVES_FILE;
import static gov.usgs.earthquake.nshmp.aws.Util.MAP_FILE;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.base.Enums;
import com.google.common.base.Throwables;

import gov.usgs.earthquake.nshmp.aws.Util.LambdaHelper;
import gov.usgs.earthquake.nshmp.calc.DataType;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.ServletUtil;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * AWS Lambda function to list all hazard results in the nshmp-hazout S3 bucket
 * that contain a map.csv file.
 */
@SuppressWarnings("unused")
public class HazardResultsMetadataLambda implements RequestStreamHandler {

  private static final AmazonS3 S3 = AmazonS3ClientBuilder.defaultClient();

  private static final int IMT_DIR_BACK_FROM_TOTAL = 2;
  private static final int IMT_DIR_BACK_FROM_SOURCE = 4;
  private static final String S3_BUCKET = "nshmp-hazout";
  private static final String RESULT_BUCKET = "nshmp-haz-lambda";
  private static final String RESULT_KEY = "nshmp-haz-aws-results-metadata.json";

  @Override
  public void handleRequest(
      InputStream input,
      OutputStream output,
      Context context) throws IOException {
    LambdaHelper lambdaHelper = new LambdaHelper(input, output, context);

    try {
      Response response = processRequest();
      String json = GSON.toJson(response, Response.class);
      uploadResults(json);
      output.write(json.getBytes());
      output.close();
    } catch (Exception e) {
      lambdaHelper.logger.log("\nError: " + Throwables.getStackTraceAsString(e) + "\n\n");
      String message = Metadata.errorMessage("", e, false);
      output.write(message.getBytes());
    }
  }

  private static Response processRequest() {
    Map<String, CurvesMapResult> curvesMapResults = new HashMap<>();
    Set<String> users = getUsers();

    for (String file : new String[] { CURVES_FILE, MAP_FILE }) {
      List<HazardResults> hazardResults = listObjects(users, file);
      CurvesMapResult result = new CurvesMapResult(users, hazardResults);
      curvesMapResults.put(file, result);
    }

    Result result = new Result(curvesMapResults.get(CURVES_FILE), curvesMapResults.get(MAP_FILE));
    return new Response(result);
  }

  private static List<HazardResults> listObjects(Set<String> users, String file) {
    ListObjectsV2Request request = new ListObjectsV2Request()
        .withBucketName(S3_BUCKET)
        .withDelimiter(file);
    ListObjectsV2Result s3Result;
    List<S3Listing> s3Listings = new ArrayList<>();

    do {
      s3Result = S3.listObjectsV2(request);
      s3Result.getCommonPrefixes()
          .stream()
          .map(key -> keyToHazardListing(key))
          .forEach(listing -> s3Listings.add(listing));

      request.setContinuationToken(s3Result.getNextContinuationToken());
    } while (s3Result.isTruncated());

    return transformS3Listing(users, s3Listings);
  }

  private static List<HazardResults> transformS3Listing(Set<String> users, List<S3Listing> s3Listings) {
    List<HazardResults> hazardResults = new ArrayList<>();

    users.forEach(user -> {
      TreeSet<String> resultDirectories = s3Listings.stream()
          .filter(listing -> listing.user.equals(user))
          .map(listing -> listing.resultPrefix)
          .collect(Collectors.toCollection(TreeSet::new));

      resultDirectories.forEach(resultPrefix -> {
        List<S3Listing> s3Filteredlistings = s3Listings.parallelStream()
            .filter(listing -> listing.user.equals(user))
            .filter(listing -> listing.resultPrefix.equals(resultPrefix))
            .collect(Collectors.toList());

        List<HazardListing> listings = s3Filteredlistings.parallelStream()
            .map(listing -> s3ListingToHazardListing(listing))
            .collect(Collectors.toList());

        S3Listing s3Listing = s3Filteredlistings.get(0);
        String path = s3Listing.path.split(resultPrefix)[0];
        String s3Path = s3Listing.user + "/" + path + resultPrefix;

        hazardResults.add(new HazardResults(
            user,
            s3Listing.bucket,
            resultPrefix,
            s3Path,
            listings));
      });
    });

    return hazardResults;
  }

  private static HazardListing s3ListingToHazardListing(S3Listing s3Listing) {
    return new HazardListing(s3Listing.dataType, s3Listing.path, s3Listing.file);
  }

  private static S3Listing keyToHazardListing(String key) {
    List<String> keys = Parsing.splitToList(key, Delimiter.SLASH);
    HazardDataType<?> dataType = getDataType(keys);
    String user = keys.get(0);
    String file = keys.get(keys.size() - 1);
    String path = keys.subList(1, keys.size() - 1)
        .stream()
        .collect(Collectors.joining("/"));

    return new S3Listing(user, S3_BUCKET, path, file, dataType);
  }

  private static Set<String> getUsers() {
    ListObjectsV2Request request = new ListObjectsV2Request()
        .withBucketName(S3_BUCKET)
        .withDelimiter("/");

    ListObjectsV2Result listing = S3.listObjectsV2(request);

    return listing.getCommonPrefixes().stream()
        .map(prefix -> prefix.replace("/", ""))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static HazardDataType<?> getDataType(List<String> keys) {
    String sourceType = keys.get(keys.size() - IMT_DIR_BACK_FROM_TOTAL);
    HazardDataType<?> dataType = null;
    String resultDirectory = null;
    Imt imt = null;

    if (Enums.getIfPresent(SourceType.class, sourceType).isPresent()) {
      imt = Imt.valueOf(keys.get(keys.size() - IMT_DIR_BACK_FROM_SOURCE));
      resultDirectory = keys.get(keys.size() - IMT_DIR_BACK_FROM_SOURCE - 1);
      SourceType type = SourceType.valueOf(sourceType);
      dataType = new HazardDataType<SourceType>(imt, DataType.SOURCE, type, resultDirectory);
    } else if (Enums.getIfPresent(Gmm.class, sourceType).isPresent()) {
      imt = Imt.valueOf(keys.get(keys.size() - IMT_DIR_BACK_FROM_SOURCE));
      resultDirectory = keys.get(keys.size() - IMT_DIR_BACK_FROM_SOURCE - 1);
      Gmm type = Gmm.valueOf(sourceType);
      dataType = new HazardDataType<Gmm>(imt, DataType.GMM, type, resultDirectory);
    } else if (Enums.getIfPresent(Imt.class, sourceType).isPresent()) {
      Imt type = Imt.valueOf(sourceType);
      resultDirectory = keys.get(keys.size() - IMT_DIR_BACK_FROM_TOTAL - 1);
      imt = type;
      dataType = new HazardDataType<Imt>(imt, DataType.TOTAL, type, resultDirectory);
    } else {
      throw new RuntimeException("Source type [" + sourceType + "] not supported");
    }

    return dataType;
  }

  private static void uploadResults(String results) {
    byte[] bytes = results.getBytes();
    ByteArrayInputStream input = new ByteArrayInputStream(bytes);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(bytes.length);
    metadata.setContentType("application/json");

    PutObjectRequest request = new PutObjectRequest(
        RESULT_BUCKET,
        RESULT_KEY,
        input,
        metadata);

    S3.putObject(request);
  }

  static class HazardDataType<E extends Enum<E>> {
    final Imt imt;
    final DataType type;
    final transient String resultPrefix;
    final E sourceType;

    HazardDataType(Imt imt, DataType type, E sourceType, String resultPrefix) {
      this.imt = imt;
      this.type = type;
      this.resultPrefix = resultPrefix;
      this.sourceType = sourceType;
    }
  }

  private static class HazardResults {
    final String user;
    final String bucket;
    final String resultPrefix;
    final String path;
    final List<HazardListing> listings;

    HazardResults(
        String user,
        String bucket,
        String resultPrefix,
        String path,
        List<HazardListing> listings) {
      this.user = user;
      this.bucket = bucket;
      this.resultPrefix = resultPrefix;
      this.path = path;
      this.listings = listings;
    }
  }

  private static class HazardListing {
    final HazardDataType<?> dataType;
    final String file;
    final String path;

    HazardListing(HazardDataType<?> dataType, String path, String file) {
      this.dataType = dataType;
      this.file = file;
      this.path = path;
    }
  }

  private static class S3Listing {
    final String user;
    final String bucket;
    final String path;
    final String file;
    final String resultPrefix;
    final HazardDataType<?> dataType;

    S3Listing(String user, String bucket, String path, String file, HazardDataType<?> dataType) {
      this.user = user;
      this.bucket = bucket;
      this.path = path;
      this.file = file;
      this.resultPrefix = dataType.resultPrefix;
      this.dataType = dataType;
    }
  }

  private static class CurvesMapResult {
    final Set<String> users;
    final List<HazardResults> hazardResults;

    CurvesMapResult(Set<String> users, List<HazardResults> hazardResults) {
      this.users = users;
      this.hazardResults = hazardResults;
    }
  }

  private static class Result {
    final CurvesMapResult curves;
    final CurvesMapResult map;

    Result(CurvesMapResult curves, CurvesMapResult map) {
      this.curves = curves;
      this.map = map;
    }
  }

  private static class Response {
    final String status;
    final String date;
    final Result result;

    Response(Result result) {
      status = Status.SUCCESS.toString();
      date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
      this.result = result;
    }
  }
}
