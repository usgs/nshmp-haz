package gov.usgs.earthquake.nshmp.util;

import static org.junit.Assert.*;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class MathsTests {

  @Test
  public void hypot() {
    /* 2- and 3-arg flavors. */
    assertEquals(Maths.hypot(3, 4), 5.0, 0.0);
    assertEquals(Maths.hypot(3, 4, 0), 5.0, 0.0);
    /* Variadic flavor. */
    assertEquals(Maths.hypot(2), 2.0, 0.0);
    assertEquals(Maths.hypot(3, 4, 0, 0), 5.0, 0.0);
  }
  
  

}
