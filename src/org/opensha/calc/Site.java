package org.opensha.calc;

import org.opensha.geo.Location;

/**
 * Basic site data container. AsyncCalc have direct access to fields.
 * @author Peter Powers
 */
public class Site {
	
	public final Location loc;
	public final double vs30;
	public final boolean vsInferred;
	public final double z1p0;
	public final double z2p5;
	
	private Site(Location loc, double vs30, boolean vsInferred, double z1p0, double z2p5) {
		this.loc = loc;
		this.vs30 = vs30;
		this.vsInferred = vsInferred;
		this.z1p0 = z1p0;
		this.z2p5 = z2p5;
	}
	
	public static Site create(Location loc) {
		return new Site(loc, 760.0, true, Double.NaN, Double.NaN);
	}
	
	public static Site create(Location loc, double vs30) {
		return new Site(loc, vs30, true, Double.NaN, Double.NaN);
	}
	
	public static Site create(Location loc, double vs30, boolean vsInferred) {
		return new Site(loc, vs30, vsInferred, Double.NaN, Double.NaN);
	}
	
	public static Site create(Location loc, double vs30, double z1p0, double z2p5) {
		return new Site(loc, vs30, true, z1p0, z1p0);
	}
	
	public static Site create(Location loc, double vs30, boolean vsInferred, double z1p0, double z2p5) {
		return new Site(loc, vs30, vsInferred, z1p0, z1p0);
	}

}
