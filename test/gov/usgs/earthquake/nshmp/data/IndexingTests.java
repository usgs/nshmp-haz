package gov.usgs.earthquake.nshmp.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.BitSet;
import java.util.List;

import org.junit.Test;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

@SuppressWarnings("javadoc")
public final class IndexingTests {

  @Test
  public final void testIndices() {
    // size - also tests from lower than to
    int[] expect = { 0, 1, 2, 3 };
    assertArrayEquals(expect, Indexing.indices(4));
    // to lower than from
    expect = new int[] { 3, 2, 1, 0 };
    assertArrayEquals(expect, Indexing.indices(3, 0));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesSizeLo_IAE() {
    Indexing.indices(0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesSizeHi_IAE() {
    Indexing.indices(Indexing.INDICES_MAX_SIZE + 1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesFrom_IAE() {
    Indexing.indices(-1, 10);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesTo_IAE() {
    Indexing.indices(10, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesFromToSize_IAE() {
    Indexing.indices(0, Indexing.INDICES_MAX_SIZE + 1);
  }

  @Test
  public final void testSortedIndices() {
    List<Double> input = Doubles.asList(5, 2, 4, -1, Double.NaN, 10, Double.NEGATIVE_INFINITY);
    List<Integer> expectedAscending = Ints.asList(6, 3, 1, 2, 0, 5, 4);
    List<Integer> expectedDescending = Ints.asList(4, 5, 0, 2, 1, 3, 6);
    assertEquals(expectedAscending, Indexing.sortedIndices(input, true));
    assertEquals(expectedDescending, Indexing.sortedIndices(input, false));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testSortedIndices_IAE() {
    Indexing.sortedIndices(Doubles.asList(), true);
  }

  @Test
  public final void testBitsToIndices() {
    int[] expect = { 0, 1, 7, 9 };
    BitSet bits = new BitSet(10);
    for (int i : expect) {
      bits.set(i);
    }
    assertArrayEquals(expect, Indexing.bitsToIndices(bits));
  }

  @Test
  public final void testIndicesToBits() {
    List<Integer> indices = Ints.asList(0, 1, 7, 9);
    int capacity = 10;
    BitSet expect = new BitSet(capacity);
    for (int i : indices) {
      expect.set(i);
    }
    assertEquals(expect, Indexing.indicesToBits(indices, capacity));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testIndicesToBitsSize_IAE() {
    Indexing.indicesToBits(Ints.asList(), -1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public final void testIndicesToBitsBadIndices_IOBE() {
    Indexing.indicesToBits(Ints.asList(10), 10);
  }

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
