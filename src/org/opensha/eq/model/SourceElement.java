package org.opensha.eq.model;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * HazardModel source XML elements.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum SourceElement {

	SOURCE,
	SETTINGS,
	DEFAULT_MFDS,
	INCREMENTAL_MFD,
	SOURCE_PROPERTIES,

	MAG_UNCERTAINTY,
	EPISTEMIC,
	ALEATORY,

	FAULT_SOURCE_SET,
	GEOMETRY,
	TRACE,

	SUBDUCTION_SOURCE_SET,
	LOWER_TRACE,

	SYSTEM_SOURCE_SET,
	SYSTEM_FAULT_SECTIONS,
	SECTION,
	
	GRID_SOURCE_SET,
	
	NODES,
	NODE,
	
	CLUSTER_SOURCE_SET,
	CLUSTER;
		
	/**
	 * Returns a {@code CaseFormat#UPPER_CAMEL} {@code String} representation
	 * of this {@code SourceElement}.
	 */
	@Override
	public String toString() { 
		return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
	}
	
	/**
	 * Converts supplied {@code String} to equivalent {@code SourceElement}.
	 * Method expects a {@code String} with {@code CaseFormat#UPPER_CAMEL}
	 * @param s {@code String} to convert
	 * @return the corresponding {@code SourceElement}
	 * @see CaseFormat
	 * @throws IllegalArgumentException if supplied {@code String} is
	 *         incorrectly formatted or no matching {@code SourceElement} exists
	 */
	public static SourceElement fromString(String s) {
		return valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, s));
	}
		
}
