package gov.usgs.earthquake.nshmp.tree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Modeling logic tree container with a single node and multiple
 * {@code Branch}es.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
 * @param <T> The type of {@code Branch}
 */
public class LogicTree<T> implements Iterable<LogicTree.Branch<T>> {

  /*
   * TODO: For further consideration: serialization=, consider adding
   * LogicTree.asGraph(), see Guava graph classes
   */

  /* {@code Branch}es of the {@code LogicTree}. */
  private final List<Branch<T>> branches;

  /* Cumulative weights of the {@code Branch} weights */
  private final double[] cumulativeWeights;

  private LogicTree(List<Branch<T>> branches) {
    this.branches = branches;
    cumulativeWeights = toCumulative();
  }

  /**
   * Return the {@code Branch<T>} where the given sample is <=
   * {@link LogicTree#cumulativeWeights}.
   * 
   * <p> Note: The sample weight is not checked and the last {@code Branch<T>}
   * is returned if the sample weight is not within [0.0, 1.0].
   * 
   * @param sample The sample weight to compare to the cumulative weight
   */
  public Branch<T> sample(double sample) {
    int index = -1;

    for (double weight : cumulativeWeights) {
      index++;
      if (sample <= weight) break;
    }

    return branches.get(index);
  }

  /**
   * Return a {@code List<Branch<T>>} (backed by {@link ImmutableList}) where
   * the given samples are <= {@link LogicTree#cumulativeWeights}.
   * 
   * <p> See {@link LogicTree#sample(double)}.
   * 
   * @param samples The sample weights to compare to the cumulative weights
   */
  public List<Branch<T>> sample(double[] samples) {
    return Arrays.stream(samples)
        .mapToObj(this::sample)
        .collect(ImmutableList.toImmutableList());
  }

  /*
   * Override the iterator to ensure a pointer to the branch is returned and not
   * a copy of the branch.
   */
  @Override
  public Iterator<Branch<T>> iterator() {
    return new Iterator<Branch<T>>() {

      private int index = 0;
      private int indexEnd = branches.size();

      @Override
      public boolean hasNext() {
        return index < indexEnd;
      }

      @Override
      public Branch<T> next() {
        return branches.get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * A single branch of a {@code LogicTree}.
   * 
   * @param <T> The type of {@code Branch}
   */
  public static class Branch<T> {
    private final String key;
    private final double weight;
    private final T value;

    private Branch(String key, double weight, T value) {
      this.key = key;
      this.weight = weight;
      this.value = value;
    }

    /** Return the {@code Branch} identifier. */
    public String key() {
      return key;
    }

    /** Return the {@code Branch} value. */
    public T value() {
      return value;
    }

    /** Return the {@code Branch} weight. */
    public double weight() {
      return weight;
    }
  }

  /**
   * Return a new {@code Builder} to create a {@code LogicTree} with a
   * {@code Branch} type {@code T}.
   *
   * @param <T> The type of {@code Branch}
   */
  public static <T> Builder<T> builder() {
    return new Builder<T>();
  }

  /**
   * Build a {@code LogicTree} with a {@code Branch} type {@code T}.
   * 
   * @param <T> The type of {@code Branch}
   */
  public static class Builder<T> {
    private ImmutableList.Builder<Branch<T>> branches;
    private boolean built;

    private Builder() {
      branches = ImmutableList.builder();
      built = false;
    }

    /** Return a new {@code LogicTree}. */
    public LogicTree<T> build() {
      checkState(!built);

      ImmutableList<Branch<T>> branches = this.branches.build();
      checkState(!branches.isEmpty());

      double[] weights = branches.stream()
          .mapToDouble(Branch::weight)
          .toArray();

      Data.checkWeights(weights, false);

      built = true;

      return new LogicTree<T>(branches);
    }

    /**
     * Add a {@code Branch} to the {@code LogicTree}.
     * 
     * @param key The {@code Branch} identifier
     * @param weight The weight of the {@code Branch}
     * @param value The {@code Branch} value
     * @return The {@code Builder}
     */
    public Builder<T> add(String key, double weight, T value) {
      branches.add(new Branch<T>(checkNotNull(key), weight, checkNotNull(value)));
      return this;
    }
  }

  /* Create an array of cumulative branch weights */
  private double[] toCumulative() {
    double[] cumulativeWeights = new double[branches.size()];
    double sum = 0;
    int index = 0;

    for (Branch<T> branch : this) {
      sum += branch.weight();
      /* round to cleaner values */
      cumulativeWeights[index++] = Maths.round(sum, 8);
    }

    return cumulativeWeights;
  }

}
