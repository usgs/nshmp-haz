package org.opensha.eq.forecast;

import static org.junit.Assert.*;

import org.junit.Test;

public class LoaderTests {

	
	@Test(expected = IllegalArgumentException.class)
	public void testBadPath() {
		String badPath = "badPath";
//		Loader.load(badPath);
	}
	
	// test load(file) where file is not a directory
	
//	@Test
//	public final void testBadPath() {
//		fail("Not yet implemented"); // TODO
//	}

}
