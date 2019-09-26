package gov.usgs.earthquake.nshmp.www;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.calc.HazardExport.curvesBySource;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.MODEL_CACHE_CONTEXT_ID;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.emptyRequest;
import static gov.usgs.earthquake.nshmp.www.Util.readDouble;
import static gov.usgs.earthquake.nshmp.www.Util.readValue;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LATITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LONGITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.MODEL;
import static gov.usgs.earthquake.nshmp.www.Util.Key.VS30;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.CalcConfig.Builder;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.data.MutableXySequence;
import gov.usgs.earthquake.nshmp.data.Sequences;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.ServletUtil.TimedTask;
import gov.usgs.earthquake.nshmp.www.ServletUtil.Timer;
import gov.usgs.earthquake.nshmp.www.SourceServices.SourceModel;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * Probabilisitic seismic hazard calculation service.
 *
 * @author Peter Powers
 */
@SuppressWarnings("unused")
@WebServlet(
    name = "Hazard Service 2",
    description = "USGS NSHMP Hazard Curve Calculator",
    urlPatterns = {
        "/haz",
        "/haz/*" })
public final class HazardService2 extends NshmpServlet {

  /*
   * Developer notes:
   *
   * Updated hazard service that identifies models directly, instead of
   * editions, to simplify model comparison. Models are defined by a region and
   * year. This service computes hazard for all supported IMTs and a single
   * vs30.
   * 
   * As with the existing hazard service, calculations are designed to leverage
   * all available processors by default, distributing work using the
   * ServletUtil.CALC_EXECUTOR. This can create problems in a servlet
   * environment, however, because Tomcat does not support a single threaded
   * request queue where requests are processed as they are received with the
   * next task starting only once the prior has finished. One can really only
   * limit the maximum number of simultaneous requests. When multiple requests
   * are received in a short span, Tomcat will attempt to run hazard or deagg
   * calculations simultaneously. The net effect is that there can be out of
   * memory problems as too many results are retained, and multiple requests do
   * not return until all are finished.
   *
   * To address this, requests are submitted as tasks to the single-threaded
   * ServletUtil.TASK_EXECUTOR and are processed one-at-a-time in the order
   * received.
   * 
   * TODO Add support for multi model requests in order to combine models per
   * the original hazard service.
   */

  private LoadingCache<Model, HazardModel> modelCache;

  private static final String USAGE = SourceServices.GSON.toJson(
      new SourceServices.ResponseData());

