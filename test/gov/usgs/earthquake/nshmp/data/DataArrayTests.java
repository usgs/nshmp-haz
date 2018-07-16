package gov.usgs.earthquake.nshmp.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.Range;

@SuppressWarnings("javadoc")
public class DataArrayTests {

  @Rule 
  public ExpectedException exception = ExpectedException.none();
  
  @Test
  public void copyOf() {
    DataArray dataArray1 = DataArray.copyOf(new double[] {1.0, 2.0, 3.0});
    assertEquals(dataArray1.get(0), 1.0, 0.0);
    assertEquals(dataArray1.get(1), 2.0, 0.0);
    assertEquals(dataArray1.get(2), 3.0, 0.0);
    
    DataArray dataArray2 = DataArray.copyOf(Arrays.asList(1.0, 2.0, 3.0));
    assertEquals(dataArray2.get(0), 1.0, 0.0);
    assertEquals(dataArray2.get(1), 2.0, 0.0);
    assertEquals(dataArray2.get(2), 3.0, 0.0);
  }
  
  @Test
  public void builderIAE1() {
    exception.expect(IllegalArgumentException.class);
    DataArray.builderWithSize(-1);
  }

  @Test
  public void builderIAE2() {
    exception.expect(ArrayIndexOutOfBoundsException.class);
    DataArray.builderWithSize(2).set(2, 3.0);
  }
  
  @Test 
  public void builderIAE3() {
    exception.expect(IllegalArgumentException.class);
    DataArray.builderWithSize(2).set(-1, 3.0);
  }
  
  @Test
  public void toArray() {
    double[] expect = { 1.0, 10.0, 100.0, 1000.0 };
    double[] actual = DataArray.builderWithData(expect).build().toArray();
    
    assertArrayEquals(expect, actual, 0.0);
  }
  
  @Test
  public void transform() {
    double[] data = { 1.0, 10.0, 100.0, 1000.0 };
    double[] expect = { 4.0, 40.0, 400.0, 4000.0};
    DoubleUnaryOperator function = (x) -> { return 2 * x; };
    
    double[] actual = DataArray.builderWithData(data)
        .transform(function)
        .transform(function)
        .build()
        .toArray();
   
    assertArrayEquals(expect, actual, 0.0);
  }
  
  @Test 
  public void transformRange() {
    int lower = 1;
    int upper = 3;
    Range<Integer> range = Range.closedOpen(lower, upper);
    
    double[] data = { 1.0, 10.0, 100.0, 1000.0 };
    double[] expect = { 2.0, 40.0, 400.0, 2000.0 };
    DoubleUnaryOperator function = (x) -> { return 2 * x; };
    
    double[] actual1 = DataArray.builderWithData(data)
        .transform(function)
        .transformRange(range, function)
        .build()
        .toArray();
   
    double[] actual2 = DataArray.builderWithData(data)
        .transform(function)
        .transformRange(lower, upper, function)
        .build()
        .toArray();
   
    assertArrayEquals(expect, actual1, 0.0);
    assertArrayEquals(expect, actual2, 0.0);
  }

  @Test
  public void values() {
    DataArray da = DataArray.builderWithSize(2)
        .set(0, 1)
        .set(1, 2)
        .build();
    assertEquals(da.get(0), 1.0, 0.0);
    assertEquals(da.get(1), 2.0, 0.0);
  }
  
  @Test
  public void size() {
    DataArray da = DataArray.builderWithSize(2)
        .set(0, 1)
        .set(1, 2)
        .build();
    assertEquals(da.size(), 2);
  }

}
