package org.opensha.eq.forecast;

import org.opensha.geo.Location;
import org.opensha.eq.fault.Faults;
import org.opensha.eq.fault.surface.RuptureSurface;

/**
 * A {@code Rupture} is a proxy for an actual earthquake and encapsulates all the
 * source information required by a ground motion model (Gmm).
 *
 * @author Peter Powers
 */
public class Rupture {

    double mag;
    double rake;
	double rate;
    Location hypocenter; // TODO needed??
    RuptureSurface surface;

	/* for internal use only */
	Rupture() {}
	
	private Rupture(double mag, double rate, double rake,
		RuptureSurface surface, Location hypocenter) {
		this.mag = mag;
		// TODO validate mag?
		// where are mags coming from? if MFD then no need to validate
		this.rate = rate;
		this.rake = Faults.validateRake(rake);
		this.surface = surface;
		this.hypocenter = hypocenter;
		// TODO checkNotNull?
	}

	/**
	 * 
	 * @param mag moment magnitude
	 * @param rate of occurrence (annual)
	 * @param rake slip direction on rupture surface
	 * @param surface of the rupture
	 * @return a new {@code Rupture}
	 */
	public static Rupture create(double mag, double rate, double rake,
			RuptureSurface surface) {
		return new Rupture(mag, rate, rake, surface, null);
	}

	/**
	 * Creates a new {@code Rupture}.
	 * 
	 * @param mag moment magnitude
	 * @param rate of occurrence (annual)
	 * @param rake slip direction on rupture surface
	 * @param surface of the rupture
	 * @param hypocenter of the rupture
	 * @return a new {@code Rupture}
	 */
	public static Rupture create(double mag, double rate, double rake,
			RuptureSurface surface, Location hypocenter) {
		return new Rupture(mag, rate, rake, surface, hypocenter);
	}

    public double mag() { return mag; }
    
    public double rake() { return rake; }

    public RuptureSurface surface() { return surface; }

//    public Location hypocenter() { return hypocenter; }

	public double rate() { return rate; }


	// TODO clean up
	@Override
    public String toString() {
       String info = new String("\tMag. = " + (float) mag + "\n" +
                         "\tAve. Rake = " + (float) rake + "\n" +
                         "\tAve. Dip = " + (float) surface.dip() +
                         "\n" +
                         "\tHypocenter = " + hypocenter + "\n");

      info += surface.toString();
      return info;
    }
    

}
