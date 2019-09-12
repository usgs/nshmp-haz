package gov.usgs.earthquake.nshmp.www;

import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.MODEL_CACHE_CONTEXT_ID;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.emptyRequest;
import static gov.usgs.earthquake.nshmp.www.Util.readBoolean;
import static gov.usgs.earthquake.nshmp.www.Util.readDouble;
import static gov.usgs.earthquake.nshmp.www.Util.Key.BASIN;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LATITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LONGITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.MODEL;
import static gov.usgs.earthquake.nshmp.www.Util.Key.VS30;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.Deaggregation;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.www.ServletUtil.TimedTask;
import gov.usgs.earthquake.nshmp.www.ServletUtil.Timer;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * Hazard deaggregation service.
 *
 * @author Peter Powers
 */
@SuppressWarnings("unused")
@WebServlet(
    name = "Epsilon Deaggregation Service (experimental)",
    description = "USGS NSHMP Hazard Deaggregator",
    urlPatterns = { "/deagg-epsilon" })
public final class DeaggEpsilonService extends NshmpServlet {

  /* Developer notes: See HazardService. */

  private LoadingCache<Model, HazardModel> modelCache;
  private URL basinUrl;

  private static final String USAGE = SourceServices.GSON.toJson(
      new SourceServices.ResponseData());

  @Override
  @SuppressWarnings("unchecked")
  public void init() throws ServletException {

    ServletContext context = getServletConfig().getServletContext();
    Object modelCache = context.getAttribute(MODEL_CACHE_CONTEXT_ID);
    this.modelCache = (LoadingCache<Model, HazardModel>) modelCache;

    try (InputStream config =
        DeaggService2.class.getResourceAsStream("/config.properties")) {

      checkNotNull(config, "Missing config.properties");

      Properties props = new Properties();
      props.load(config);
      if (props.containsKey("basin_host")) {
        /*
         * TODO Site builder tests if service is working, which may be
         * inefficient for single call services.
         */
        URL url = new URL(props.getProperty("basin_host") + "/nshmp-site-ws/basin/local-data");
        this.basinUrl = url;
      }
    } catch (IOException | NullPointerException e) {
      throw new ServletException(e);
    }
  }

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    UrlHelper urlHelper = urlHelper(request, response);

    if (emptyRequest(request)) {
      urlHelper.writeResponse(USAGE);
      return;
    }

