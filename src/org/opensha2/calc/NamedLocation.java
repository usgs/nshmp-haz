package org.opensha.calc;

import org.opensha.geo.Location;
import org.opensha.util.Named;

/**
 * Marker interface for {@code enum}s of {@link Location}s. This interface is
 * distinct from {@link Named} due to shadowing of {@link Enum#name()}.
 *
 * @author Peter Powers
 */
public interface NamedLocation {

	/**
	 * Return the location.
	 */
	public Location location();

}
