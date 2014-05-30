package org.opensha.eq.forecast;

/**
 * Ways in which {@code Rupture}s may float on a fault surface.
 * 
 * @author Peter Powers
 */
public enum FloatStyle {

	// TODO need NSHMP mag dependent floater

	/** Along-strike and using full down-dip width. */
	FULL_DOWN_DIP,

	/**
	 * Along-strike and down-dip (those ruptures whose width is less than the
	 * width of the parent fault surface).
	 */
	DOWN_DIP,

	/**
	 * Along-strike and centered down-dip (those ruptures whose width is less
	 * than the width of the parent fault surface).
	 */
	CENTERED;

}
