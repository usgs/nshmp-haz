package org.opensha2.data;

import static java.lang.Math.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.primitives.Doubles;

@SuppressWarnings("javadoc")
public final class DataUtilsTests {

  private static final double[] VALUES = { 1.0, 10.0, 100.0 };

  private static double[] valueArray() {
    return Arrays.copyOf(VALUES, VALUES.length);
  }

  private static List<Double> valueList() {
    return Doubles.asList(valueArray());
  }

  static final void testTransform(
      double[] expectedArray,
      double[] actualArray,
      List<Double> actualList) {

    assertArrayEquals(expectedArray, actualArray, 0.0);
    List<Double> expectedList = Doubles.asList(expectedArray);
    assertEquals(expectedList, actualList);
  }

  @Test
  public final void testAdd() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // add term
    expectedArray = new double[] { 2.0, 11.0, 101.0 };
    actualArray = Data.add(1.0, valueArray());
    actualList = Data.add(1.0, valueList());
    testTransform(expectedArray, actualArray, actualList);

    // add arrays
    expectedArray = new double[] { 2.0, 20.0, 200.0 };
    actualArray = Data.add(valueArray(), valueArray());
    actualList = Data.add(valueList(), valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testMultidimensionalAdd() {

    double[][] d2_ex = { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } };
    double[][][] d3_ex = {
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } },
        { { 2.0, 20.0, 200.0 }, { 2.0, 20.0, 200.0 } } };

    double[][] d2_1 = { valueArray(), valueArray() };
    double[][] d2_2 = { valueArray(), valueArray() };
    double[][][] d3_1 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };
    double[][][] d3_2 = { { valueArray(), valueArray() }, { valueArray(), valueArray() } };

    double[][] d2_actual = Data.add(d2_1, d2_2);
    for (int i = 0; i < d2_ex.length; i++) {
      assertArrayEquals(d2_ex[i], d2_actual[i], 0.0);
    }

    double[][][] d3_actual = Data.add(d3_1, d3_2);
    for (int i = 0; i < d3_ex.length; i++) {
      for (int j = 0; j < d3_ex[1].length; j++) {
        assertArrayEquals(d3_ex[i][j], d3_actual[i][j], 0.0);
      }
    }

  }

  @Test
  public final void testSubtract() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // subtract arrays
    expectedArray = new double[] { 0.0, 0.0, 0.0 };
    actualArray = Data.subtract(valueArray(), valueArray());
    actualList = Data.subtract(valueList(), valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testMultiply() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // multiply term
    expectedArray = new double[] { 5.0, 50.0, 500.0 };
    actualArray = Data.multiply(5.0, valueArray());
    actualList = Data.multiply(5.0, valueList());
    testTransform(expectedArray, actualArray, actualList);

    // multiply arrays
    expectedArray = new double[] { 1.0, 100.0, 10000.0 };
    actualArray = Data.multiply(valueArray(), valueArray());
    actualList = Data.multiply(valueList(), valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testDivide() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // divide arrays
    expectedArray = new double[] { 1.0, 1.0, 1.0 };
    actualArray = Data.divide(valueArray(), valueArray());
    actualList = Data.divide(valueList(), valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testAbs() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // abs array
    expectedArray = valueArray();
    double[] absArray = Data.multiply(-1, valueArray());
    List<Double> absList = Data.multiply(-1, valueList());
    actualArray = Data.abs(absArray);
    actualList = Data.abs(absList);
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testExp() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // exp array
    expectedArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectedArray[i] = Math.exp(VALUES[i]);
    }
    actualArray = Data.exp(valueArray());
    actualList = Data.exp(valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testLn() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // ln array
    expectedArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectedArray[i] = Math.log(VALUES[i]);
    }
    actualArray = Data.ln(valueArray());
    actualList = Data.ln(valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testPow10() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // pow10 array
    expectedArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectedArray[i] = Math.pow(10, VALUES[i]);
    }
    actualArray = Data.pow10(valueArray());
    actualList = Data.pow10(valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testLog() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // log10 array
    expectedArray = new double[3];
    for (int i = 0; i < 3; i++) {
      expectedArray[i] = Math.log10(VALUES[i]);
    }
    actualArray = Data.log(valueArray());
    actualList = Data.log(valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testFlip() {
    double[] expectedArray, actualArray;
    List<Double> actualList;

    // flip arrays
    expectedArray = new double[] { -1.0, -10.0, -100.0 };
    actualArray = Data.flip(valueArray());
    actualList = Data.flip(valueList());
    testTransform(expectedArray, actualArray, actualList);
  }

  @Test
  public final void testSum() {
    double expected = 111.0;
    assertEquals(expected, Data.sum(valueArray()), 0.0);
    assertEquals(expected, Data.sum(valueList()), 0.0);
  }

}
