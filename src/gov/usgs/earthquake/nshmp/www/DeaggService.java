package gov.usgs.earthquake.nshmp.www;

import static gov.usgs.earthquake.nshmp.www.ServletUtil.GSON;
import static gov.usgs.earthquake.nshmp.www.ServletUtil.emptyRequest;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import gov.usgs.earthquake.nshmp.calc.Deaggregation;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.HazardService.RequestData;
import gov.usgs.earthquake.nshmp.www.ServletUtil.TimedTask;
import gov.usgs.earthquake.nshmp.www.ServletUtil.Timer;
import gov.usgs.earthquake.nshmp.www.meta.Edition;
import gov.usgs.earthquake.nshmp.www.meta.Metadata;
import gov.usgs.earthquake.nshmp.www.meta.Region;
import gov.usgs.earthquake.nshmp.www.meta.Status;

/**
 * Hazard deaggregation service.
 *
 * @author Peter Powers
 */
@SuppressWarnings("unused")
@WebServlet(
    name = "Deaggregation Service",
    description = "USGS NSHMP Hazard Deaggregator",
    urlPatterns = {
        "/deagg",
        "/deagg/*" })
public final class DeaggService extends NshmpServlet {

  /* Developer notes: See HazardService. */

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    UrlHelper urlHelper = urlHelper(request, response);
    String query = request.getQueryString();
    String pathInfo = request.getPathInfo();

    if (emptyRequest(request)) {
      urlHelper.writeResponse(Metadata.DEAGG_USAGE);
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
        requestData = HazardService.buildRequest(request);
      } else {
        /* process slash-delimited request */
        List<String> params = Parsing.splitToList(pathInfo, Delimiter.SLASH);
        if (params.size() < 7) {
          urlHelper.writeResponse(Metadata.DEAGG_USAGE);
          return;
        }
        requestData = HazardService.buildRequest(params);
      }

      /* Submit as task to job executor */
      DeaggTask task = new DeaggTask(urlHelper.url, getServletContext(), requestData);
      Result result = ServletUtil.TASK_EXECUTOR.submit(task).get();
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

  private static class DeaggTask extends TimedTask<Result> {

    RequestData data;

    DeaggTask(String url, ServletContext context, RequestData data) {
      super(url, context);
      this.data = data;
    }

    @Override
    Result calc() throws Exception {

      Hazard hazard = HazardService.calcHazard(data, context);
      Deaggregation deagg = HazardCalcs.deaggReturnPeriod(
          hazard,
          data.returnPeriod.getAsDouble(),
          ServletUtil.CALC_EXECUTOR);

      return new Result.Builder()
          .requestData(data)
          .url(url)
          .timer(timer)
          .deagg(deagg)
          .build();
    }
  }

  private static final class ResponseData {

    final Edition edition;
    final Region region;
    final double latitude;
    final double longitude;
    final Imt imt;
    final double returnperiod;
    final Vs30 vs30;
    final String rlabel = "Closest Distance, rRup (km)";
    final String mlabel = "Magnitude (Mw)";
    final String εlabel = "% Contribution to Hazard";
    final Object εbins;

    ResponseData(Deaggregation deagg, RequestData request, Imt imt) {
      this.edition = request.edition;
      this.region = request.region;
      this.longitude = request.longitude;
      this.latitude = request.latitude;
      this.imt = imt;
      this.returnperiod = request.returnPeriod.getAsDouble();
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
        for (Imt imt : request.imts) {
          ResponseData responseData = new ResponseData(
              deagg,
              request,
              imt);
          Object deaggs = deagg.toJson(imt);
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
