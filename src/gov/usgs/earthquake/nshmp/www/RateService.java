package gov.usgs.earthquake.nshmp.www;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static gov.usgs.earthquake.nshmp.calc.ValueFormat.ANNUAL_RATE;
import static gov.usgs.earthquake.nshmp.calc.ValueFormat.POISSON_PROBABILITY;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.MODEL_CACHE_CONTEXT_ID;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.emptyRequest;
import static gov.usgs.earthquake.nshmp.www.Util.readDouble;
import static gov.usgs.earthquake.nshmp.www.Util.readValue;
import static gov.usgs.earthquake.nshmp.www.Util.Key.DISTANCE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.EDITION;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LATITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.LONGITUDE;
import static gov.usgs.earthquake.nshmp.www.Util.Key.REGION;
import static gov.usgs.earthquake.nshmp.www.Util.Key.TIMESPAN;
import static gov.usgs.earthquake.nshmp.www.meta.Region.CEUS;
import static gov.usgs.earthquake.nshmp.www.meta.Region.WUS;

import java.util.Optional;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.CalcConfig.Builder;
import gov.usgs.earthquake.nshmp.calc.EqRate;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.ValueFormat;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.NshmpServlet.UrlHelper;
import gov.usgs.earthquake.nshmp.www.ServletUtil.Timer;
import gov.usgs.earthquake.nshmp.www.meta.Edition;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Region;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * Earthquake probability and rate calculation service.
 *
 * @author Peter Powers
 */
@SuppressWarnings("unused")
@WebServlet(
    name = "Earthquake Probability & Rate Service",
    description = "USGS NSHMP Earthquake Probability & Rate Calculator",
    urlPatterns = {
        "/rate",
        "/rate/*",
        "/probability",
        "/probability/*" })
public final class RateService extends NshmpServlet {

  /*
   * Developer notes:
   *
   * The RateService is currently single-threaded and does not submit jobs to a
   * request queue; see HazardService. However, jobs are placed on a thread in
   * the CALC_EXECUTOR thread pool to handle parallel calculation of CEUS and
   * WUS models.
   */

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    Timer timer = ServletUtil.timer();

    UrlHelper urlHelper = urlHelper(request, response);
    String query = request.getQueryString();
    String pathInfo = request.getPathInfo();
    String service = request.getServletPath();

    ValueFormat format = service.equals("/rate") ? ANNUAL_RATE : POISSON_PROBABILITY;
    String usage = (format == ANNUAL_RATE) ? Metadata.RATE_USAGE : Metadata.PROBABILITY_USAGE;
    int paramCount = (format == ANNUAL_RATE) ? 5 : 6;

    if (emptyRequest(request)) {
      urlHelper.writeResponse(usage);
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
        requestData = buildRequest(request, format);
      } else {
        /* process slash-delimited request */
        List<String> params = Parsing.splitToList(pathInfo, Delimiter.SLASH);
        if (params.size() < paramCount) {
          urlHelper.writeResponse(usage);
          return;
        }
        requestData = buildRequest(params, format);
      }

      EqRate rates = calc(requestData, getServletContext());
      Result result = new Result.Builder()
          .requestData(requestData)
          .url(urlHelper.url)
          .timer(timer)
          .rates(rates)
          .build();
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

  /* Reduce query string key-value pairs */
  private RequestData buildRequest(HttpServletRequest request, ValueFormat format) {

    Optional<Double> timespan = (format == POISSON_PROBABILITY)
        ? Optional.of(readDouble(TIMESPAN, request)) : Optional.<Double> empty();

    return new RequestData(
        readValue(EDITION, request, Edition.class),
        readValue(REGION, request, Region.class),
        readDouble(LONGITUDE, request),
        readDouble(LATITUDE, request),
        readDouble(DISTANCE, request),
        timespan);
  }

  /* Reduce slash-delimited request */
  private RequestData buildRequest(List<String> params, ValueFormat format) {

    Optional<Double> timespan = (format == POISSON_PROBABILITY)
        ? Optional.of(Double.valueOf(params.get(5))) : Optional.<Double> empty();

    return new RequestData(
        Enum.valueOf(Edition.class, params.get(0)),
        Enum.valueOf(Region.class, params.get(1)),
        Double.valueOf(params.get(2)),
        Double.valueOf(params.get(3)),
        Double.valueOf(params.get(4)),
        timespan);
  }

  /*
   * TODO delete if not needed
   * 
   * Currently unused, however, will be used if it makes sense to submit jobs to
   * TASK_EXECUTOR.
   */
  private static class RateTask implements Callable<Result> {

    final String url;
    final RequestData data;
    final ServletContext context;
    final Timer timer;

    RateTask(String url, RequestData data, ServletContext context) {
      this.url = url;
      this.data = data;
      this.context = context;
      this.timer = ServletUtil.timer();
    }

    @Override
    public Result call() throws Exception {
      EqRate rates = calc(data, context);
      return new Result.Builder()
          .requestData(data)
          .url(url)
          .timer(timer)
          .rates(rates)
          .build();
    }
  }

