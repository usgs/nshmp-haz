package gov.usgs.earthquake.nshmp.tree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.data.Data;

/**
 * Modeling logic tree interface.
 * 
 * @author Brandon Clayton
 * @param <T> The type of {@link Branch#value()}
 */
public interface LogicTree<T> extends Iterable<Branch<T>> {

  /**
   * Return the {@code Branch<T>} where the given sample is <= cumulative
   * weights of the {@code Branch}es.
   * 
   * <p> Note: The sample weight is not checked and the last {@code Branch<T>}
   * is returned if the sample weight is not within [0.0, 1.0].
   * 
   * @param sample The sample weight to compare to the cumulative weight
   */
  public Branch<T> sample(double sample);

  /**
   * Return a {@code List<Branch<T>>} (backed by {@link ImmutableList}) where
   * the given samples are <= cumulative weights of the {code Branch}es
   * 
   * <p> See {@link LogicTree#sample(double)}.
   * 
   * @param samples The sample weights to compare to the cumulative weights
   */
  public List<Branch<T>> sample(double[] samples);

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
      Data.cumulate(weights);

      built = true;

      return new RegularLogicTree<T>(branches, weights);
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
      branches.add(new RegularBranch<T>(checkNotNull(key), weight, checkNotNull(value)));
      return this;
    }
  }

}
