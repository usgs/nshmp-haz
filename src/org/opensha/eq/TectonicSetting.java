package org.opensha.eq;

import org.opensha.util.Parsing;

/**
 * Tectonic setting identifier.
 * @author Peter Powers
 */
public enum TectonicSetting {

	/** Active shallow crust tectonic setting identifier. */
	ACTIVE_SHALLOW_CRUST,
	
	/** Stable shallow crust tectonic setting identifier. */
	STABLE_SHALLOW_CRUST,
	
	/** Subduction Interface tectonic setting identifier. */
	SUBDUCTION_INTERFACE,
	
	/** Subduction IntraSlab tectonic setting identifier. */
	SUBDUCTION_INTRASLAB,
	
	/** Volcanic tectonic setting identifier. */
	VOLCANIC;

	@Override
	public String toString() {
		return Parsing.enumLabelWithSpaces(this);
	}
	
	// TODO delete
	public static void main(String[] args) {
		for (TectonicSetting ts : TectonicSetting.values()) {
			System.out.println(ts);
		}
	}
}
