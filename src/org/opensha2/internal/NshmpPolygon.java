package org.opensha2.internal;

import org.opensha2.geo.LocationList;

/**
 * Geographic polygons commonly used by the NSHMP.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum NshmpPolygon {

  CEUS_CLIP(Data.CEUS_CLIP, "Central & Eastern US Map Extents"),
  WUS_CLIP(Data.WUS_CLIP, "Western US Map Extents"),

  CONTERMINOUS_US(Data.CONTERMINOUS, "Conterminous US"),

  LA_BASIN(Data.WG_07_LA, "Los Angeles Basin – WGCEP 2007"),
  SF_BAY(Data.WG_02_SF, "San Francisco Bay Area – WGCEP 2002"),

  WASATCH(Data.WASATCH, "Wasatch Front – WGUEP 2013"),
  NEW_MADRID(Data.NEW_MADRID, "New Madrid Seismic Zone"),
  PUGET(Data.PUGET, "Puget Lowland"),

  UCERF3_NSHM14(Data.UCERF3_NSHM14, "UCERF3 Production – 2014 NSHM"),
  UCERF3_RELM(Data.UCERF3_RELM, "UCERF3 Development – RELM Testing Region"),

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

    private static final double[][] CEUS_CLIP = {
        { -115.0, 24.6 },
        { -65.0, 50.0 }
    };

    private static final double[][] WUS_CLIP = {
        { -125.0, 24.6 },
        { -100.0, 50.0 }
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
  }

}