  private static EqRate calc(RequestData data, ServletContext context)
      throws InterruptedException, ExecutionException {

    Location location = Location.create(data.latitude, data.longitude);
    Site site = Site.builder().location(location).build();

    double distance = data.distance;

    @SuppressWarnings("unchecked")
    LoadingCache<Model, HazardModel> modelCache =
        (LoadingCache<Model, HazardModel>) context.getAttribute(MODEL_CACHE_CONTEXT_ID);

    EqRate rates;

    /*
     * Because we need to combine model results, intially calculate incremental
     * annual rates and only convert to cumulative probabilities at the end if
     * probability service has been called.
     */
    Optional<Double> emptyTimespan = Optional.<Double> empty();

    // May include trailing 'B' for 2014B
    String baseYear = data.edition.name().substring(1);

    if (data.region == Region.COUS) {

      Model wusId = Model.valueOf(WUS.name() + "_" + baseYear);
      HazardModel wusModel = modelCache.get(wusId);
      ListenableFuture<EqRate> wusRates = process(wusModel, site, distance, emptyTimespan);

      String ceusYear = baseYear.equals("2014B") ? "2014" : baseYear;
      Model ceusId = Model.valueOf(CEUS.name() + "_" + ceusYear);
      HazardModel ceusModel = modelCache.get(ceusId);
      ListenableFuture<EqRate> ceusRates = process(ceusModel, site, distance, emptyTimespan);

      rates = EqRate.combine(wusRates.get(), ceusRates.get());

    } else {
      
      String year = (baseYear.equals("2014B") && data.region == Region.CEUS) 
          ?  "2014" : baseYear;
      Model modelId = Model.valueOf(data.region.name() + "_" + year);
      
      HazardModel model = modelCache.get(modelId);
      rates = process(model, site, distance, emptyTimespan).get();
    }

    if (data.timespan.isPresent()) {
      rates = EqRate.toCumulative(rates);
      rates = EqRate.toPoissonProbability(rates, data.timespan.get());
    }
    return rates;
  }

  private static ListenableFuture<EqRate> process(
      HazardModel model,
      Site site,
      double distance,
      Optional<Double> timespan) {

    Builder configBuilder = CalcConfig.Builder
        .copyOf(model.config())
        .distance(distance);
    if (timespan.isPresent()) {
      /* Also sets value format to Poisson probability. */
      configBuilder.timespan(timespan.get());
    }
    CalcConfig config = configBuilder.build();
    Callable<EqRate> task = EqRate.callable(model, config, site);
    return ServletUtil.CALC_EXECUTOR.submit(task);
  }

  static final class RequestData {

    final Edition edition;
    final Region region;
    final double latitude;
    final double longitude;
    final double distance;
    final Optional<Double> timespan;

    RequestData(
        Edition edition,
        Region region,
        double longitude,
        double latitude,
        double distance,
        Optional<Double> timespan) {

      this.edition = edition;
      this.region = region;
      this.latitude = latitude;
      this.longitude = longitude;
      this.distance = distance;
      this.timespan = timespan;
    }
  }

  private static final class ResponseData {

    final Edition edition;
    final Region region;
    final double latitude;
    final double longitude;
    final double distance;
    final Double timespan;

    final String xlabel = "Magnitude (Mw)";
    final String ylabel;

    ResponseData(RequestData request) {
      boolean isProbability = request.timespan.isPresent();
      this.edition = request.edition;
      this.region = request.region;
      this.longitude = request.longitude;
      this.latitude = request.latitude;
      this.distance = request.distance;
      this.ylabel = isProbability ? "Probability" : "Annual Rate (yr⁻¹)";
      this.timespan = request.timespan.orElse(null);
    }
  }

  private static final class Response {

    final ResponseData metadata;
    final List<Sequence> data;

    Response(ResponseData metadata, List<Sequence> data) {
      this.metadata = metadata;
      this.data = data;
    }
  }

  /*
   * TODO would rather use this a general container for mfds and hazard curves.
   * See HazardService.Curve
   */
  private static class Sequence {

    final String component;
    final List<Double> xvalues;
    final List<Double> yvalues;

    Sequence(String component, List<Double> xvalues, List<Double> yvalues) {
      this.component = component;
      this.xvalues = xvalues;
      this.yvalues = yvalues;
    }
  }

  private static final String TOTAL_KEY = "Total";

  private static final class Result {

    final String status = Status.SUCCESS.toString();
    final String date = ZonedDateTime.now().format(ServletUtil.DATE_FMT);
    final String url;
    final Object server;
    final Response response;

    Result(String url, Object server, Response response) {
      this.url = url;
      this.server = server;
      this.response = response;
    }

    static final class Builder {

      String url;
      Timer timer;
      RequestData request;
      EqRate rates;

      Builder rates(EqRate rates) {
        checkState(this.rates == null, "Rate data has already been added to this builder");
        this.rates = rates;
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

        ImmutableList.Builder<Sequence> sequenceListBuilder = ImmutableList.builder();

        /* Total mfd. */
        XySequence total = (!rates.totalMfd.isClear()) ? rates.totalMfd.trim() : rates.totalMfd;
        Sequence totalOut = new Sequence(
            TOTAL_KEY,
            total.xValues(),
            total.yValues());
        sequenceListBuilder.add(totalOut);

        /* Source type mfds. */
        for (Entry<SourceType, XySequence> entry : rates.typeMfds.entrySet()) {
          XySequence type = entry.getValue();
          if (type.isClear()) {
            continue;
          }
          type = type.trim();
          Sequence typeOut = new Sequence(
              entry.getKey().toString(),
              type.xValues(),
              type.yValues());
          sequenceListBuilder.add(typeOut);
        }

        ResponseData responseData = new ResponseData(request);
        Object server = Metadata.serverData(ServletUtil.THREAD_COUNT, timer);
        Response response = new Response(responseData, sequenceListBuilder.build());

        return new Result(url, server, response);
      }
    }
  }
}
