package gov.usgs.earthquake.nshmp.data;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class ImmutableXySequenceTests {

  @Test
  public final void testIsEmpty() {
    
    double[] xs = new double[] {0,1,2,3};
    double[] ys = new double[] {-1,0,1,0};
    
    XySequence xy = XySequence.createImmutable(xs, ys);
    assertFalse(xy.isClear());
  }

}
