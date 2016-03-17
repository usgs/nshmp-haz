package org.opensha2.eq.fault.scaling.impl;

import org.opensha2.eq.fault.scaling.MagAreaRelationship;

/**
 * NSHMP EllB/WC94 mag-area relation.
 *
 * @author Peter Powers
 */
public class CA_MagAreaRelationship extends MagAreaRelationship {

	/*
	 * This is an implementation of the bizarre NSHMP EllB/WC94 Mag-Area relation.
	 * See hazFXnga7c.f line ~1773
	 */

	private final static String NAME = "NSHMP CA Mag-Area Relation";

	// mag cutoff based on EllB
	private final static double mag_cut = Math.log10(500) + 4.2;

	@Override
	public double getMedianMag(double area) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMagStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMedianArea(double mag) {
		return (mag >= mag_cut) ? Math.pow(10.0, mag - 4.2) : // EllB
			Math.pow(10.0, (mag - 4.07) / 0.98); // WC94
	}

	@Override
	public double getAreaStdDev() {
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
