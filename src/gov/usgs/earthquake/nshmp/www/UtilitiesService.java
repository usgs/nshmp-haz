package gov.usgs.earthquake.nshmp.www;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import gov.usgs.earthquake.nshmp.geo.json.Feature;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson;
import gov.usgs.earthquake.nshmp.geo.json.Properties;
import gov.usgs.earthquake.nshmp.internal.NshmpSite;

@WebServlet(
    name = "Utilities Service",
    description = "USGS NSHMP Web Service Utilities",
    urlPatterns = {
        "/util",
        "/util/*" })
@SuppressWarnings("javadoc")
public class UtilitiesService extends NshmpServlet {

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    PrintWriter out = response.getWriter();
    String utilUrl = "/nshmp-haz-ws/apps/util.html";

    String pathInfo = request.getPathInfo();

    switch (pathInfo) {
      case "/testsites":
        out.println(proccessTestSites());
        break;
      default:
        response.sendRedirect(utilUrl);
    }
  }

  private static String proccessTestSites() {
    Map<String, EnumSet<NshmpSite>> nshmpSites = new HashMap<>();
    nshmpSites.put("ceus", NshmpSite.ceus());
    nshmpSites.put("cous", NshmpSite.cous());
    nshmpSites.put("wus", NshmpSite.wus());
    nshmpSites.put("ak", NshmpSite.alaska());
    nshmpSites.put("facilities", NshmpSite.facilities());
    nshmpSites.put("nehrp", NshmpSite.nehrp());
    nshmpSites.put("nrc", NshmpSite.nrc());
    nshmpSites.put("hawaii", NshmpSite.hawaii());

    GeoJson.Builder builder = GeoJson.builder();

    for (String regionKey : nshmpSites.keySet()) {
      RegionInfo regionInfo = getRegionInfo(regionKey);
      for (NshmpSite site : nshmpSites.get(regionKey)) {
        Map<String, Object> properties = Properties.builder()
            .put(Key.TITLE, site.toString())
            .put(Key.REGION_ID, regionInfo.regionId)
            .put(Key.REGION_TITLE, regionInfo.regionDisplay)
            .build();

        builder.add(Feature.point(site.location())
            .id(site.id())
            .properties(properties)
            .build());
      }
    }
    return builder.toJson();
  }

  private static class Key {
    private static final String TITLE = "title";
    private static final String REGION_ID = "regionId";
    private static final String REGION_TITLE = "regionTitle";
  }

  private static class RegionInfo {
    private String regionId;
    private String regionDisplay;

    private RegionInfo(String regionDisplay, String regionId) {
      this.regionId = regionId.toUpperCase();
      this.regionDisplay = regionDisplay;
    }

  }

  private static RegionInfo getRegionInfo(String regionId) {
    String regionDisplay = "";

    switch (regionId) {
      case "ceus":
        regionDisplay = "Central & Eastern US";
        break;
      case "cous":
        regionDisplay = "Conterminous US";
        break;
      case "wus":
        regionDisplay = "Western US";
        break;
      case "ak":
        regionDisplay = "Alaska";
        break;
      case "facilities":
        regionDisplay = "US National Labs";
        break;
      case "nehrp":
        regionDisplay = "NEHRP";
        break;
      case "nrc":
        regionDisplay = "NRC";
        break;
      case "hawaii":
        regionDisplay = "Hawaii";
        break;
      default:
        throw new RuntimeException("Region [" + regionId + "] not found");
    }

    return new RegionInfo(regionDisplay, regionId);
  }

}
