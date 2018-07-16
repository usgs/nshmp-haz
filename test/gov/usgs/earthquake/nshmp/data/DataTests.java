package gov.usgs.earthquake.nshmp.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.gmm.Imt;

@SuppressWarnings("javadoc")
public final class DataTests {

  @Rule
  public ExpectedException exception = ExpectedException.none();
  
  private static final double[] VALUES = { 1.0, 10.0, 100.0 };

  private static double[] valueArray() {
    return Arrays.copyOf(VALUES, VALUES.length);
  }

  private static List<Double> valueList() {
    return Doubles.asList(valueArray());
  }

  static final void testArrayAndList(
      double[] expectArray,
      double[] actualArray,
      List<Double> actualList) {

    assertArrayEquals(expectArray, actualArray, 0.0);
    List<Double> expectList = Doubles.asList(expectArray);
    assertEquals(expectList, actualList);
  }

  /* * * * * * * * * * * * * OPERATORS * * * * * * * * * * * * */

  @Test
  public final void testAddTerm() {
    // 1D
    double[] expectArray = { 2.0, 11.0, 101.0 };
    double[] actualArray = Data.add(1.0, valueArray());
    List<Double> actualList = Data.add(1.0, valueList());
    testArrayAndList(expectArray, actualArray, actualList);
    // 2D
    double[][] d2_expect = { expectArray, expectArray };
    double[][] d2_input = { valueArray(), valueArray() };
    double[][] d2_actual = Data.add(1.0, d2_input);
    for (int i = 0; i < d2_expect.length; i++) {
      assertArrayEquals(d2_expect[i], d2_actual[i], 0.0);
    }
    // 3D
    double[][][] d3_expect = { { expectArray, expectArray }, { expectArray, expectArray } };
    double[][][] d3_input = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_actual = Data.add(1.0, d3_input);
    for (int i = 0; i < d3_expect.length; i++) {
      for (int j = 0; j < d3_expect[1].length; j++) {
        assertArrayEquals(d3_expect[i][j], d3_actual[i][j], 0.0);
      }
    }
  }

