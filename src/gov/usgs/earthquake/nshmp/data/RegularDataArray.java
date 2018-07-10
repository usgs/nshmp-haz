package gov.usgs.earthquake.nshmp.data;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

public class RegularDataArray implements DataArray {

  final double[] data;
  
  public RegularDataArray(double[] data) {
    this.data = data;
  }
  
//  @Override
//  public PrimitiveIterator.OfDouble iterator() {
//    //DoubleStream.of(data).iterator(); does this autobox?
//    return Spliterators.iterator(spliterator());
//  }

  @Override
  public Spliterator.OfDouble spliterator() {
    //DoubleStream.of(data).spliterator(); does this autobox?
    return Arrays.spliterator(data);
  }

//  @Override
//  public DoubleStream stream() {
//    // DoubleStream.of(data); ?
//    // Arrays.stream(data); ?
//    return StreamSupport.doubleStream(spliterator(), false);
//  }

//  @Override
//  public DoubleStream parallelStream() {
//    // DoubleStream.of(data).parallel(); ?
//    return StreamSupport.doubleStream(spliterator(), true);
//  }

  @Override
  public double get(int index) {
    return data[index];
  }

  @Override
  public int size() {
    return data.length;
  }

}
