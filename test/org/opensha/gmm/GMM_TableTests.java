package org.opensha.gmm;

import static org.junit.Assert.assertEquals;
import static org.opensha.util.MathUtils.LOG_BASE_10_TO_E;

import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class GMM_TableTests {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	private static final double TABLE_TOL = 0.00000000001;

	/*
	 * Test ground motion table and parser; simply tests that indexing and
	 * lookup is working satisfactorily by examining a few values including some
	 * extrema above and below the min and max mag and distance. Table
	 * implementation returns the natural log of ground motion, however,
	 * original values are base-10 so we convert back
	 */
	@Test
	public void testGroundMotionTable() {
		GMM_Table gmt = GMM_Tables.getFrankel96(IMT.PGA, SiteClass.SOFT_ROCK);
		double testVal;

		// extrema
		testVal = gmt.get(0.1, 2) / LOG_BASE_10_TO_E;
		assertEquals(-0.71, testVal, TABLE_TOL);

		testVal = gmt.get(0.1, 5.8) / LOG_BASE_10_TO_E;
		assertEquals(-0.14, testVal, TABLE_TOL);

		testVal = gmt.get(0.1, 8.3) / LOG_BASE_10_TO_E;
		assertEquals(0.60, testVal, TABLE_TOL);

		testVal = gmt.get(100, 2.0) / LOG_BASE_10_TO_E;
		assertEquals(-2.17, testVal, TABLE_TOL);

		testVal = gmt.get(100, 5.8) / LOG_BASE_10_TO_E;
		assertEquals(-1.43, testVal, TABLE_TOL);

		testVal = gmt.get(100, 8.3) / LOG_BASE_10_TO_E;
		assertEquals(-0.52, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 2.0) / LOG_BASE_10_TO_E;
		assertEquals(-4.48, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 5.8) / LOG_BASE_10_TO_E;
		assertEquals(-3.35, testVal, TABLE_TOL);

		testVal = gmt.get(1001, 8.3) / LOG_BASE_10_TO_E;
		assertEquals(-2.05, testVal, TABLE_TOL);

		// interpolation
		testVal = gmt.get(Math.pow(10, 1.55), 5.1) / LOG_BASE_10_TO_E;
		assertEquals(-1.1575, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 1.55), 7.1) / LOG_BASE_10_TO_E;
		assertEquals(-0.3775, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 2.55), 5.1) / LOG_BASE_10_TO_E;
		assertEquals(-2.7075, testVal, TABLE_TOL);

		testVal = gmt.get(Math.pow(10, 2.55), 7.1) / LOG_BASE_10_TO_E;
		assertEquals(-1.665, testVal, TABLE_TOL);

	}
}
