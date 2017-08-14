package gov.usgs.earthquake.nshmp.eq.fault.surface;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import gov.usgs.earthquake.nshmp.eq.fault.surface.DefaultGriddedSurface;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

@SuppressWarnings("javadoc")
public class DefaultGriddedSurfaceTest {

  @Test
  public final void testBuilder() {

    DefaultGriddedSurface surface = createBasic();

    assertEquals(surface.dipSpacing, 1.0, 0.0);
    assertEquals(surface.strikeSpacing, 0.9884, 0.000001);

    assertEquals(surface.getNumRows(), 16);
    assertEquals(surface.getNumCols(), 46);

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
