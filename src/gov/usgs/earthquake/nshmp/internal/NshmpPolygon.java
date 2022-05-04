package gov.usgs.earthquake.nshmp.internal;

import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Geographic polygons commonly used by the NSHMP.
 *
 * @author U.S. Geological Survey
 */
@SuppressWarnings("javadoc")
public enum NshmpPolygon {

  CEUS_CLIP(Data.CEUS_CLIP, "NSHMP Central & Eastern US Map Extents"),
  WUS_CLIP(Data.WUS_CLIP, "NSHMP Western US Map Extents"),
  CONUS_CLIP(Data.CONUS_CLIP, "NSHMP Conterminous US Map Extents"),
  ALASKA_CLIP(Data.ALASKA_CLIP, "NSHMP Alaska Map Extents"),
  HAWAII_CLIP(Data.HAWAII_CLIP, "NSHMP Hawaii Map Extents"),

  ALASKA(Data.ALASKA, "NSHMP Alaska"),
  HAWAII(Data.HAWAII, "NSHMP Hawaii"),
  CONTERMINOUS_US(Data.CONTERMINOUS, "NSHMP Conterminous US"),

  LA_BASIN(Data.WG_07_LA, "Los Angeles Basin – WGCEP 2007"),
  SF_BAY(Data.WG_02_SF, "San Francisco Bay Area – WGCEP 2002"),

  WASATCH(Data.WASATCH, "Wasatch Front – WGUEP 2013"),
  NEW_MADRID(Data.NEW_MADRID, "New Madrid Seismic Zone"),
  PUGET(Data.PUGET, "Puget Lowland"),

  UCERF3_NSHM14(Data.UCERF3_NSHM14, "UCERF3 Production – 2014 NSHM"),
  UCERF3_RELM(Data.UCERF3_RELM, "UCERF3 Development – RELM Testing Region"),
  UCERF3_NSHM_CLIP(Data.UCERF3_NSHM_CLIP, "UCERF3 Clipping Region"),

  CYBERSHAKE(Data.CYBERSHAKE, "Cybershake – Los Angeles Basin");

  private final LocationList coordinates;
  private final String label;

  private NshmpPolygon(double[][] coords, String label) {
    this.coordinates = createPolygon(coords);
    this.label = label;
  }

  /**
   * Return a list of locations containing the coordinates of this polygon.
   */
  public LocationList coordinates() {
    return coordinates;
  }

  @Override
  public String toString() {
    return label;
  }

  private static LocationList createPolygon(double[][] coords) {
    LocationList.Builder locs = LocationList.builder();
    for (double[] coord : coords) {
      locs.add(coord[1], coord[0]);
    }
    return locs.build();
  }

  private static class Data {

    private static final double[][] CONUS_CLIP = {
        { -125.0, 24.4 },
        { -65.0, 50.0 }
    };

    private static final double[][] CEUS_CLIP = {
        { -115.0, 24.4 },
        { -65.0, 50.0 }
    };

    private static final double[][] WUS_CLIP = {
        { -125.0, 24.4 },
        { -100.0, 50.0 }
    };

    private static final double[][] ALASKA_CLIP = {
        { -200.0, 48.0 },
        { -125.0, 72.0 }
    };

    private static final double[][] ALASKA = {
        { -137.5, 70.6 },
        { -157.1, 72.6 },
        { -163.6, 71.6 },
        { -168.4, 69.7 },
        { -175.2, 61.4 },
        { -178.8, 56.8 },
        { -182.5, 55.6 },
        { -188.3, 55.4 },
        { -191.1, 54.5 },
        { -191.5, 52.9 },
        { -191.1, 50.8 },
        { -187.8, 49.5 },
        { -178.0, 48.4 },
        { -162.1, 50.3 },
        { -152.2, 53.8 },
        { -147.8, 56.3 },
        { -142.8, 57.2 },
        { -138.8, 55.8 },
        { -134.9, 53.5 },
        { -130.6, 53.0 },
        { -126.8, 55.5 },
        { -134.3, 60.8 },
        { -136.6, 60.7 },
        { -137.5, 61.4 },
        { -137.5, 70.6 }
    };

