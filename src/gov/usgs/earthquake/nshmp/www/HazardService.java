package gov.usgs.earthquake.nshmp.www;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.calc.HazardExport.curvesBySource;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.MODEL_CACHE_CONTEXT_ID;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.emptyRequest;
import static gov.usgs.earthquake.nshmp.www.Util.readDouble;
import static gov.usgs.earthquake.nshmp.www.Util.readValue;
import static gov.usgs.earthquake.nshmp.www.Util.readValues;
import static gov.usgs.earthquake.nshmp.www.Util.Key.EDITION;
import static gov.usgs.earthquake.nshmp.www.Util.Key.IMT;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LATITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LONGITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.REGION;
import static gov.usgs.earthquake.nshmp.www.Util.Key.RETURNPERIOD;
import static gov.usgs.earthquake.nshmp.www.Util.Key.VS30;
import static gov.usgs.earthquake.nshmp.www.meta.Region.CEUS;
import static gov.usgs.earthquake.nshmp.www.meta.Region.COUS;
import static gov.usgs.earthquake.nshmp.www.meta.Region.WUS;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.CalcConfig.Builder;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.ServletUtil.TimedTask;
import gov.usgs.earthquake.nshmp.www.ServletUtil.Timer;
import gov.usgs.earthquake.nshmp.www.meta.Edition;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Region;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * Probabilisitic seismic hazard calculation service.
 *
 * @author Peter Powers
 */
@SuppressWarnings("unused")
@WebServlet(
    name = "Hazard Service",
    description = "USGS NSHMP Hazard Curve Calculator",
    urlPatterns = {
        "/hazard",
        "/hazard/*" })
public final class HazardService extends NshmpServlet {

  /*
   * Developer notes:
   *
   * The HazardService and DeaggService are very similar. Deagg delegates to a
   * package method HazardService.hazardCalc() to obtain a Hazard object, which
   * it then deaggregates. This method may combine Hazard objects from CEUS and
   * WUS models, otherwise it runs a single model. HazardService.RequestData
   * objects are common to both services, with the understanding that Optional
   * fields (1) 'imts' will always contain a Set<Imt> with a single entry for
   * deagg, and that (2) 'returnPeriod' will be absent for hazard.
   *
   * Nshmp-haz calculations are designed to leverage all available processors by
   * default distributing work using the ServletUtil.CALC_EXECUTOR. This can
   * create problems in a servlet environment, however, because Tomcat does not
   * support a single threaded request queue where requests are processed as
   * they are received with the next task starting only once the prior has
   * finished. One can really only limit the maximum number of simultaneous
   * requests. When multiple requests are received in a short span, Tomcat will
   * attempt to run hazard or deagg calculations simultaneously. The net effect
   * is that there can be out of memory problems as too many results are
   * retained, and multiple requests do not return until all are finished.
   *
   * To address this, requests of HazardService and DeaggService are submitted
   * as tasks to the single-threaded ServletUtil.TASK_EXECUTOR and are processed
   * one-at-a-time in the order received.
   */

  /*
   * IMTs: PGA, SA0P20, SA1P00 TODO this need to be updated to the result of
   * polling all models and supports needs to be updated to specific models
   *
   * Regions: COUS, WUS, CEUS, [HI, AK, GM, AS, SAM, ...]
   *
   * vs30: 180, 259, 360, 537, 760, 1150, 2000
   *
   * 2014 updated values
   *
   * vs30: 185, 260, 365, 530, 760, 1080, 2000
   *
   */

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    UrlHelper urlHelper = urlHelper(request, response);
    String query = request.getQueryString();
    String pathInfo = request.getPathInfo();

    if (emptyRequest(request)) {
      urlHelper.writeResponse(Metadata.HAZARD_USAGE);
      return;
    }

    if (ServletUtil.uhtBusy) {
      ServletUtil.missCount++;
      String message = Metadata.busyMessage(
          urlHelper.url,
          ServletUtil.hitCount,
          ServletUtil.missCount);
      //response.setStatus(503);
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
      response.getWriter().print(message);
      return;
    }

