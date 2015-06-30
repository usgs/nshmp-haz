package org.opensha2.gmm;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;

@SuppressWarnings("javadoc")
public class GroundMotionTableTests {


	private static final double TABLE_TOL = 0.00000000001;

	/*
	 * Test ground motion table and parser; simply tests that indexing and
	 * lookup is working satisfactorily by examining a few values including some
	 * extrema above and below the min and max mag and distance. Table
	 * implementation returns the natural log of ground motion, however,
	 * original values are base-10 so we convert back
	 */
	@Test public void testGroundMotionTable() {
		GroundMotionTable gmt = GroundMotionTables.getFrankel96(Imt.PGA, SiteClass.SOFT_ROCK);
		double testVal;

		// extrema
		testVal = gmt.get(0.1, 2);
		assertEquals(-0.71, testVal, TABLE_TOL);

		testVal = gmt.get(0.1, 5.8);
		assertEquals(-0.14, testVal, TABLE_TOL);

		testVal = gmt.get(0.1, 8.3);
		assertEquals(0.60, testVal, TABLE_TOL);

		testVal = gmt.get(100, 2.0);
		assertEquals(-2.17, testVal, TABLE_TOL);

		testVal = gmt.get(100, 5.8);
		assertEquals(-1.43, testVal, TABLE_TOL);

		testVal = gmt.get(100, 8.3);
		assertEquals(-0.52, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 2.0);
		assertEquals(-4.48, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 5.8);
		assertEquals(-3.35, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 8.3);
		assertEquals(-2.05, testVal, TABLE_TOL);

		// interpolation
		testVal = gmt.get(Math.pow(10, 1.55), 5.1);
		assertEquals(-1.1575, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 1.55), 7.1);
		assertEquals(-0.3775, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 2.55), 5.1);
		assertEquals(-2.7075, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 2.55), 7.1);
		assertEquals(-1.665, testVal, TABLE_TOL);

	}
}
