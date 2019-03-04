package gov.usgs.earthquake.nshmp.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class InterpolatorTests {

  /*
   * Developer notes:
   * 
   * How are single valued XySequences handled; should
   * empty XySequences be allowed, probably not; singletons should be
   * however; so how would this behave in interpolator if extrapolation
   * is allowed for y-interpolation; answer: singletons shouldn't
   * be allowed as arguments; it's just simpler
   * 
   * add checkArgument(xys.size() > 1), test with XySeq.size = 1; add to docs
   */
  
//  @Test
//  public void test() {
//    
//    fail("Not yet implemented");
//  }

}
