package org.opensha2.data;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class ImmutableXySequenceTests {

  @Test
  public final void testIsEmpty() {
    
    double[] xs = new double[] {0,1,2,3};
    double[] ys = new double[] {-1,0,1,0};
    
    XySequence xy = XySequence.createImmutable(xs, ys);
    assertFalse(xy.isClear());
  }

}
