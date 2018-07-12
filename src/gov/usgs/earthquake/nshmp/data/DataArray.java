package gov.usgs.earthquake.nshmp.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * An interface representing an immutable wrapper around an array of primitive
 * {@code double} values. Use factory constructors or builders to create
 * instances of this class.
 * 
 * <p>In addition to {@link #iterator()} returning the 
 * {@code double} primitive specialization {@link PrimitiveIterator.OfDouble} 
 * implementations of this interface provide streaming support via 
 * {@link #stream()} and {@link parallelStream()}, both of which return 
 * the {@code double} primitive specialization, {@link DoubleStream}. 
 * Note that traditional iteration will incur additional autoboxing overhead.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
 */
public interface DataArray extends Iterable<Double> {

  /*
   * Developer notes:
   * 
   * Currently Iterable is implemented in favor of the more heavyweight List or
   * Collection interfaces to support traditional for-each loops, however the
   * iterator returned is an updated spliterator based double specialization.
   * 
   */

  /**
   * Return a {@code PrimitiveInterator.ofDouble} iterator.
   */
  @Override
  default PrimitiveIterator.OfDouble iterator() {
    return Spliterators.iterator(spliterator());
  }
  
  /**
   * Returns a {@code Spliterator.ofDouble} {@code Spliterator}.
   */
  @Override 
  Spliterator.OfDouble spliterator();

  /**
   * Return a {@code double} at {@code index}.
   * 
   * @param index of the value to get
   * @return {@code double} at position {@code index}
   */
  double get(int index);
  
  /**
   * Return the number of elements in {@code DataArray}.
   * 
   * @return {@code int} The size of {@code DataArray}
   */
  int size();

  /**
   * Returns a new, mutable copy of the {@code DataArray}'s values as
   *    a primitive {@code double[]}.
   *    
   * @return {@code double[]}
   */
  double[] toArray();
  
  /**
   * Return a parallel {@code DoubleStream} of the {@code DataArray}.
   * 
   * @return parallel {@code DoubleStream}
   */
  default DoubleStream parallelStream() {
    Boolean isParallel = true;
    return StreamSupport.doubleStream(spliterator(), isParallel);
  }
  
  /**
   * Return a sequential {@code DoubleStream} of the {@code DataArray}.
   * 
   * @return {@code DoubleStream}
   */
  default DoubleStream stream() {
    Boolean isParallel = false;
    return StreamSupport.doubleStream(spliterator(), isParallel);
  }
  
  /**
   * Create a new {@code DataArray} from the supplied {@code data}.
   * 
   * @param data to copy
   * @return New {@code DataArray}
   */
  static DataArray copyOf(double... data) {
    return builderWithData(data).build();
  }
  
  /**
   * Create a new {@code DataArray} from an {@code Iterable<Double>}.
   * 
   * @param data to copy
   * @return new {@code DataArray}
   */
  static DataArray copyOf(Iterable<Double> data) {
    return builderWithData(data).build();
  }
  
  /**
   * Return a new {@code DataArray} {@code Builder} initialized with 
   *    {@code data}.
   *    
   * @param data to copy
   * @return new {@code Builder}
   */
  static Builder builderWithData(double... data) {
    return new Builder(data);
  }
  
  /**
   * Return a new {@code DataArray} {@code Builder} initialized with
   *    {@code Iterable<Double>}.
   *    
   * @param data to copy
   * @return new {@code Builder}.
   */
  static Builder builderWithData(Iterable<Double> data) {
    if (data instanceof Collection) {
      return new Builder(Doubles.toArray((Collection<Double>) data));
    }

    double[] array = Stream.of(Iterables.toArray(data, Double.class))
          .mapToDouble(Double::doubleValue)
          .toArray();
    return new Builder(array);
  }
 
  /**
   * Return a new {@code DataArray} {@code Builder} initialized to
   *    {@code size}.
   *    
   * @param size of the backing array
   * @return new {@code Builder}
   */
  static Builder builderWithSize(int size) {
    checkArgument(size >= 0);
    return new Builder(new double[size]);
  }
  
  /**
   * A {@code DataArray} builder. Use one of the following to create a new builder:
   *    <ul> 
   *      <li> {@link DataArray#builderWithData(double...)} </li>
   *      <li> {@link DataArray#builderWithData(Iterable)} </li>
   *      <li> {@link DataArray#builderWithSize(int)} </li>
   *    </ul> 
   */
  public static class Builder {
    private double[] data;
    
    private Builder(double... data) {
      this.data = Arrays.copyOf(data, data.length); 
    }
   
    /**
     * Return a new {@code DataArray}.
     * 
     * @return {@code DataArray}
     */
    public DataArray build() {
      return new RegularDataArray(Arrays.copyOf(data, data.length));
    }
   
    /**
     * Set the {@code value} at {@code index} in the {@code DataArray}.
     * 
     * @param index to set the {@code value}
     * @param value at {@code index}
     * @return {@code Builder} to chain
     */
    public Builder set(int index, double value) {
      checkArgument(index >= 0);
      data[index] = value;
      return this;
    }
    
    /**
     * Transform the {@code DataArray} at all indices.
     * 
     * @param function to transform the {@code dataArray}
     * @return {@code Builder} to chain
     */
    public Builder transform(DoubleUnaryOperator function) {
      Data.transform(function, data);
      return this;
    }
    
    /**
     * Transform the {@code DataArray} at a specified {@code Range}.
     * 
     * @param range of indices to transform the {@code DataArray}
     * @param function to transform the {@code DataArray}
     * @return {@code Builder} to chain
     */
    public Builder transformRange(Range<Integer> range, DoubleUnaryOperator function) {
      Data.transform(range, function, data);
      return this;
    }
   
    /**
     * Transform the {@code DataArray} at a specified range, 
     *    [{@code lower}, {@code upper}).
     *    
     * @param lower inclusive index
     * @param upper exclusive index
     * @param function to apply to {@code DataArray}
     * @return {@code Builder} to chain
     */
    public Builder transformRange(int lower, int upper, DoubleUnaryOperator function) {
      Data.transform(lower, upper, function, data);
      return this;
    }
   
  }

}
