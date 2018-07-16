package gov.usgs.earthquake.nshmp.data;

import java.util.Arrays;
import java.util.Spliterator;

public class RegularDataArray implements DataArray {

  private final double[] data;
  
  RegularDataArray(double[] data) {
    this.data = data;
  }
  
  @Override
  public Spliterator.OfDouble spliterator() {
    return Arrays.spliterator(data);
  }

  @Override
  public double get(int index) {
    return data[index];
  }

  @Override
  public int size() {
    return data.length;
  }
  
  @Override 
  public double[] toArray() {
    return Arrays.copyOf(data, data.length);
  }

}
