package gov.usgs.earthquake.nshmp.www;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshmp.ResponseSpectra.spectra;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VSINF;
import static gov.usgs.earthquake.nshmp.gmm.Imt.AI;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGV;
import static gov.usgs.earthquake.nshmp.www.Util.readValue;
import static gov.usgs.earthquake.nshmp.www.Util.Key.IMT;
import static gov.usgs.earthquake.nshmp.www.meta.Metadata.errorMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Enums;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import gov.usgs.earthquake.nshmp.GroundMotions;
import gov.usgs.earthquake.nshmp.GroundMotions.DistanceResult;
import gov.usgs.earthquake.nshmp.ResponseSpectra.MultiResult;
import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Builder;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.meta.EnumParameter;
import gov.usgs.earthquake.nshmp.www.meta.ParamType;
import gov.usgs.earthquake.nshmp.www.meta.Status;
import gov.usgs.earthquake.nshmp.www.meta.Util;

@WebServlet(
    name = "Ground Motion Model Services",
    description = "Utilities for working with ground motion models",
    urlPatterns = {
        "/gmm",
        "/gmm/*" })
public class GmmServices extends NshmpServlet {
  private static final long serialVersionUID = 1L;

  private static final Gson GSON;

  private static final String GMM_KEY = "gmm";
  private static final String RMIN_KEY = "rMin";
  private static final String RMAX_KEY = "rMax";
  private static final String IMT_KEY = "imt";
  private static final int ROUND = 5;

  static {
    GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .registerTypeAdapter(Double.class, new Util.NaNSerializer())
        .registerTypeAdapter(Parameters.class, new Parameters.Serializer())
        .registerTypeAdapter(Imt.class, new Util.EnumSerializer<Imt>())
        .registerTypeAdapter(Constraints.class, new Util.ConstraintsSerializer())
        .create();
  }

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    UrlHelper urlHelper = urlHelper(request, response);
    Service service = getService(request);

