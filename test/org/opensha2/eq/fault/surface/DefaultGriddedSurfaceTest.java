package org.opensha2.eq.fault.surface;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;

@SuppressWarnings("javadoc")
public class DefaultGriddedSurfaceTest {


	@Test public final void testBuilder() {
		
		DefaultGriddedSurface surface = createBasic();
		
		assertEquals(surface.dipSpacing, 1.0, 0.0);
		assertEquals(surface.strikeSpacing, 0.9884, 0.000001);
		
		assertEquals(surface.numRows, 16);
		assertEquals(surface.numCols, 46);
		
	}
	
	private static DefaultGriddedSurface createBasic() {
		
		LocationList trace = LocationList.create(
			Location.create(34.0, -118.0),
			Location.create(34.4, -118.0));

		DefaultGriddedSurface surface = DefaultGriddedSurface.builder()
				.trace(trace)
				.depth(0.0)
				.dip(90.0)
				.width(15.0)
				.build();

		return surface;
	}

}
