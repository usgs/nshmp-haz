package gov.usgs.earthquake.nshmp.data;

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * An interface representing an immutable wrapper around an array of primitive
 * {@code double} values. Use factory constructors or builders to create
 * instances of this class.
 * 
 * <p>In addition to {@link #iterator()} returning the 
 * {@code double} primitive specialization {@link PrimitiveIterator.OfDouble} implementations of this interface
 * provide streaming support via {@link #stream()} and {@link parallelStream()},
 * both of which return the {@code double} primitive specialization,
 * {@link DoubleStream}. Note that traditional iteration will incur additional
 * autoboxing overhead.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
 */
interface DataArray extends Iterable<Double> {

  /*
   * Developer notes:
   * 
   * Currently Iterable is implemented in favor of the more heavyweight List or
   * Collection interfaces to support traditional for-each loops, however the
   * iterator returned is an updated spliterator based double specialization.
   * 
   */

  // Implementation notes:
  //
  // Understand why we want to override Iterable.iterator() and
  // Iterable.spliterator()

  // Extra Credit: add method (ideally static) to create a collector to use
  // with Stream.collect().

  // ---------------------------------
  // Implementation guidelines:

  // Put basic implementation, RegularDataAray, in its own class.

  // Classes to take note of:
  //
  // java.util.Arrays
  // java.util.Spliterator
  // java.util.Spliterators
  // java.util.stream.StreamSupport
  
  
  // Static factory methods:
  //
  // DataArray copyOf(double... data)
  // DataArray copyOf(Iterable<Double> data) {
  // DataArray.Builder builderWithSize(int size)
  // DataArray.Builder builderWithData(double... data)
  // DataArray.Builder builderWithData(Iterable<Double> data)
  
  public static DataArray copyOf(double... data) {
    return builderWithData(data).build();
  }
  
  public static DataArray copyOf(Iterable<Double> data) {
    return builderWidthData(data).build();
  }
  
  public static Builder builderWithSize(int size) {
    return new Builder(size);
  }
  
  public static Builder builderWithData(double... data) {
    return new Builder(data);
  }
  
  public static Builder builderWidthData(Iterable<Double> data) {
    return new Builder(data);
  }
  
  // Interface methods to override:
  // @Override PrimitiveIterator.OfDouble iterator(); (see Spliterators)
  // @Override Spliterator.OfDouble spliterator();
  // -- (in implementation, get from Arrays, not Spliterators)

  @Override
  default public PrimitiveIterator.OfDouble iterator() {
    return Spliterators.iterator(spliterator());
  }
  
  @Override 
  public Spliterator.OfDouble spliterator();
  
  // Instance methods to declare:
  //
  // DoubleStream stream();
  // DoubleStream parallelStream();
  // -- (use StreamSupport for the above methods)
  // double get(int index);
  // int size();

  default public DoubleStream stream() {
    Boolean isParallel = false;
    return StreamSupport.doubleStream(spliterator(), isParallel);
  }
   
  default public DoubleStream parallelStream() {
    Boolean isParallel = true;
    return StreamSupport.doubleStream(spliterator(), isParallel);
  }
  
  public double get(int index);
  
  public int size();
  
  // Nested builder class instance methods:
  //
  // Builder set(int index, double value)
  // Builder transform(DoubleUnaryOperator function)
  // Builder transformRange(Range<Integer> range, DoubleUnaryOperator function)
  
  public static class Builder {
    private double[] data;
    private DoubleUnaryOperator transformFunction = (x) -> { return x; };
    private DoubleUnaryOperator transformRangeFunction = (x) -> { return x; };
    private Range<Integer> range;
    private Boolean transform = false;
    private Boolean transformRange = false;
    
    private Builder(int size) {
      checkArgument(size >= 0);
      data = new double[size];
    }
   
    private Builder(double... data) {
      for (int index = 0; index < data.length; index++) {
        set(index, data[index]);
      }
    }
    
    private Builder(Iterable<Double> data) {
      int count = 0;
      for (double datum : data) {
        set(count, datum);
        count++;
      }
    }
    
    public DataArray build() {
      data = transformData();
      transformDataRange();
      
      DataArray dataArray = new RegularDataArray(data);
      return dataArray;
    }
    
    public Builder set(int index, double value) {
      checkArgument(index >= 0);
      data[index] = value;
      return this;
    }
    
    public Builder transform(DoubleUnaryOperator function) {
      transform = true;
      transformFunction = function;
      return this;
    }
    
    public Builder transformRange(Range<Integer> range, DoubleUnaryOperator function) {
      transformRange = true;
      transformRangeFunction = function;
      this.range = range;
      return this;
    }
    
    private double[] transformData() {
      return !transform ? data : DoubleStream.of(data).map(transformFunction).toArray();
    }
    
    private void transformDataRange() {
      if (!transformRange) return;
      
      ContiguousSet<Integer> rangeSet = ContiguousSet.create(range, DiscreteDomain.integers());
      rangeSet.stream()
          .forEach((rangeIndex) -> {  
            data[rangeIndex] = transformRangeFunction.applyAsDouble(data[rangeIndex]);
          });
    }
    
  }

}