    try {
      /* At a minimum, Gmms must be defined. */
      if (!hasGMM(request, service, urlHelper)) return;

      Map<String, String[]> params = request.getParameterMap();

      ResponseData svcResponse = processRequest(service, params, urlHelper);

      response.getWriter().print(GSON.toJson(svcResponse));
    } catch (Exception e) {
      String message = errorMessage(urlHelper.url, e, false);
      response.getWriter().print(message);
      e.printStackTrace();
    }
  }

  @Override
  protected void doPost(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    BufferedReader requestReader = request.getReader();
    UrlHelper urlHelper = urlHelper(request, response);
    Service service = getService(request);

    try {
      /* At a minimum, Gmms must be defined. */
      if (!hasGMM(request, service, urlHelper)) return;

      String[] gmmParams = request.getParameterValues(GMM_KEY);

      List<String> requestData = requestReader.lines().collect(Collectors.toList());

      if (requestData.isEmpty()) {
        throw new IllegalStateException("Post data is empty");
      }

      List<String> keys = Parsing.splitToList(requestData.get(0), Delimiter.COMMA);

      ResponseDataPost svcResponse = new ResponseDataPost(service, urlHelper);

      List<ResponseData> gmmResponses = requestData.subList(1, requestData.size())
          .parallelStream()
          .filter((line) -> !line.startsWith("#") && !line.trim().isEmpty())
          .map((line) -> {
            List<String> values = Parsing.splitToList(line, Delimiter.COMMA);

            Map<String, String[]> params = new HashMap<>();
            params.put(GMM_KEY, gmmParams);

            int index = 0;

            for (String key : keys) {
              String value = values.get(index);
              if ("null".equals(value.toLowerCase())) continue;

              params.put(key, new String[] { value });
              index++;
            }

            return processRequest(service, params, urlHelper);
          })
          .collect(Collectors.toList());

      svcResponse.setResponse(gmmResponses);
      response.getWriter().print(GSON.toJson(svcResponse));
    } catch (Exception e) {
      String message = errorMessage(urlHelper.url, e, false);
      response.getWriter().print(message);
      e.printStackTrace();
    }
  }

  static class RequestData {
    Set<Gmm> gmms;
    GmmInput input;

    RequestData(Map<String, String[]> params) {
      this.gmms = buildGmmSet(params);
      this.input = buildInput(params);
    }
  }

  static class RequestDataDistance extends RequestData {
    String imt;
    double minDistance;
    double maxDistance;

    RequestDataDistance(
        Map<String, String[]> params,
        String imt,
        double rMin,
        double rMax) {

      super(params);

      this.imt = imt;
      minDistance = rMin;
      maxDistance = rMax;
    }
  }

  static class ResponseDataPost {
    String name;
    String status = Status.SUCCESS.toString();
    String date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
    String url;
    Object server;
    List<ResponseData> response;

    ResponseDataPost(Service service, UrlHelper urlHelper) {
      name = service.resultName;

      server = gov.usgs.earthquake.nshmp.www.meta.Metadata.serverData(1, ServletUtil.timer());

      url = urlHelper.url;
    }

    void setResponse(List<ResponseData> response) {
      this.response = response;
    }
  }

  static class ResponseData {
    String name;
    String status = Status.SUCCESS.toString();
    String date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
    String url;
    Object server;
    RequestData request;
    GmmXYDataGroup means;
    GmmXYDataGroup sigmas;

    ResponseData(Service service, RequestData request) {
      name = service.resultName;

      server = gov.usgs.earthquake.nshmp.www.meta.Metadata.serverData(1, ServletUtil.timer());

      this.request = request;

      means = GmmXYDataGroup.create(
          service.groupNameMean,
          service.xLabel,
          service.yLabelMedian);

      sigmas = GmmXYDataGroup.create(
          service.groupNameSigma,
          service.xLabel,
          service.yLabelSigma);
    }

    void setXY(
        Map<Gmm, List<Double>> x,
        Map<Gmm, List<Double>> means,
        Map<Gmm, List<Double>> sigmas) {

      for (Gmm gmm : means.keySet()) {
        XySequence xyMeans = XySequence.create(
            x.get(gmm),
            Data.round(ROUND, Data.exp(new ArrayList<>(means.get(gmm)))));
        this.means.add(gmm.name(), gmm.toString(), xyMeans, gmm);

        XySequence xySigmas = XySequence.create(
            x.get(gmm),
            Data.round(ROUND, new ArrayList<>(sigmas.get(gmm))));
        this.sigmas.add(gmm.name(), gmm.toString(), xySigmas, gmm);
      }
    }

  }

  private static class GmmXYDataGroup extends XY_DataGroup {

    GmmXYDataGroup(String name, String xLabel, String yLabel) {
      super(name, xLabel, yLabel);
    }

    public static GmmXYDataGroup create(String name, String xLabel, String yLabel) {
      return new GmmXYDataGroup(name, xLabel, yLabel);
    }

    public GmmXYDataGroup add(String id, String name, XySequence data, Gmm gmm) {
      this.data.add(new GmmSeries(id, name, data, gmm));
      return this;
    }

    static class GmmSeries extends XY_DataGroup.Series {
      final Constraints constraints;
      final TreeSet<String> supportedImts;

      GmmSeries(String id, String label, XySequence data, Gmm gmm) {
        super(id, label, data);
        constraints = gmm.constraints();
        supportedImts = gmm.supportedIMTs().stream()
            .map(imt -> imt.name())
            .collect(Collectors.toCollection(TreeSet::new));
      }
    }
  }

  static ResponseData processRequest(
      Service service,
      Map<String, String[]> params,
      UrlHelper urlHelper) {
    ResponseData svcResponse = null;

    switch (service) {
      case DISTANCE:
      case HW_FW:
        svcResponse = processRequestDistance(service, params);
        break;
      case SPECTRA:
        svcResponse = processRequestSpectra(service, params);
        break;
      default:
        throw new IllegalStateException("Service not supported [" + service + "]");
    }

    svcResponse.url = urlHelper.url;
    return svcResponse;
  }

  static ResponseData processRequestDistance(
      Service service, Map<String, String[]> params) {

    boolean isLogSpace = service.equals(Service.DISTANCE) ? true : false;
    Imt imt = readValue(IMT, params, Imt.class);
    double rMin = Double.valueOf(params.get(RMIN_KEY)[0]);
    double rMax = Double.valueOf(params.get(RMAX_KEY)[0]);

    RequestDataDistance request = new RequestDataDistance(
        params, imt.toString(), rMin, rMax);

    DistanceResult result = GroundMotions.distanceGroundMotions(
        request.gmms, request.input, imt, rMin, rMax, isLogSpace);

    ResponseData response = new ResponseData(service, request);
    response.setXY(result.distance, result.means, result.sigmas);

    return response;
  }

  private static ResponseData processRequestSpectra(
      Service service, Map<String, String[]> params) {

    RequestData request = new RequestData(params);
    MultiResult result = spectra(request.gmms, request.input, false);

    ResponseData response = new ResponseData(service, request);
    response.setXY(result.periods, result.means, result.sigmas);

    return response;
  }

  static Set<Gmm> buildGmmSet(Map<String, String[]> params) {
    checkArgument(params.containsKey(GMM_KEY),
        "Missing ground motion model key: " + GMM_KEY);
    return Sets.newEnumSet(
        FluentIterable
            .from(params.get(GMM_KEY))
            .transform(Enums.stringConverter(Gmm.class)),
        Gmm.class);
  }

  static GmmInput buildInput(Map<String, String[]> params) {

    Builder builder = GmmInput.builder().withDefaults();
    for (Entry<String, String[]> entry : params.entrySet()) {
      if (entry.getKey().equals(GMM_KEY) || entry.getKey().equals(IMT_KEY) ||
          entry.getKey().equals(RMAX_KEY) || entry.getKey().equals(RMIN_KEY))
        continue;
      Field id = Field.fromString(entry.getKey());
      String value = entry.getValue()[0];
      if (value.equals("")) {
        continue;
      }
      builder.set(id, value);
    }
    return builder.build();
  }

  static final class Metadata {

    String status = Status.USAGE.toString();
    String description;
    String syntax;
    Parameters parameters;

    Metadata(Service service) {
      this.syntax = "%s://%s/nshmp-haz-ws/gmm" + service.pathInfo + "?";
      this.description = service.description;
      this.parameters = new Parameters(service);
    }
  }

  /*
   * Placeholder class; all parameter serialization is done via the custom
   * Serializer. Service reference needed serialize().
   */
  static final class Parameters {

    private final Service service;

    Parameters(Service service) {
      this.service = service;
    }

    static final class Serializer implements JsonSerializer<Parameters> {

      @Override
      public JsonElement serialize(
          Parameters meta,
          Type type,
          JsonSerializationContext context) {

        JsonObject root = new JsonObject();

        if (!meta.service.equals(Service.SPECTRA)) {
          Set<Imt> imtSet = EnumSet.complementOf(EnumSet.range(PGV, AI));
          final EnumParameter<Imt> imts;
          imts = new EnumParameter<>(
              "Intensity measure type",
              ParamType.STRING,
              imtSet);
          root.add(IMT_KEY, context.serialize(imts));
        }

        /* Serialize input fields. */
        Constraints defaults = Constraints.defaults();
        for (Field field : Field.values()) {
          Param param = createGmmInputParam(field, defaults.get(field));
          JsonElement fieldElem = context.serialize(param);
          root.add(field.id, fieldElem);
        }

        /* Add only add those Gmms that belong to a Group. */
        List<Gmm> gmms = Arrays.stream(Gmm.Group.values())
            .flatMap(group -> group.gmms().stream())
            .sorted(Comparator.comparing(Object::toString))
            .distinct()
            .collect(Collectors.toList());

        GmmParam gmmParam = new GmmParam(
            GMM_NAME,
            GMM_INFO,
            gmms);
        root.add(GMM_KEY, context.serialize(gmmParam));

        /* Add gmm groups. */
        GroupParam groups = new GroupParam(
            GROUP_NAME,
            GROUP_INFO,
            EnumSet.allOf(Gmm.Group.class));
        root.add(GROUP_KEY, context.serialize(groups));

        return root;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Param createGmmInputParam(
      Field field,
      Optional<?> constraint) {
    return (field == VSINF) ? new BooleanParam(field)
        : new NumberParam(field, (Range<Double>) constraint.get());
  }

  /*
   * Marker interface for spectra parameters. This was previously implemented as
   * an abstract class for label, info, and units, but Gson serialized subclass
   * fields before parent fields. To maintain a preferred order, one can write
   * custom serializers or repeat these four fields in each implementation.
   */
  private static interface Param {}

  @SuppressWarnings("unused")
  private static final class NumberParam implements Param {

    final String label;
    final String info;
    final String units;
    final Double min;
    final Double max;
    final Double value;

    NumberParam(GmmInput.Field field, Range<Double> constraint) {
      this(field, constraint, field.defaultValue);
    }

    NumberParam(GmmInput.Field field, Range<Double> constraint, Double value) {
      this.label = field.label;
      this.info = field.info;
      this.units = field.units.orElse(null);
      this.min = constraint.lowerEndpoint();
      this.max = constraint.upperEndpoint();
      this.value = Doubles.isFinite(value) ? value : null;
    }
  }

  @SuppressWarnings("unused")
  private static final class BooleanParam implements Param {

    final String label;
    final String info;
    final boolean value;

    BooleanParam(GmmInput.Field field) {
      this(field, field.defaultValue == 1.0);
    }

    BooleanParam(GmmInput.Field field, boolean value) {
      this.label = field.label;
      this.info = field.info;
      this.value = value;
    }
  }

  private static final String GMM_NAME = "Ground Motion Models";
  private static final String GMM_INFO = "Empirical models of ground motion";

  @SuppressWarnings("unused")
  private static class GmmParam implements Param {

    final String label;
    final String info;
    final List<Value> values;

    GmmParam(String label, String info, List<Gmm> gmms) {
      this.label = label;
      this.info = info;
      this.values = gmms.stream()
          .map(gmm -> new Value(gmm))
          .collect(Collectors.toList());
    }

    private static class Value {

      final String id;
      final String label;
      final ArrayList<String> supportedImts;
      final Constraints constraints;

      Value(Gmm gmm) {
        this.id = gmm.name();
        this.label = gmm.toString();
        this.supportedImts = SupportedImts(gmm.supportedIMTs());
        this.constraints = gmm.constraints();
      }
    }

    private static ArrayList<String> SupportedImts(Set<Imt> imts) {
      ArrayList<String> supportedImts = new ArrayList<>();

      for (Imt imt : imts) {
        supportedImts.add(imt.name());
      }

      return supportedImts;
    }

  }

  private static final String GROUP_KEY = "group";
  private static final String GROUP_NAME = "Ground Motion Model Groups";
  private static final String GROUP_INFO = "Groups of related ground motion models ";

  @SuppressWarnings("unused")
  private static final class GroupParam implements Param {

    final String label;
    final String info;
    final List<Value> values;

    GroupParam(String label, String info, Set<Gmm.Group> groups) {
      this.label = label;
      this.info = info;
      this.values = new ArrayList<>();
      for (Gmm.Group group : groups) {
        this.values.add(new Value(group));
      }
    }

    private static class Value {

      final String id;
      final String label;
      final List<Gmm> data;

      Value(Gmm.Group group) {
        this.id = group.name();
        this.label = group.toString();
        this.data = group.gmms();
      }
    }
  }

  private static enum Service {

    DISTANCE(
        "Ground Motion Vs. Distance",
        "Compute ground motion Vs. distance",
        "/distance",
        "Means",
        "Sigmas",
        "Distance (km)",
        "Median ground motion (g)",
        "Standard deviation"),

    HW_FW(
        "Hanging Wall Effect",
        "Compute hanging wall effect on ground motion Vs. distance",
        "/hw-fw",
        "Means",
        "Sigmas",
        "Distance (km)",
        "Median ground motion (g)",
        "Standard deviation"),

    SPECTRA(
        "Deterministic Response Spectra",
        "Compute deterministic response spectra",
        "/spectra",
        "Means",
        "Sigmas",
        "Period (s)",
        "Median ground motion (g)",
        "Standard deviation");

    final String name;
    final String description;
    final String pathInfo;
    final String resultName;
    final String groupNameMean;
    final String groupNameSigma;
    final String xLabel;
    final String yLabelMedian;
    final String yLabelSigma;

    private Service(
        String name, String description,
        String pathInfo, String groupNameMean,
        String groupNameSigma, String xLabel,
        String yLabelMedian, String yLabelSigma) {
      this.name = name;
      this.description = description;
      this.resultName = name + " Results";
      this.pathInfo = pathInfo;
      this.groupNameMean = groupNameMean;
      this.groupNameSigma = groupNameSigma;
      this.xLabel = xLabel;
      this.yLabelMedian = yLabelMedian;
      this.yLabelSigma = yLabelSigma;
    }

  }

  private static Service getService(HttpServletRequest request) {
    Service service = null;
    String pathInfo = request.getPathInfo();

    switch (pathInfo) {
      case PathInfo.DISTANCE:
        service = Service.DISTANCE;
        break;
      case PathInfo.HW_FW:
        service = Service.HW_FW;
        break;
      case PathInfo.SPECTRA:
        service = Service.SPECTRA;
        break;
      default:
        throw new IllegalStateException("Unsupported service [" + pathInfo + "]");
    }

    return service;
  }

  private static final class PathInfo {
    private static final String DISTANCE = "/distance";
    private static final String HW_FW = "/hw-fw";
    private static final String SPECTRA = "/spectra";
  }

  private static boolean hasGMM(
      HttpServletRequest request,
      Service service,
      UrlHelper urlHelper)
      throws IOException {

    String gmmParam = request.getParameter(GMM_KEY);
    if (gmmParam != null) return true;

    String usage = GSON.toJson(new Metadata(service));
    urlHelper.writeResponse(usage);
    return false;
  }

}