    RequestData requestData;
    ServletUtil.uhtBusy = true;
    try {
      if (query != null) {
        /* process query '?' request */
        requestData = buildRequest(request);
      } else {
        /* process slash-delimited request */
        List<String> params = Parsing.splitToList(pathInfo, Delimiter.SLASH);
        if (params.size() < 6) {
          urlHelper.writeResponse(Metadata.HAZARD_USAGE);
          return;
        }
        requestData = buildRequest(params);
      }

      /* Submit as task to job executor */
      HazardTask task = new HazardTask(urlHelper.url, getServletContext(), requestData);
      Result result = ServletUtil.TASK_EXECUTOR.submit(task).get();
      // GSON.toJson(result, response.getWriter()); TODO test and use elsewhere?
      String resultStr = GSON.toJson(result);
      response.getWriter().print(resultStr);

    } catch (Exception e) {
      String message = Metadata.errorMessage(urlHelper.url, e, false);
      response.getWriter().print(message);
      getServletContext().log(urlHelper.url, e);
    }
    ServletUtil.hitCount++;
    ServletUtil.uhtBusy = false;
  }

  /*
   * Reduce query string key-value pairs. This method is shared with deagg.
   * Deagg must supply a single Imt. See RequestData notes below.
   */
  static RequestData buildRequest(HttpServletRequest request) {

    Map<String, String[]> paramMap = request.getParameterMap();

    /* Read params as for hazard. */
    double lon = readDouble(LONGITUDE, request);
    double lat = readDouble(LATITUDE, request);
    Vs30 vs30 = Vs30.fromValue(readDouble(VS30, request));
    Edition edition = readValue(EDITION, request, Edition.class);
    Region region = ServletUtil.checkRegion(
        readValue(REGION, request, Region.class),
        lon);
    Set<Imt> supportedImts = Metadata.commonImts(edition, region);
    Set<Imt> imts = paramMap.containsKey(IMT.toString())
        ? readValues(IMT, request, Imt.class)
        : supportedImts;
    OptionalDouble returnPeriod = OptionalDouble.empty();

    /* Possibly update for deagg. */
    if (paramMap.containsKey(RETURNPERIOD.toString())) {
      returnPeriod = OptionalDouble.of(readDouble(RETURNPERIOD, request));
    }

    return new RequestData(
        edition,
        region,
        lon,
        lat,
        imts,
        vs30,
        returnPeriod);
  }

  /*
   * Reduce slash-delimited request. This method is shared with deagg. Deagg
   * must supply a single Imt. See RequestData notes below.
   */
  static RequestData buildRequest(List<String> params) {

    /* Read params as for hazard */
    double lon = Double.valueOf(params.get(2));
    double lat = Double.valueOf(params.get(3));
    Vs30 vs30 = Vs30.fromValue(Double.valueOf(params.get(5)));
    Edition edition = Enum.valueOf(Edition.class, params.get(0));
    Region region = ServletUtil.checkRegion(
        Enum.valueOf(Region.class, params.get(1)),
        lon);
    Set<Imt> supportedImts = Metadata.commonImts(edition, region);
    Set<Imt> imts = (params.get(4).equalsIgnoreCase("any"))
        ? supportedImts
        : readValues(params.get(4), Imt.class);
    OptionalDouble returnPeriod = OptionalDouble.empty();

    /* Possibly update for deagg. */
    if (params.size() == 7) {
      returnPeriod = OptionalDouble.of(Double.valueOf(params.get(6)));
    }

    return new RequestData(
        edition,
        region,
        lon,
        lat,
        imts,
        vs30,
        returnPeriod);
  }

  private static class HazardTask extends TimedTask<Result> {

    final RequestData data;

    HazardTask(String url, ServletContext context, RequestData data) {
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

  /* Also used by DeaggService */
  static Hazard calcHazard(RequestData data, ServletContext context) {

    Location loc = Location.create(data.latitude, data.longitude);
    Site.Builder siteBuilder = Site.builder().location(loc).vs30(data.vs30.value());

    @SuppressWarnings("unchecked")
    LoadingCache<Model, HazardModel> modelCache =
        (LoadingCache<Model, HazardModel>) context.getAttribute(MODEL_CACHE_CONTEXT_ID);

    // TODO cache calls should be using checked get(id)

    // May include trailing 'B' for 2014B
    String baseYear = data.edition.name().substring(1);

    /*
     * When combining (merging) Hazard, the config from the first supplied
     * Hazard is used for the result. This means, for example, the exceedance
     * model used for deaggregation may be different than that used to compute
     * the original hazard curves. Because the CEUS exceedance model,
     * NSHM_CEUS_MAX_INTENSITY, is really just a 3σ truncation model except
     * close to New Madrid when fixed maximum values apply, it is okay to just
     * use the WUS 3σ truncation exceedance model in the CEUS-WUS overlap zone.
     * However, it is important to have the WUS result be first in the merge()
     * call below.
     */
    if (data.region == COUS) {

      Model wusId = Model.valueOf(WUS.name() + "_" + baseYear);
      HazardModel wusModel = modelCache.getUnchecked(wusId);
      Site site = siteBuilder
          .basinDataProvider(wusModel.config().siteData.basinDataProvider)
          .build();
      Hazard wusResult = process(wusModel, site, data.imts);

      String ceusYear = baseYear.equals("2014B") ? "2014" : baseYear;
      Model ceusId = Model.valueOf(CEUS.name() + "_" + ceusYear);
      HazardModel ceusModel = modelCache.getUnchecked(ceusId);
      Hazard ceusResult = process(ceusModel, site, data.imts);

      return Hazard.merge(wusResult, ceusResult);
    }

    String year = (baseYear.equals("2014B") && data.region == Region.CEUS)
        ? "2014" : baseYear;
    Model modelId = Model.valueOf(data.region.name() + "_" + year);
    HazardModel model = modelCache.getUnchecked(modelId);
    Site site = siteBuilder.basinDataProvider(model.config().siteData.basinDataProvider).build();
    return process(model, site, data.imts);
  }

  private static Hazard process(HazardModel model, Site site, Set<Imt> imts) {
    Builder configBuilder = CalcConfig.Builder.copyOf(model.config());
    configBuilder.imts(imts);
    CalcConfig config = configBuilder.build();
    return HazardCalcs.hazard(model, config, site, ServletUtil.CALC_EXECUTOR);
  }

  /*
   * We use a single request object type for both hazard and deagg. With the
   * extension of deagg to support CMS, we need medians and sigmas for other
   * spectral periods, but we don't (at least not yet) want to return serialized
   * deaggs across all periods. Whereas both underlying programs support
   * multiple Imts, the hazard service expects a set of Imts (that may
   * correspond to 'any' implying all supportedImts), but the deagg service
   * expects a single Imt. To address this, we've added the deaggImt field,
   * which will be derived from the singleton imt URL argument as before, but
   * hazard will always be computed across the set of Imts supplied so that the
   * cms at the deaggImt can be computed. Under the hood the deagg application
   * provides support for all specified Imts. Also note that the presence of a
   * 'return period' is used to flag deagg service requests.
   */
  static final class RequestData {

    final Edition edition;
    final Region region;
    final double latitude;
    final double longitude;
    final Set<Imt> imts;
    final Vs30 vs30;
    final OptionalDouble returnPeriod;

    RequestData(
        Edition edition,
        Region region,
        double longitude,
        double latitude,
        Set<Imt> imts,
        Vs30 vs30,
        OptionalDouble returnPeriod) {

      this.edition = edition;
      this.region = region;
      this.latitude = latitude;
      this.longitude = longitude;
      this.imts = imts;
      this.vs30 = vs30;
      this.returnPeriod = returnPeriod;
    }
  }

  private static final class ResponseData {

    final Edition edition;
    final Region region;
    final double latitude;
    final double longitude;
    final Imt imt;
    final Vs30 vs30;
    final String xlabel = "Ground Motion (g)";
    final String ylabel = "Annual Frequency of Exceedence";
    final List<Double> xvalues;

    ResponseData(RequestData request, Imt imt, List<Double> xvalues) {
      this.edition = request.edition;
      this.region = request.region;
      this.longitude = request.longitude;
      this.latitude = request.latitude;
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

      Map<Imt, Map<SourceType, XySequence>> componentMaps;
      Map<Imt, XySequence> totalMap;
      Map<Imt, List<Double>> xValuesLinearMap;

      Builder hazard(Hazard hazardResult) {
        checkState(totalMap == null, "Hazard has already been added to this builder");

        componentMaps = new EnumMap<>(Imt.class);
        totalMap = new EnumMap<>(Imt.class);
        xValuesLinearMap = new EnumMap<>(Imt.class);

        Map<Imt, Map<SourceType, XySequence>> typeTotalMaps = curvesBySource(hazardResult);

        for (Imt imt : hazardResult.curves().keySet()) {

          // total curve
          hazardResult.curves().get(imt).addToMap(imt, totalMap);

          // component curves
          Map<SourceType, XySequence> typeTotalMap = typeTotalMaps.get(imt);
          Map<SourceType, XySequence> componentMap = componentMaps.get(imt);
          if (componentMap == null) {
            componentMap = new EnumMap<>(SourceType.class);
            componentMaps.put(imt, componentMap);
          }

          for (SourceType type : typeTotalMap.keySet()) {
            typeTotalMap.get(type).addToMap(type, componentMap);
          }

          xValuesLinearMap.put(
              imt,
              hazardResult.config().hazard.modelCurve(imt).xValues());
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
              totalMap.get(imt).yValues());
          curveListBuilder.add(totalCurve);

          // component curves
          Map<SourceType, XySequence> typeMap = componentMaps.get(imt);
          for (SourceType type : typeMap.keySet()) {
            Curve curve = new Curve(
                type.toString(),
                typeMap.get(type).yValues());
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
