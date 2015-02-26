package org.opensha.eq.model;

/**
 * Ways in which {@code Rupture}s may float on a fault surface.
 * 
 * @author Peter Powers
 */
@Deprecated
public enum FloatStyle {

	// TODO need NSHMP mag dependent floater
	// TODO this identifier needs to be exposed somewhere in model xml
	// probably SourceProperties

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