    try {
      RequestData requestData = buildRequestData(request);

      /* Submit as task to job executor */
      Deagg2Task task = new Deagg2Task(urlHelper.url, getServletContext(), requestData);
      Result result = ServletUtil.TASK_EXECUTOR.submit(task).get();
      GSON.toJson(result, response.getWriter());

    } catch (Exception e) {
      String message = Metadata.errorMessage(urlHelper.url, e, false);
      response.getWriter().print(message);
      getServletContext().log(urlHelper.url, e);
    }
  }

  /* Reduce query string key-value pairs. */
  static RequestData buildRequestData(HttpServletRequest request) {

    try {

      /* process query '?' request */
      List<Model> models = readModelsFromQuery(request);
      double lon = readDouble(LONGITUDE, request);
      double lat = readDouble(LATITUDE, request);
      Map<Imt, Double> imtImls = readImtsFromQuery(request);
      double vs30 = readDouble(VS30, request);
      boolean basin = readBoolean(BASIN, request);

      return new RequestData(
          models,
          lon,
          lat,
          imtImls,
          vs30,
          basin);

    } catch (Exception e) {
      throw new IllegalArgumentException("Error parsing request URL", e);
    }
  }

  private static List<Model> readModelsFromQuery(HttpServletRequest request) {
    String[] ids = Util.readValues(MODEL, request);
    return Arrays.stream(ids)
        .map(Model::valueOf)
        .distinct()
        .collect(ImmutableList.toImmutableList());
  }

  /* Create map of IMT to deagg IML. */
  private static Map<Imt, Double> readImtsFromQuery(HttpServletRequest request) {
    EnumMap<Imt, Double> imtImls = new EnumMap<>(Imt.class);
    for (Entry<String, String[]> param : request.getParameterMap().entrySet()) {
      if (isImtParam(param.getKey())) {
        imtImls.put(
            Imt.valueOf(param.getKey()),
            Double.valueOf(param.getValue()[0]));
      }
    }
    return imtImls;
  }

  private static boolean isImtParam(String key) {
    return key.equals("PGA") || key.startsWith("SA");
  }

  private class Deagg2Task extends TimedTask<Result> {

    RequestData data;

    Deagg2Task(String url, ServletContext context, RequestData data) {
      super(url, context);
      this.data = data;
    }

    @Override
    Result calc() throws Exception {
      Deaggregation deagg = calcDeagg(data);

      return new Result.Builder()
          .requestData(data)
          .url(url)
          .timer(timer)
          .deagg(deagg)
          .build();
    }
  }

  /*
   * Developer notes:
   * 
   * We're opting here to fetch basin terms ourselves. If we were to set the
   * basin provider in the config, which requires additions to config, the URL
   * is tested every time a site is created for a servlet request. While this
   * worked for maps it's not good here.
   * 
   * Site has logic for parsing the basin service response, which perhaps it
   * shouldn't. TODO is it worth decomposing data objects and services
   */
  Deaggregation calcDeagg(RequestData data) {
    Location loc = Location.create(data.latitude, data.longitude);

    Site site = Site.builder()
        .location(Location.create(data.latitude, data.longitude))
        .basinDataProvider(data.basin ? this.basinUrl : null)
        .vs30(data.vs30)
        .build();

    Hazard[] hazards = new Hazard[data.models.size()];
    for (int i = 0; i < data.models.size(); i++) {
      HazardModel model = modelCache.getUnchecked(data.models.get(i));
      hazards[i] = process(model, site, data.imtImls.keySet());
    }
    Hazard hazard = Hazard.merge(hazards);
    return Deaggregation.atImls(hazard, data.imtImls, ServletUtil.CALC_EXECUTOR);
  }

  private static Hazard process(HazardModel model, Site site, Set<Imt> imts) {
    CalcConfig config = CalcConfig.Builder
        .copyOf(model.config())
        .imts(imts)
        .build();
    // System.out.println(config);
    return HazardCalcs.hazard(model, config, site, ServletUtil.CALC_EXECUTOR);
  }

  static final class RequestData {

    final List<Model> models;
    final double latitude;
    final double longitude;
    final Map<Imt, Double> imtImls;
    final double vs30;
    final boolean basin;

    RequestData(
        List<Model> models,
        double longitude,
        double latitude,
        Map<Imt, Double> imtImls,
        double vs30,
        boolean basin) {

      this.models = models;
      this.latitude = latitude;
      this.longitude = longitude;
      this.imtImls = imtImls;
      this.vs30 = vs30;
      this.basin = basin;
    }
  }

  private static final class ResponseData {

    final List<Model> models;
    final double longitude;
    final double latitude;
    final String imt;
    final double iml;
    final double vs30;
    final String rlabel = "Closest Distance, rRup (km)";
    final String mlabel = "Magnitude (Mw)";
    final String εlabel = "% Contribution to Hazard";
    final Object εbins;

    ResponseData(Deaggregation deagg, RequestData request, Imt imt) {
      this.models = request.models;
      this.longitude = request.longitude;
      this.latitude = request.latitude;
      this.imt = imt.toString();
      this.iml = request.imtImls.get(imt);
      this.vs30 = request.vs30;
      this.εbins = deagg.εBins();
    }
  }

  private static final class Response {

    final ResponseData metadata;
    final Object data;

    Response(ResponseData metadata, Object data) {
      this.metadata = metadata;
      this.data = data;
    }
  }

  private static final class Result {

    final String status = Status.SUCCESS.toString();
    final String date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
    final String url;
    final Object server;
    final List<Response> response;

    Result(String url, Object server, List<Response> response) {
      this.url = url;
      this.server = server;
      this.response = response;
    }

    static final class Builder {

      String url;
      Timer timer;
      RequestData request;
      Deaggregation deagg;

      Builder deagg(Deaggregation deagg) {
        this.deagg = deagg;
        return this;
      }

      Builder url(String url) {
        this.url = url;
        return this;
      }

      Builder timer(Timer timer) {
        this.timer = timer;
        return this;
      }

      Builder requestData(RequestData request) {
        this.request = request;
        return this;
      }

      Result build() {
        ImmutableList.Builder<Response> responseListBuilder = ImmutableList.builder();

        for (Imt imt : request.imtImls.keySet()) {
          ResponseData responseData = new ResponseData(deagg, request, imt);
          Object deaggs = deagg.toJsonCompact(imt);
          Response response = new Response(responseData, deaggs);
          responseListBuilder.add(response);
        }

        List<Response> responseList = responseListBuilder.build();
        Object server = Metadata.serverData(ServletUtil.THREAD_COUNT, timer);

        return new Result(url, server, responseList);
      }
    }
  }

}
