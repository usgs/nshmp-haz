package org.opensha.gmm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Resources;
import com.google.common.primitives.Doubles;

/**
 * Class loads and manages {@code GroundMotionModel} coefficients.
 * 
 * <p>Coefficients are loaded from CSV files. When such files are updated, it
 * may be necessary to edit certain {@code Imt} designations that are commonly
 * coded as integers (e.g. -1 = PGV, usually) or coefficient IDs that contain
 * illegal characters (e.g those with units labels in parentheses). </p>
 * 
 * <p>Note that coefficent values are mapped from the supplied *.csv resource to
 * field names. This means that the {@link Coefficients} implementation in a Gmm
 * may declare additional fields that may be initialized independently. See
 * {@link CampbellBozorgnia_2013} for an example. This also means that
 * {@link Coefficients} implementations must declare all coefficents named in a
 * *.csv file or an exception is thrown.</p>
 * 
 * @author Peter Powers
 * @see Coefficients
 */
final class CoefficientContainer {

	private static final String C_DIR = "coeffs/";
	private Table<Imt, String, Double> table;
	private Map<Imt, Coefficients> coeffMap;

	/**
	 * Create a new coefficent wrapper from a comma-delimited coefficient
	 * resource for use by a Gmm.
	 * 
	 * @param resource coefficent csv text resource
	 * @param clazz of the Gmm specific {@code Coefficients} implementation
	 * @throws RuntimeException if an error occurs reading coefficient resource
	 */
	CoefficientContainer(String resource, Class<? extends Coefficients> clazz) {
		// NOTE table is only retained to more easily support get(Imt, String)
		try {
			table = load(resource);
			coeffMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : table.rowKeySet()) {
				Coefficients c = clazz.newInstance();
				set(c, imt);
				coeffMap.put(imt, c);
			}
		} catch (Exception e) {
			// TODO init logging
			e.printStackTrace();
		}
	}
	
	/*
	 * Sets the coefficient fields for the supplied {@code Imt}.
	 */
	private void set(Coefficients c, Imt imt) throws IllegalAccessException,
			NoSuchFieldException {
		for (String name : table.columnKeySet()) {
			double value = table.get(imt, name);
			c.getClass().getDeclaredField(name).set(c, value);
			c.imt = imt;
		}
	}

	Coefficients get(Imt imt) {
		return coeffMap.get(imt);
	}

	/**
	 * Returns the value of the coefficient for the supplied name and intensity
	 * measure type.
	 * @param imt intensity measure type
	 * @param name of the coefficient to look up
	 * @return the coefficient value
	 */
	double get(Imt imt, String name) {
		return table.get(imt, name);
	}
	
	/**
	 * Returns an {@code EnumSet} if the intensity measure types (IMTs) for
	 * which coefficients are supplied.
	 * @return the {@code Set} of supported IMTs
	 */
	EnumSet<Imt> imtSet() {
		return EnumSet.copyOf(table.rowKeySet());
	}

	private Table<Imt, String, Double> load(String resource) throws IOException {
		URL url = Resources.getResource(Coefficients.class, C_DIR + resource);
		List<String> lines = Resources.readLines(url, UTF_8);
		// build coeff name list
		Iterable<String> nameList = Parsing.split(lines.get(0), Delimiter.COMMA);
		Iterable<String> names = Iterables.skip(nameList, 1);
		// build Imt-value map
		Map<Imt, Double[]> valueMap = Maps.newHashMap();
		for (String line : Iterables.skip(lines, 1)) {
			Iterable<String> entries = Parsing.split(line, Delimiter.COMMA);
			String imtStr = Iterables.get(entries, 0);
			Imt imt = Imt.parseImt(imtStr);
			checkNotNull(imt, "Unparseable Imt: " + imtStr);
			Iterable<String> valStrs = Iterables.skip(entries, 1);
			Iterable<Double> values = Iterables.transform(valStrs, 
				Doubles.stringConverter());
			valueMap.put(imt, Iterables.toArray(values, Double.class));
		}
		// create and load table
		Table<Imt, String, Double> table = ArrayTable.create(valueMap.keySet(),
			names);
		for (Imt imt : valueMap.keySet()) {
			Double[] values = valueMap.get(imt);
			int i = 0;
			for (String name : names) {
				table.put(imt, name, values[i++]);
			}
		}
		return table;
	}
	
}
