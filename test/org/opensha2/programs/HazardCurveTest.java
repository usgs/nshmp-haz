package org.opensha2.programs;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

public class HazardCurveTest {

	@BeforeClass public static void setUpBeforeClass() throws Exception {}

	@Test public final void testRun() {
		String[] args = new String[] {};
		String status = HazardCurve.run(args);
		assertEquals(HazardCurve.USAGE.substring(0,18), status.substring(0,18));
	}
	

}