  @Override
  @SuppressWarnings("unchecked")
  public void init() {
    ServletContext context = getServletConfig().getServletContext();
    Object modelCache = context.getAttribute(MODEL_CACHE_CONTEXT_ID);
    this.modelCache = (LoadingCache<Model, HazardModel>) modelCache;
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
      Hazard2Task task = new Hazard2Task(urlHelper.url, getServletContext(), requestData);
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

      Model model;
      double lon;
      double lat;
      Vs30 vs30;

      if (request.getQueryString() != null) {
        /* process query '?' request */
        model = readValue(MODEL, request, Model.class);
        lon = readDouble(LONGITUDE, request);
        lat = readDouble(LATITUDE, request);
        vs30 = Vs30.fromValue(readDouble(VS30, request));

      } else {
        /* process slash-delimited request */
        List<String> params = Parsing.splitToList(
            request.getPathInfo(),
            Delimiter.SLASH);
        model = Model.valueOf(params.get(0));
        lon = Double.valueOf(params.get(1));
        lat = Double.valueOf(params.get(2));
        vs30 = Vs30.fromValue(Double.valueOf(params.get(3)));
      }

      return new RequestData(
          model,
          lon,
          lat,
          vs30);

    } catch (Exception e) {
      throw new IllegalArgumentException("Error parsing request URL", e);
    }
  }

  private class Hazard2Task extends TimedTask<Result> {

    final RequestData data;

    Hazard2Task(String url, ServletContext context, RequestData data) {
      super(url, context);
      this.data = data;
    }

    @Override
    Result calc() throws Exception {
      Hazard hazard = calcHazard(data, context);
      return new Result.Builder()
          .requestData(data)
          .url(url)
          .timer(timer)
          .hazard(hazard)
          .build();
    }
  }

  Hazard calcHazard(RequestData data, ServletContext context) {
    Location loc = Location.create(data.latitude, data.longitude);
    HazardModel model = modelCache.getUnchecked(data.model);
    Builder configBuilder = CalcConfig.Builder.copyOf(model.config());
    configBuilder.imts(data.model.imts);
    CalcConfig config = configBuilder.build();

    Site site = Site.builder()
        .basinDataProvider(config.siteData.basinDataProvider)
        .location(loc)
        .vs30(data.vs30.value())
        .build();

    return HazardCalcs.hazard(model, config, site, ServletUtil.CALC_EXECUTOR);
  }

  static final class RequestData {

    final Model model;
    final double latitude;
    final double longitude;
    final Vs30 vs30;

    RequestData(
        Model model,
        double longitude,
        double latitude,
        Vs30 vs30) {

      this.model = model;
      this.latitude = latitude;
      this.longitude = longitude;
      this.vs30 = vs30;
    }
  }

  private static final class ResponseData {

    final SourceModel model;
    final double latitude;
    final double longitude;
    final Imt imt;
    final Vs30 vs30;
    final String xlabel = "Ground Motion (g)";
    final String ylabel = "Annual Frequency of Exceedence";
    final List<Double> xvalues;

    ResponseData(RequestData request, Imt imt, List<Double> xvalues) {
      this.model = new SourceModel(request.model);
      this.latitude = request.latitude;
      this.longitude = request.longitude;
      this.imt = imt;
      this.vs30 = request.vs30;
      this.xvalues = xvalues;
    }
  }

  private static final class Response {

    final ResponseData metadata;
    final List<Curve> data;

    Response(ResponseData metadata, List<Curve> data) {
      this.metadata = metadata;
      this.data = data;
    }
  }

  private static final class Curve {

    final String component;
    final List<Double> yvalues;

    Curve(String component, List<Double> yvalues) {
      this.component = component;
      this.yvalues = yvalues;
    }
  }

  private static final String TOTAL_KEY = "Total";

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

      Map<Imt, Map<SourceType, MutableXySequence>> componentMaps;
      Map<Imt, MutableXySequence> totalMap;
      Map<Imt, List<Double>> xValuesLinearMap;

      Builder hazard(Hazard hazardResult) {
        checkState(totalMap == null, "Hazard has already been added to this builder");

        componentMaps = new EnumMap<>(Imt.class);
        totalMap = new EnumMap<>(Imt.class);
        xValuesLinearMap = new EnumMap<>(Imt.class);

        Map<Imt, Map<SourceType, ? extends XySequence>> typeTotalMaps = curvesBySource(hazardResult);

        for (Imt imt : hazardResult.curves().keySet()) {

          // total curve
          Sequences.addToMap(imt, totalMap, hazardResult.curves().get(imt));

          // component curves
          Map<SourceType, ? extends XySequence> typeTotalMap = typeTotalMaps.get(imt);
          Map<SourceType, MutableXySequence> componentMap = componentMaps.get(imt);
          if (componentMap == null) {
            componentMap = new EnumMap<>(SourceType.class);
            componentMaps.put(imt, componentMap);
          }

          for (SourceType type : typeTotalMap.keySet()) {
            Sequences.addToMap(type, componentMap, typeTotalMap.get(type));
          }

          xValuesLinearMap.put(
              imt,
              hazardResult.config()
                  .hazard
                  .modelCurve(imt)
                  .xValues()
                  .boxed()
                  .collect(Collectors.toList()));
        }
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

        for (Imt imt : totalMap.keySet()) {

          ResponseData responseData = new ResponseData(
              request,
              imt,
              xValuesLinearMap.get(imt));

          ImmutableList.Builder<Curve> curveListBuilder = ImmutableList.builder();

          // total curve
          Curve totalCurve = new Curve(
              TOTAL_KEY,
              totalMap.get(imt).yValues().boxed().collect(Collectors.toList()));
          curveListBuilder.add(totalCurve);

          // component curves
          Map<SourceType, MutableXySequence> typeMap = componentMaps.get(imt);
          for (SourceType type : typeMap.keySet()) {
            Curve curve = new Curve(
                type.toString(),
                typeMap.get(type).yValues().boxed().collect(Collectors.toList()));
            curveListBuilder.add(curve);
          }

          Response response = new Response(responseData, curveListBuilder.build());
          responseListBuilder.add(response);
        }

        List<Response> responseList = responseListBuilder.build();
        Object server = Metadata.serverData(ServletUtil.THREAD_COUNT, timer);

        return new Result(url, server, responseList);
      }
    }
  }
}
