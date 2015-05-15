package org.opensha.eq.fault.scaling.impl;

import org.opensha.eq.fault.scaling.MagLengthRelationship;

/*
 * This is an implementation of the GeoMatrix mag length scaling relation
 * that is used for Cascadia sources
 */
public class GeoMat_MagLenthRelationship extends MagLengthRelationship {

	private final static String NAME = "NSHMP Subduction Mag-Length Relation";

	public GeoMat_MagLenthRelationship() {
		rake = Double.NaN;
	}

	@Override
	public double getMedianMag(double area) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMagStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMedianLength(double mag) {
        return Math.pow(10.0, (mag - 4.94) / 1.39);
	}

	@Override
	public double getLengthStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRake(double rake) {
		// do nothing; rake not considered in NSHMP
	}

	@Override
	public String name() {
		return NAME;
	}

}
