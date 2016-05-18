package org.opensha2.eq.fault.surface;

import java.util.Iterator;

import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.geo.Region;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class Surfaces {

  // TODO this is corny and must be deleted

  /**
   * This returns brief info about this surface
   * @param surf
   */
  public static String getSurfaceInfo(GriddedSurface surf) {
    Location loc1 = surf.getLocation(0, 0);
    Location loc2 = surf.getLocation(0, surf.getNumCols() - 1);
    Location loc3 = surf.getLocation(surf.getNumRows() - 1, 0);
    Location loc4 = surf.getLocation(surf.getNumRows() - 1, surf.getNumCols() - 1);
    return new String("\tRup. Surf. Corner Locations (lat, lon, depth (km):" + "\n\n" + "\t\t" +
      (float) loc1.lat() + ", " + (float) loc1.lon() + ", " + (float) loc1.depth() + "\n" +
      "\t\t" + (float) loc2.lat() + ", " + (float) loc2.lon() + ", " + (float) loc2.depth() +
      "\n" + "\t\t" + (float) loc3.lat() + ", " + (float) loc3.lon() + ", " +
      (float) loc3.depth() + "\n" + "\t\t" + (float) loc4.lat() + ", " + (float) loc4.lon() +
      ", " + (float) loc4.depth() + "\n");
  }

  public static double getFractionOfSurfaceInRegion(GriddedSurface surface, Region region) {
    double numInside = 0;
    for (Location loc : surface) {
      if (region.contains(loc)) numInside += 1;
    }
    return numInside / surface.size();
  }

  // TODO this appears to only be used in UCERF3 inversion setups and
  // is only ever called by RuptureSurface implementations, all of which
  // also implement getMinDistance(RuptureSurface). Shoot for removal.

  /**
   * This returns the minimum distance as the minimum among all location pairs
   * between the two surfaces
   * @param surface1 RuptureSurface
   * @param surface2 RuptureSurface
   * @return distance in km
   */
  public static double getMinDistanceBetweenSurfaces(GriddedSurface surface1,
      GriddedSurface surface2) {
    Iterator<Location> it = surface1.iterator();
    double min3dDist = Double.POSITIVE_INFINITY;
    double dist;
    // find distance between all location pairs in the two surfaces
    while (it.hasNext()) { // iterate over all locations in this surface
      Location loc1 = (Location) it.next();
      Iterator<Location> it2 = surface2.getEvenlyDiscritizedListOfLocsOnSurface().iterator();
      while (it2.hasNext()) { // iterate over all locations on the user
        // provided surface
        Location loc2 = (Location) it2.next();
        dist = Locations.linearDistanceFast(loc1, loc2);
        if (dist < min3dDist) {
          min3dDist = dist;
        }
      }
    }
    return min3dDist;
  }

}
