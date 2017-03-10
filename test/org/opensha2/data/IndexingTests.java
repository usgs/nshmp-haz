package org.opensha2.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class IndexingTests {

  @Test
  public final void testMinMaxIndex() {
    // 1D
    double[] D1_1 = { 4, -1, 33, -8, 33 };
    double[] D1_2 = {};
    assertEquals(3, Indexing.minIndex(D1_1));
    assertEquals(2, Indexing.maxIndex(D1_1));
    assertEquals(-1, Indexing.minIndex(D1_2));
    assertEquals(-1, Indexing.maxIndex(D1_2));
    // 2D
    double[][] D2_1 = { D1_1, D1_1 };
    double[][] D2_2 = { D1_2, D1_2 };
    assertArrayEquals(new int[] { 0, 3 }, Indexing.minIndex(D2_1));
    assertArrayEquals(new int[] { 0, 2 }, Indexing.maxIndex(D2_1));
    assertArrayEquals(new int[] { -1, -1 }, Indexing.minIndex(D2_2));
    assertArrayEquals(new int[] { -1, -1 }, Indexing.maxIndex(D2_2));
    // 3D
    double[][][] D3_1 = { D2_1, D2_1 };
    double[][][] D3_2 = { D2_2, D2_2 };
    assertArrayEquals(new int[] { 0, 0, 3 }, Indexing.minIndex(D3_1));
    assertArrayEquals(new int[] { 0, 0, 2 }, Indexing.maxIndex(D3_1));
    assertArrayEquals(new int[] { -1, -1, -1 }, Indexing.minIndex(D3_2));
    assertArrayEquals(new int[] { -1, -1, -1 }, Indexing.maxIndex(D3_2));
  }


}