    private static final double[][] HAWAII_CLIP = {
        { -160.5, 18.6 },
        { -154.3, 22.5 }
    };

    private static final double[][] HAWAII = {
        { -160.39, 21.74 },
        { -160.07, 21.65 },
        { -159.81, 21.90 },
        { -159.49, 21.75 },
        { -159.25, 21.82 },
        { -158.39, 21.49 },
        { -158.19, 21.22 },
        { -157.69, 21.14 },
        { -157.41, 21.03 },
        { -157.12, 20.71 },
        { -156.81, 20.46 },
        { -156.60, 20.41 },
        { -156.23, 20.47 },
        { -156.05, 20.25 },
        { -156.03, 20.03 },
        { -156.14, 19.84 },
        { -156.16, 19.67 },
        { -156.09, 19.48 },
        { -156.08, 19.29 },
        { -156.08, 19.06 },
        { -155.96, 18.89 },
        { -155.81, 18.80 },
        { -155.57, 18.72 },
        { -155.36, 18.69 },
        { -155.18, 18.69 },
        { -155.00, 18.78 },
        { -154.90, 18.92 },
        { -154.78, 19.05 },
        { -154.58, 19.15 },
        { -154.47, 19.26 },
        { -154.43, 19.42 },
        { -154.48, 19.60 },
        { -154.72, 19.76 },
        { -154.87, 19.93 },
        { -155.03, 20.14 },
        { -155.34, 20.34 },
        { -155.56, 20.46 },
        { -155.71, 20.61 },
        { -155.74, 20.78 },
        { -155.82, 20.95 },
        { -156.02, 21.07 },
        { -156.29, 21.18 },
        { -156.56, 21.30 },
        { -156.84, 21.41 },
        { -157.21, 21.44 },
        { -157.46, 21.50 },
        { -157.63, 21.69 },
        { -157.86, 21.85 },
        { -158.16, 21.84 },
        { -158.35, 21.71 },
        { -159.13, 21.96 },
        { -159.15, 22.19 },
        { -159.32, 22.35 },
        { -159.63, 22.39 },
        { -159.88, 22.23 },
        { -160.15, 22.11 },
        { -160.34, 21.99 },
        { -160.39, 21.74 }
    };

    private static final double[][] CONTERMINOUS = {
        { -97.7, 25.6 },
        { -96.9, 25.6 },
        { -96.9, 27.6 },
        { -93.5, 29.5 },
        { -90.6, 28.7 },
        { -88.9, 28.7 },
        { -88.5, 30.0 },
        { -86.2, 30.0 },
        { -85.3, 29.4 },
        { -84.9, 29.4 },
        { -84.0, 29.8 },
        { -83.0, 28.7 },
        { -83.1, 27.7 },
        { -82.0, 25.8 },
        { -82.0, 24.4 },
        { -81.2, 24.4 },
        { -80.0, 25.0 },
        { -79.7, 26.8 },
        { -81.1, 30.7 },
        { -80.7, 31.8 },
        { -75.1, 35.2 },
        { -75.1, 35.7 },
        { -75.6, 36.9 },
        { -73.4, 40.4 },
        { -69.8, 41.1 },
        { -69.8, 42.2 },
        { -70.4, 42.4 },
        { -70.4, 43.0 },
        { -69.9, 43.6 },
        { -68.8, 43.7 },
        { -66.7, 44.7 },
        { -67.5, 46.0 },
        { -67.5, 47.1 },
        { -68.4, 47.6 },
        { -69.4, 47.6 },
        { -71.8, 45.2 },
        { -75.1, 45.2 },
        { -76.7, 44.1 },
        { -76.6, 43.7 },
        { -77.0, 43.5 },
        { -78.8, 43.5 },
        { -79.4, 43.3 },
        { -79.2, 42.8 },
        { -82.2, 41.7 },
        { -82.7, 41.7 },
        { -82.9, 42.1 },
        { -82.3, 42.6 },
        { -82.2, 43.1 },
        { -83.3, 46.4 },
        { -88.4, 48.5 },
        { -89.6, 48.2 },
        { -94.4, 49.0 },
        { -94.6, 49.6 },
        { -95.5, 49.6 },
        { -95.5, 49.2 },
        { -123.7, 49.2 },
        { -123.7, 48.6 },
        { -125.3, 48.6 },
        { -124.6, 46.2 },
        { -125.0, 43.2 },
        { -125.0, 40.0 },
        { -119.9, 33.0 },
        { -117.0, 32.3 },
        { -115.0, 32.3 },
        { -111.1, 31.1 },
        { -107.9, 31.1 },
        { -107.9, 31.5 },
        { -106.6, 31.5 },
        { -105.1, 30.2 },
        { -104.8, 29.4 },
        { -103.3, 28.7 },
        { -103.0, 28.7 },
        { -102.4, 29.5 },
        { -101.5, 29.5 },
        { -99.2, 26.1 },
        { -97.7, 25.6 }
    };