  @Test
  public final void testAddArrays() {
    // 1D array and list
    double[] expectArray = { 2.0, 20.0, 200.0 };
    double[] actualArray = Data.add(valueArray(), valueArray());
    List<Double> actualList = Data.add(valueList(), valueList());
    testArrayAndList(expectArray, actualArray, actualList);
    // 2D primitive arrays
    double[][] d2_expect = { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } };
    double[][] d2_1 = { valueArray(), valueArray() };
    double[][] d2_2 = { valueArray(), valueArray() };
    double[][] d2_actual = Data.add(d2_1, d2_2);
    for (int i = 0; i < d2_expect.length; i++) {
      assertArrayEquals(d2_expect[i], d2_actual[i], 0.0);
    }
    // 3D primitive arrays
    double[][][] d3_expect = {
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } },
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } } };
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_2 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_actual = Data.add(d3_1, d3_2);
    for (int i = 0; i < d3_expect.length; i++) {
      for (int j = 0; j < d3_expect[1].length; j++) {
        assertArrayEquals(d3_expect[i][j], d3_actual[i][j], 0.0);
      }
    }
  }

  @Test
  public final void testAddArraysUnchecked() {
    // 1D checked covariant passes through to unchecked
    // 2D primitive arrays
    double[][] d2_expect = { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } };
    double[][] d2_1 = { valueArray(), valueArray() };
    double[][] d2_2 = { valueArray(), valueArray() };
    double[][] d2_actual = Data.uncheckedAdd(d2_1, d2_2);
    for (int i = 0; i < d2_expect.length; i++) {
      assertArrayEquals(d2_expect[i], d2_actual[i], 0.0);
    }
    // 3D primitive arrays
    double[][][] d3_expect = {
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } },
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } } };
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_2 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_actual = Data.uncheckedAdd(d3_1, d3_2);
    for (int i = 0; i < d3_expect.length; i++) {
      for (int j = 0; j < d3_expect[1].length; j++) {
        assertArrayEquals(d3_expect[i][j], d3_actual[i][j], 0.0);
      }
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddLists1D_IAE() {
    Data.add(valueList(), new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArrays1D_IAE() {
    Data.add(valueArray(), new double[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArray2D1_IAE() {
    double[][] d2_1 = { valueArray(), valueArray() };
    // 1st level lengths different
    double[][] d2_2 = { valueArray() };
    Data.add(d2_1, d2_2);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArray2D2_IAE() {
    double[][] d2_1 = { valueArray(), valueArray() };
    // 2nd level lengths different
    double[][] d2_2 = { valueArray(), {} };
    Data.add(d2_1, d2_2);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArray3D1_IAE() {
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    // 1st level lengths different
    double[][][] d3_2 = { { valueArray(), valueArray() } };
    Data.add(d3_1, d3_2);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArray3D2_IAE() {
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    // 2nd level lengths different
    double[][][] d3_2 = { { valueArray(), valueArray() }, { valueArray() } };
    Data.add(d3_1, d3_2);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAddArray3D3_IAE() {
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    // 3rd level lengths different
    double[][][] d3_2 = { { valueArray(), valueArray() }, { valueArray(), {} } };
    Data.add(d3_1, d3_2);
  }

  @Test
  public final void testAddMap() {
    Map<Imt, Double> m1 = new EnumMap<>(Imt.class);
    m1.put(Imt.PGA, 0.01);
    m1.put(Imt.SA1P0, 0.5);
    Map<Imt, Double> m2 = new EnumMap<>(Imt.class);
    m2.put(Imt.PGA, 0.01);
    m2.put(Imt.SA0P2, 0.2);
    m2.put(Imt.SA1P0, 0.5);
    Map<Imt, Double> mExpect = new EnumMap<>(Imt.class);
    mExpect.put(Imt.PGA, 0.02);
    mExpect.put(Imt.SA0P2, 0.2);
    mExpect.put(Imt.SA1P0, 1.0);
    Map<Imt, Double> m1p2 = Data.add(m1, m2);
    assertEquals(mExpect, m1p2);
  }

  @Test
  public final void testSubtract() {
    // 1D array and list
    double[] expectArray = { 0.0, 0.0, 0.0 };
    double[] actualArray = Data.subtract(valueArray(), valueArray());
    List<Double> actualList = Data.subtract(valueList(), valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testSubtractLists1D_IAE() {
    Data.subtract(valueList(), new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testSubtractArrays1D_IAE() {
    Data.subtract(valueArray(), new double[0]);
  }

  @Test
  public final void testMultiplyTerm() {
    // 1D
    double[] expectArray = { 5.0, 50.0, 500.0 };
    double[] actualArray = Data.multiply(5.0, valueArray());
    List<Double> actualList = Data.multiply(5.0, valueList());
    testArrayAndList(expectArray, actualArray, actualList);
    // 2D
    double[][] d2_expect = { expectArray, expectArray };
    double[][] d2_input = { valueArray(), valueArray() };
    double[][] d2_actual = Data.multiply(5.0, d2_input);
    for (int i = 0; i < d2_expect.length; i++) {
      assertArrayEquals(d2_expect[i], d2_actual[i], 0.0);
    }
    // 3D
    double[][][] d3_expect = { { expectArray, expectArray }, { expectArray, expectArray } };
    double[][][] d3_input = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_actual = Data.multiply(5.0, d3_input);
    for (int i = 0; i < d3_expect.length; i++) {
      for (int j = 0; j < d3_expect[1].length; j++) {
        assertArrayEquals(d3_expect[i][j], d3_actual[i][j], 0.0);
      }
    }
  }

  @Test
  public final void testMultiplyArrays() {
    // 1D array and list
    double[] expectArray = { 1.0, 100.0, 10000.0 };
    double[] actualArray = Data.multiply(valueArray(), valueArray());
    List<Double> actualList = Data.multiply(valueList(), valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testMultiplyLists1D_IAE() {
    Data.multiply(valueList(), new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testMultiplyArrays1D_IAE() {
    Data.multiply(valueArray(), new double[0]);
  }

  @Test
  public final void testDivideArrays() {
    // 1D array and list
    double[] expectArray = { 1.0, 1.0, 1.0 };
    double[] actualArray = Data.divide(valueArray(), valueArray());
    List<Double> actualList = Data.divide(valueList(), valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testDivideLists1D_IAE() {
    Data.divide(valueList(), new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testDivideArrays1D_IAE() {
    Data.divide(valueArray(), new double[0]);
  }

  @Test
  public final void testAbs() {
    double[] expectArray = valueArray();
    double[] absArray = Data.multiply(-1, valueArray());
    List<Double> absList = Data.multiply(-1, valueList());
    double[] actualArray = Data.abs(absArray);
    List<Double> actualList = Data.abs(absList);
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testExp() {
    double[] expectArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectArray[i] = Math.exp(VALUES[i]);
    }
    double[] actualArray = Data.exp(valueArray());
    List<Double> actualList = Data.exp(valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testLn() {
    double[] expectArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectArray[i] = Math.log(VALUES[i]);
    }
    double[] actualArray = Data.ln(valueArray());
    List<Double> actualList = Data.ln(valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testPow10() {
    double[] expectArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectArray[i] = Math.pow(10, VALUES[i]);
    }
    double[] actualArray = Data.pow10(valueArray());
    List<Double> actualList = Data.pow10(valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testLog() {
    double[] expectArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectArray[i] = Math.log10(VALUES[i]);
    }
    double[] actualArray = Data.log(valueArray());
    List<Double> actualList = Data.log(valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testFlip() {
    double[] expectArray = { -1.0, -10.0, -100.0 };
    double[] actualArray = Data.flip(valueArray());
    List<Double> actualList = Data.flip(valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testSum() {
    double expect = 111.0;
    assertEquals(expect, Data.sum(valueArray()), 0.0);
    assertEquals(expect, Data.sum(valueList()), 0.0);
  }

  @Test
  public final void testCollapse() {
    // 2D
    double[] d2_expect = { 111.0, 111.0 };
    double[][] d2_input = { valueArray(), valueArray() };
    double[] d2_actual = Data.collapse(d2_input);
    assertArrayEquals(d2_expect, d2_actual, 0.0);
    // 3D
    double[][] d3_expect = { { 111.0, 111.0 }, { 111.0, 111.0 } };
    double[][][] d3_input = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][] d3_actual = Data.collapse(d3_input);
    for (int i = 0; i < d3_expect.length; i++) {
      assertArrayEquals(d3_expect[i], d3_actual[i], 0.0);
    }
  }

  @Test
  public final void testTransform() {
    DoubleUnaryOperator function = (x) -> { return x + 1; };
    
    double[] expectArray = { 2.0, 11.0, 101.0 };
    double[] actualArray = Data.transform(function, valueArray());
    List<Double> actualList = Data.transform(function, valueList());
    testArrayAndList(expectArray, actualArray, actualList);
  }
  
  @Test
  public final void testTransformRange() {
    DoubleUnaryOperator function = (x) -> { return x + 1; };
    
    Range<Integer> range = Range.closed(0, 1);
    double[] expect = { 2.0, 11.0, 100.0 };
    double[] actual1 = Data.transform(range, function, valueArray());
    double[] actual2 = Data.transform(0, 2, function, valueArray());
    
    assertArrayEquals(expect, actual1, 0.0);
    assertArrayEquals(expect, actual2, 0.0);
  }
  
  @Test
  public final void testTransformIAE() {
    exception.expect(IllegalArgumentException.class);
 
    Range<Integer> range = Range.openClosed(0, 0);
    Data.transform(range, (x) -> { return x; }, valueArray());
  }
  
  @Test
  public final void testTransformIAE2() {
    exception.expect(IllegalArgumentException.class);
    
    Range<Integer> range = Range.atMost(1);
    Data.transform(range, (x) -> { return x; }, valueArray());
  }
  
  @Test
  public final void testTransformNPE() {
    exception.expect(NullPointerException.class);
    
    Range<Integer> range = Range.closed(0, 1);
    Data.transform(range, null, valueArray());
  }
  
  @Test
  public final void testNormalize() {
    double[] expectArray = { 0.2, 0.3, 0.5 };
    double[] inputArray = { 20, 30, 50 };
    List<Double> inputList = Doubles.asList(Arrays.copyOf(inputArray, inputArray.length));
    double[] actualArray = Data.normalize(inputArray);
    List<Double> actualList = Data.normalize(inputList);
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testRound() {
    double[] expectArray = { 0.23, 1.32 };
    double[] inputArray = { 0.23449999, 1.3150001 };
    List<Double> inputList = Doubles.asList(Arrays.copyOf(inputArray, inputArray.length));
    double[] actualArray = Data.round(2, inputArray);
    List<Double> actualList = Data.round(2, inputList);
    testArrayAndList(expectArray, actualArray, actualList);
  }

  @Test
  public final void testPositivize() {
    double[] empty = {};
    assertArrayEquals(empty, Data.positivize(empty), 0.0);
    double[] values = valueArray();
    assertArrayEquals(values, Data.positivize(values), 0.0);
    double[] expect = { 99.0, 90.0, 0.0 };
    double[] actual = Data.positivize(Data.flip(values));
    assertArrayEquals(expect, actual, 0.0);
  }

  @Test
  public final void testDiff() {
    double[] increasing_dupes = { -10, -1, 0, 0, 1, 10 };
    double[] increasing_nodupes = { -10, -1, 0, 1, 10 };
    double[] decreasing_dupes = { 10, 1, 0, 0, -1, -10 };
    double[] decreasing_nodupes = { 10, 1, 0, -1, -10 };
    double[] expect = new double[] { 9, 1, 0, 1, 9 };
    assertArrayEquals(expect, Data.diff(increasing_dupes), 0.0);
    assertArrayEquals(Data.flip(expect), Data.diff(decreasing_dupes), 0.0);
    expect = new double[] { 9, 1, 1, 9 };
    assertArrayEquals(expect, Data.diff(increasing_nodupes), 0.0);
    assertArrayEquals(Data.flip(expect), Data.diff(decreasing_nodupes), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testDiff_IAE() {
    Data.diff(new double[1]);
  }

  @Test
  public final void testPercentDiff() {
    assertEquals(5.0, Data.percentDiff(95.0, 100.0), 0.0);
    assertEquals(5.0, Data.percentDiff(105.0, 100.0), 0.0);
    assertEquals(0.0, Data.percentDiff(0.0, 0.0), 0.0);
    assertEquals(Double.POSITIVE_INFINITY, Data.percentDiff(1.0, 0.0), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testPercentDiffTest_IAE() {
    Data.percentDiff(Double.NaN, 1.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testPercentDiffTarget_IAE() {
    Data.percentDiff(1.0, Double.NaN);
  }

  /* * * * * * * * * * * * * * STATE * * * * * * * * * * * * * */

  @Test
  public final void testState() {
    // isPositiveAndReal
    assertTrue(Data.isPositiveAndReal(10.0));
    assertFalse(Data.isPositiveAndReal(0.0));
    assertFalse(Data.isPositiveAndReal(Double.POSITIVE_INFINITY));
    assertFalse(Data.isPositiveAndReal(Double.NaN));
    // arePositiveAndReal
    assertTrue(Data.arePositiveAndReal(valueArray()));
    assertFalse(Data.arePositiveAndReal(Data.flip(valueArray())));
    assertTrue(Data.arePositiveAndReal(valueList()));
    assertFalse(Data.arePositiveAndReal(Data.flip(valueList())));
    assertFalse(Data.arePositiveAndReal(0));
    // isPositiveAndRealOrZero
    assertTrue(Data.isPositiveAndRealOrZero(10.0));
    assertTrue(Data.isPositiveAndRealOrZero(0.0));
    assertFalse(Data.isPositiveAndRealOrZero(Double.POSITIVE_INFINITY));
    assertFalse(Data.isPositiveAndRealOrZero(Double.NaN));
    // arePositiveAndRealOrZero
    assertTrue(Data.arePositiveAndRealOrZero(valueArray()));
    assertFalse(Data.arePositiveAndRealOrZero(Data.flip(valueArray())));
    assertTrue(Data.arePositiveAndRealOrZero(valueList()));
    assertFalse(Data.arePositiveAndRealOrZero(Data.flip(valueList())));
    assertTrue(Data.arePositiveAndRealOrZero(0));
    // areZeroValued
    assertTrue(Data.areZeroValued(new double[] { 0, 0 }));
    assertFalse(Data.areZeroValued(new double[] { 0, 1 }));
    assertTrue(Data.areZeroValued(Lists.<Double> newArrayList(0.0, 0.0)));
    assertFalse(Data.areZeroValued(Lists.<Double> newArrayList(0.0, 1.0)));
    // areMonotonic
    double[] increasing_dupes = { -10, -1, 0, 0, 1, 10 };
    double[] increasing_nodupes = { -10, -1, 0, 1, 10 };
    double[] increasing_bad = { -10, -1, 0, -1, -10 };
    double[] decreasing_dupes = { 10, 1, 0, 0, -1, -10 };
    assertTrue(Data.areMonotonic(true, false, increasing_dupes));
    assertFalse(Data.areMonotonic(true, true, increasing_dupes));
    assertTrue(Data.areMonotonic(true, true, increasing_nodupes));
    assertFalse(Data.areMonotonic(true, false, increasing_bad));
    assertTrue(Data.areMonotonic(false, false, decreasing_dupes));
    assertFalse(Data.areMonotonic(false, true, decreasing_dupes));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testArePositiveAndRealArray_IAE() {
    Data.arePositiveAndReal();
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testArePositiveAndRealList_IAE() {
    Data.arePositiveAndReal(new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testArePositiveAndRealOrZeroArray_IAE() {
    Data.arePositiveAndReal();
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testArePositiveAndRealOrZeroList_IAE() {
    Data.arePositiveAndReal(new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAreFiniteArray_IAE() {
    Data.arePositiveAndReal();
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAreFiniteList_IAE() {
    Data.arePositiveAndReal(new ArrayList<Double>());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAreZeroValuedArray_IAE() {
    Data.areZeroValued();
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testAreZeroValuedList_IAE() {
    Data.areZeroValued(new ArrayList<Double>());
  }

  /* * * * * * * * * * * * PRECONDITIONS * * * * * * * * * * * */

  @Test
  public final void testCheckDelta() {
    assertEquals(2.0, Data.checkDelta(0.0, 10.0, 2.0), 0.0);
    assertEquals(0.0, Data.checkDelta(10.0, 10.0, 0.0), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDeltaMinOverMax_IAE() {
    Data.checkDelta(10.0, 0.0, 2.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDeltaNegativeDelta_IAE() {
    Data.checkDelta(0.0, 10.0, -2.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDeltaZeroDelta_IAE() {
    Data.checkDelta(0.0, 10.0, 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDeltaSize_IAE() {
    Data.checkDelta(0.0, 10.0, 11.0);
  }

  @Test
  public final void testCheckFinite() {
    // also tests checkFinite(double)
    double[] expectArray = { 5.0, 2.0 };
    assertArrayEquals(expectArray, Data.checkFinite(expectArray), 0.0);
    assertSame(expectArray, Data.checkFinite(expectArray));
    List<Double> expectCollect = Doubles.asList(expectArray);
    assertEquals(expectCollect, Data.checkFinite(expectCollect));
    assertSame(expectCollect, Data.checkFinite(expectCollect));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckFiniteArray_IAE() {
    Data.checkFinite(new double[] { 0, Double.NaN });
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckFiniteCollect_IAE() {
    Data.checkFinite(Doubles.asList(0, Double.NEGATIVE_INFINITY));
  }

  @Test
  public final void testCheckInRange() {
    // also tests checkInRange(double)
    double[] expectArray = { 5.0, 2.0 };
    Range<Double> r = Range.open(0.0, 10.0);
    assertArrayEquals(expectArray, Data.checkInRange(r, expectArray), 0.0);
    assertSame(expectArray, Data.checkInRange(r, expectArray));
    List<Double> expectCollect = Doubles.asList(expectArray);
    assertEquals(expectCollect, Data.checkInRange(r, expectCollect));
    assertSame(expectCollect, Data.checkInRange(r, expectCollect));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInRangeArray_IAE() {
    Range<Double> r = Range.open(0.0, 10.0);
    Data.checkInRange(r, new double[] { -1.0 });
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInRangeCollect_IAE() {
    Range<Double> r = Range.open(0.0, 10.0);
    Data.checkInRange(r, Doubles.asList(-1.0));
  }

  /*
   * checkSize overloads are checked via operator tests
   */

  @Test
  public final void testCheckWeight() {
    assertEquals(0.5, Data.checkWeight(0.5), 0.0);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public final void testCheckWeight_IAE() {
    Data.checkWeight(0.0);
  }

  @Test
  public final void testCheckWeights() {
    double[] wtArray = { 0.4, 0.6001 };
    List<Double> wtList = Doubles.asList(wtArray);
    assertEquals(wtList, Data.checkWeights(wtList));
    assertSame(wtList, Data.checkWeights(wtList));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckWeightsBadHi_IAE() {
    double[] wtArrayBadValueHi = { 1.0001 };
    Data.checkWeights(Doubles.asList(wtArrayBadValueHi));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckWeightsBadLo_IAE() {
    double[] wtArrayBadValueLo = { 0.0 };
    Data.checkWeights(Doubles.asList(wtArrayBadValueLo));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckWeightsBadSum_IAE() {
    double[] wtArrayBadSum = { 0.4, 0.6002 };
    Data.checkWeights(Doubles.asList(wtArrayBadSum));
  }

  /* * * * * * * * 2D & 3D ARRAYS EXTENSIONS * * * * * * * * */

  @Test
  public final void testCopyOf() {
    // 2D
    double[][] expect2D = { valueArray(), valueArray(), {}, { 1.0, Double.NaN } };
    double[][] actual2D = Data.copyOf(expect2D);
    assertNotSame(expect2D, actual2D);
    for (int i = 0; i < expect2D.length; i++) {
      assertArrayEquals(expect2D[i], actual2D[i], 0.0);
    }
    // 3D
    double[][][] expect3D = {
        { valueArray(), valueArray() },
        { valueArray() },
        { {}, { 1.0, Double.NaN } } };
    double[][][] actual3D = Data.copyOf(expect3D);
    for (int i = 0; i < expect3D.length; i++) {
      for (int j = 0; j < expect3D[i].length; j++) {
        assertArrayEquals(expect3D[i][j], actual3D[i][j], 0.0);
      }
    }
  }

  @Test
  public final void testToString() {
    // 2D
    double[][] expect2D = { valueArray(), valueArray(), {}, { 1.0, Double.NaN } };
    String expect2Dstr = "[[1.0, 10.0, 100.0],\n" +
        " [1.0, 10.0, 100.0],\n" +
        " [],\n" +
        " [1.0, NaN]]";
    assertEquals(expect2Dstr, Data.toString(expect2D));
    // 3D
    double[][][] expect3D = {
        { valueArray(), valueArray() },
        { valueArray() },
        { {}, { 1.0, Double.NaN } } };
    String expect3Dstr = "[[[1.0, 10.0, 100.0],\n" +
        "  [1.0, 10.0, 100.0]],\n" +
        " [[1.0, 10.0, 100.0]],\n" +
        " [[],\n" +
        "  [1.0, NaN]]]";
    assertEquals(expect3Dstr, Data.toString(expect3D));
  }

}
