package org.opensha2.calc;

import org.opensha2.geo.Location;
import org.opensha2.util.Named;

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