    private static final double[][] CYBERSHAKE = {
        { -119.38, 34.13 },
        { -117.50, 33.25 },
        { -116.85, 34.19 },
        { -118.75, 35.08 },
        { -119.38, 34.13 }
    };

    private static final double[][] WG_07_LA = {
        { -119.80, 33.86 },
        { -117.42, 32.94 },
        { -116.70, 34.23 },
        { -119.07, 35.15 },
        { -119.80, 33.86 }
    };

    private static final double[][] WG_02_SF = {
        { -122.09, 36.43 },
        { -120.61, 37.19 },
        { -122.08, 39.02 },
        { -123.61, 38.23 },
        { -122.09, 36.43 }
    };

    private static final double[][] PUGET = {
        { -123.5, 46.5 },
        { -121.5, 46.5 },
        { -121.5, 48.5 },
        { -123.5, 48.5 },
        { -123.5, 46.5 }
    };

    private static final double[][] WASATCH = {
        { -113.25, 39.0 },
        { -110.75, 39.0 },
        { -110.75, 42.5 },
        { -113.25, 42.5 },
        { -113.25, 39.0 }
    };

    private static final double[][] NEW_MADRID = {
        { -92.5, 34.0 },
        { -86.5, 34.0 },
        { -86.5, 40.0 },
        { -92.5, 40.0 },
        { -92.5, 34.0 }
    };

    private static final double[][] UCERF3_RELM = {
        { -125.2, 43.0 },
        { -125.4, 40.5 },
        { -125.4, 40.2 },
        { -123.8, 37.7 },
        { -121.6, 34.2 },
        { -121.0, 33.7 },
        { -118.4, 32.8 },
        { -117.9, 31.9 },
        { -117.1, 31.5 },
        { -114.5, 31.7 },
        { -113.6, 32.2 },
        { -113.5, 32.9 },
        { -113.1, 34.3 },
        { -114.0, 35.7 },
        { -119.0, 39.4 },
        { -119.0, 43.0 },
        { -125.2, 43.0 },
    };

    private static final double[][] UCERF3_NSHM14 = {
        { -125.2, 45.0 },
        { -125.4, 40.5 },
        { -125.4, 40.2 },
        { -123.8, 37.7 },
        { -121.6, 34.2 },
        { -121.0, 33.7 },
        { -118.4, 32.8 },
        { -117.9, 31.9 },
        { -117.1, 31.5 },
        { -111.5, 31.5 },
        { -111.5, 36.5 },
        { -116.5, 40.5 },
        { -116.5, 45.0 },
        { -125.2, 45.0 }
    };

    private static final double[][] UCERF3_NSHM_CLIP = {
        { -119.999, 39.000 },
        { -114.635, 35.000 },
        { -114.616, 34.848 },
        { -114.482, 34.719 },
        { -114.371, 34.464 },
        { -114.122, 34.285 },
        { -114.413, 34.097 },
        { -114.519, 33.934 },
        { -114.511, 33.616 },
        { -114.636, 33.426 },
        { -114.710, 33.401 },
        { -114.676, 33.055 },
        { -114.501, 33.020 },
        { -114.455, 32.861 },
        { -114.575, 32.741 },
        { -114.719, 32.718 },
        { -120.861, 32.151 },
        { -126.000, 39.000 },
        { -126.000, 42.001 },
        { -119.999, 42.001 },
        { -119.999, 39.000 }
    };
  }

}
