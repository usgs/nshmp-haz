package org.opensha.eq.fault.scaling;

import org.opensha.eq.fault.scaling.impl.CA_MagAreaRelationship;
import org.opensha.eq.fault.scaling.impl.GeoMat_MagLenthRelationship;
import org.opensha.eq.fault.scaling.impl.WC1994_MagLengthRelationship;

/**
 * Magnitude scaling realtionship identifiers.
 * 
 * @author Peter Powers
 */
public enum MagScalingType {

	/** EllsworthB – Hanks & Bakun hybrid used in UCERF2 and the 2008 NSHMP. */
	NSHMP_CA {
		@Override
		public MagScalingRelationship instance() {
			return new CA_MagAreaRelationship();
		}
	},

	/** Wells and Coppersmith '94. */
	WC_94_LENGTH {
		@Override
		public MagScalingRelationship instance() {
			return new WC1994_MagLengthRelationship();
		}
	},

	/** Youngs (GeoMatrix) subduction relation (TODO reference?). */
	GEOMAT {
		@Override
		public MagScalingRelationship instance() {
			return new GeoMat_MagLenthRelationship();
		}
	},
	
	/**
	 * Type indicator for use with sources where a magnitude scaling realtion is
	 * not required in as much as it is built into predefined source geometry
	 * and magnitude data. Calling instance() throws an
	 * {@link UnsupportedOperationException}.
	 */
	BUILT_IN {
		@Override
		public MagScalingRelationship instance() {
			throw new UnsupportedOperationException();
		}
	};

	/**
	 * Returns a new instance of the magnitude scaling relation.
	 * @return a new instance
	 */
	public abstract MagScalingRelationship instance();
}
