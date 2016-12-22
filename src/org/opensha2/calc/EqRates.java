package org.opensha2.calc;

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.geo.Location;
import org.opensha2.geo.Region;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class EqRates {

  public static EqRate atPoint(HazardModel model, Location location) {
    // return atPoint(model, location, 0.0);
    throw new UnsupportedOperationException();
  }

  public static EqRate atPoint(HazardModel model, Location location, double radius) {

    /*
     * if radius == 0, return any grid sources that fall within the
     * corresponding node for the supplied location.
     * 
     * iterableForLocation(loc) <-- gmm default iterableForLocation(loc, r)
     */

    for (SourceSet<? extends Source> sourceSet : model) {
      double weight = sourceSet.weight();
      for (Source source : sourceSet.iterableForLocation(location, radius)) {
        for (XySequence mfd : source.mfds()) {
          mfd.multiply(weight);

        }
      }
    }
    throw new UnsupportedOperationException();
  }

  public static EqRate inRegion(HazardModel model, Region region) {
    /*
     * TODO consider adding iterableForRegion and regionFilter to SourceSet
     */
    throw new UnsupportedOperationException();
  }

  public static EqRate forSource(HazardModel model, int id) {
    throw new UnsupportedOperationException();
  }

}
