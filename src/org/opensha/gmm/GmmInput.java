package org.opensha.gmm;

import org.opensha.eq.forecast.Distances;

/**
 * Earthquake {@code Rupture} data and receiver site properties used as inputs
 * to ground motion models (GMMs). Not all GMMs use all properties.
 * 
 * <p>This calss also carries a rate value, which is never used by GroundMotionModels.</p>
 * 
 * @author Peter Powers
 */
public final class GmmInput {
	
	// TODO determine how best to expose rate wrt deterministic calcualtions
	public double rate; // TODO currently empty; needs to be final

	// source
	final double Mw;

	final double rJB;
	final double rRup;
	final double rX;

	final double dip;
	final double width;
	final double zTop;
	final double zHyp;

	final double rake;

	// site
	final double vs30;
	final boolean vsInf;
	final double z2p5; // always in km
	final double z1p0; // always in meters TODO NO NO NO - this is now always km, CY08 needs updating
	
	private GmmInput(double Mw, double rJB, double rRup, double rX,
		double dip, double width, double zTop, double zHyp, double rake,
		double vs30, boolean vsInf, double z2p5, double z1p0) {

		this.Mw = Mw;

		this.rJB = rJB;
		this.rRup = rRup;
		this.rX = rX;

		this.dip = dip;
		this.width = width;
		this.zTop = zTop;
		this.zHyp = zHyp;
		this.rake = rake;

		this.vs30 = vs30;
		this.vsInf = vsInf;
		this.z2p5 = z2p5;
		this.z1p0 = z1p0;
	}
	
	/**
	 * Create a container with those rupture properties required by ground
	 * motion models (GMMs).
	 * 
	 * @param Mw moment magnitude of rupture
	 * @param rJB Joyner-Boore distance to rupture (in km)
	 * @param rRup 3D distance to rupture plane (in km)
	 * @param rX distance X (in km)
	 * @param dip of rupture (in degrees)
	 * @param width down-dip rupture width (in km)
	 * @param zTop depth to the top of the rupture (in km)
	 * @param zHyp hypocentral depth (in km)
	 * @param rake of rupture
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param vsInf whether vs30 is an inferred or measured value
	 * @param z2p5 depth to V<sub>s</sub>=2.5 km/sec (in km)
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 * @return a rupture property container
	 */
	public static GmmInput create(double Mw, double rJB, double rRup,
			double rX, double dip, double width, double zTop, double zHyp,
			double rake, double vs30, boolean vsInf, double z2p5, double z1p0) {

		return new GmmInput(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake,
			vs30, vsInf, z2p5, z1p0);
	}
		
	public static Builder builder() {
		return new Builder();
	}
	
	// TODO implement builder -- what error checking to do if this is only
	//				an internal class? 
	//			- advantage of builder (document) is that it can be reused to
	//				generate new GmmInput instances that may have changed
	//				only slightly (e.g. rake and dip for floaters; site not
	//				changing etc...)


	// TODO rename unchecked builder or provide factory methods to get both checked
	// and unchecked builders
	// TODO use Distances object
	// TODO comments javadocs
	public static class Builder {
		
		double Mw;

		double rJB;
		double rRup;
		double rX;

		double dip;
		double width;
		double zTop;
		double zHyp;

		double rake;

		// site
		double vs30;
		boolean vsInf;
		double z2p5 = Double.NaN;
		double z1p0 = Double.NaN;

		private Builder() {}
		
		public Builder mag(double Mw) {
			this.Mw = Mw;
			return this;
		}
		
		public Builder distances(double rJB, double rRup, double rX) {
			this.rJB = rJB;
			this.rRup = rRup;
			this.rX = rX;
			return this;
		}
		
		public Builder distances(Distances distances) {
			this.rJB = distances.rJB;
			this.rRup = distances.rRup;
			this.rX = distances.rX;
			return this;
		}

		public Builder dip(double dip) {
			this.dip = dip;
			return this;
		}

		public Builder width(double width) {
			this.width = width;
			return this;
		}

		public Builder zTop(double zTop) {
			this.zTop = zTop;
			return this;
		}

		public Builder zHyp(double zHyp) {
			this.zHyp = zHyp;
			return this;
		}

		public Builder rake(double rake) {
			this.rake = rake;
			return this;
		}

		public Builder vs30(double vs30, boolean vsInf) {
			this.vs30 = vs30;
			this.vsInf = vsInf;
			return this;
		}

		public Builder z2p5(double z2p5) {
			this.z2p5 = z2p5;
			return this;
		}

		public Builder z1p0(double z1p0) {
			this.z1p0 = z1p0;
			return this;
		}
		
		public GmmInput build() {
			// state checking
			return new GmmInput(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake,
				vs30, vsInf, z2p5, z1p0);
		}
	}
	
	@Override
	public String toString() {
		return new StringBuilder(getClass().getSimpleName())
			.append(" [Mw: ").append(Mw)
			.append(", rJB: ").append(rJB)
			.append(", rRup: ").append(rRup)
			.append(", rX: ").append(rX)
			.append(", dip:").append(dip)
			.append(", width: ").append(width)
			.append(", zTop: ").append(zTop)
			.append(", zHyp: ").append(zHyp)
			.append(", rake: ").append(rake)
			.append(", vs30: ").append(vs30)
			.append(", vsInf: ").append(vsInf)
			.append(", z2p5: ").append(z2p5)
			.append(", z1p0: ").append(z1p0)
			.append("]")
			.toString();
	}

}
