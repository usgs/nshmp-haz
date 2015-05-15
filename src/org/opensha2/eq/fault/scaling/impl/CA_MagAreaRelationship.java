/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.eq.fault.scaling.impl;

import org.opensha.eq.fault.scaling.MagAreaRelationship;

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
