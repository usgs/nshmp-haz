package org.opensha.gmm;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.lang.reflect.Field;

import com.google.common.base.Throwables;

/**
 * Base class for ground motion model (Gmm) coefficients. Concrete
 * implementations are created as nested static classes that declare all
 * necessary fields in each Gmm implementation. The static
 * {@code CoefficientContainer} in each Gmm implementation assumes reponsibility
 * for creating and populating {@code Coefficients} instances for every type of
 * Imt supported by a Gmm.
 * 
 * <p>{@code Coefficients} implementations may declare fields that a
 * CoefficientContainer is unaware of that can be initialized independently. See
 * CampbellBozorgnia_2013 for an example. Implementations must also declare all
 * coefficients that exist in the header line of a *.csv source file  (see
 * {@link CoefficientContainer}) or a {@code NoSuchFieldException} is
 * thrown. Variable names in *.csv files are case-sensitive and subject to
 * the same Java variable naming rules that apply to the fields to which the
 * coefficient values will be mapped.</p>
 * 
 * @author Peter Powers
 * @see CoefficientContainer
 */
abstract class Coefficients {

	Imt imt;

	@Override
	public String toString() {
		try {
			StringBuilder sb = new StringBuilder();
			for (Field f : this.getClass().getDeclaredFields()) {
				sb.append(f.getName());
				sb.append(": ");
				sb.append(f.get(this));
				sb.append(LINE_SEPARATOR.value());
			}
			return sb.toString();
		} catch (IllegalAccessException iae) {
			throw Throwables.propagate(iae);
		}
	}

}
