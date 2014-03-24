package org.opensha.eq.forecast;

import static java.lang.Math.sin;
import static org.opensha.geo.GeoTools.TO_RAD;

import java.util.BitSet;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class IndexedFaultSource {

	// possibly rename to IndexedFaultRupture
	
	// where/how to store fault data imported from XML
	private IndexedFaultSourceSet parent;
	
	// fault subsection indices in correct stitching order
	private int[] indices;
	
	// for ordered list of subsection indices, which indices are used
	// TODO BitSets are highly mutable; any way to protect these?
	BitSet bits;
	
	// surface representation
	// other parameters... mag, rake
	double Mw;
	
	double dip;
	double rake;
	double width;
	
	double zTop;
//	double zHyp;
	
	public double mag() {
		return Mw;
	}

	public double dip() {
		return dip;
	}
	
	public double width() {
		return width;
	}
	
	public double rake() {
		return rake;
	}
	
	public double zTop() {
		return zTop;
	}

	public double zHyp() {
		double zHyp = zTop + sin(dip * TO_RAD) * width / 2.0;

		return zHyp;
	}

}
