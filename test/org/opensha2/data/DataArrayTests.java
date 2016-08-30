package org.opensha2.data;

import static org.junit.Assert.*;

//import org.opensha2.data.DataArray.Builder;

import org.junit.Test;

public class DataArrayTests {

  @Test
  public void copyOf() {
    DataArray da = DataArray.copyOf(new double[] {1.0, 2.0, 3.0});
    assertEquals(da.getValue(0), 1.0, 0.0);
    assertEquals(da.getValue(1), 2.0, 0.0);
    assertEquals(da.getValue(2), 3.0, 0.0);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void builderIAE1() {
    DataArray.builder(-1);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void builderIAE2() {
    DataArray.builder(2).set(2, 3.0);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void dataArrayUOE() {
    DataArray da = DataArray.builder(2).build();
    da.set(0, 1.0);
  }

  @Test
  public void values() {
    DataArray da = DataArray.builder(2)
        .set(0, 1)
        .set(1, 2)
        .build();
    assertEquals(da.get(0), 1.0, 0.0);
    assertEquals(da.get(1), 2.0, 0.0);
    assertEquals(da.getValue(0), 1.0, 0.0);
    assertEquals(da.getValue(1), 2.0, 0.0);
  }
  
  @Test
  public void size() {
    DataArray da = DataArray.builder(2)
        .set(0, 1)
        .set(1, 2)
        .build();
    assertEquals(da.size(), 2);
  }

}
