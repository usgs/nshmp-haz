package gov.usgs.earthquake.nshmp.tree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.data.Data;

/**
 * Logic tree interface that supports the iteration and sampling of individual
 * branches.
 * 
 * @author Brandon Clayton
 */
public interface LogicTree<T> extends Iterable<Branch<T>> {

  /*
   * TODO: For further consideration: serialization=, consider adding
   * LogicTree.asGraph(), see Guava graph classes
   */

  /**
   * Return a logic tree branch corresponding to the supplied probability.
   * 
   * <p><b>Note:</b> this method does not check that {@code 0.0 ≤ p < 1.0}.
   * 
   * @param probability in the range [0..1)
   */
  Branch<T> sample(double probability);

  /**
   * Return an immutable list of logic tree branches corresponding to the
   * supplied probabilities.
   * 
   * <p> <b>Note:</b> this method does not check that each
   * {@code 0.0 ≤ p < 1.0}.
   * 
   * @param probabilities in the range [0..1)
   */
  List<Branch<T>> sample(double[] probabilities);

  /** Return a new logic tree builder. */
  static <T> Builder<T> builder() {
    return new Builder<T>();
  }

  /** A logic tree builder. */
  public static class Builder<T> {
    private ImmutableList.Builder<Branch<T>> branches;
    private boolean built;

    private Builder() {
      branches = ImmutableList.builder();
      built = false;
    }

    /**
     * Add a {@code Branch} to the {@code LogicTree}.
     * 
     * @param id the branch identifier
     * @param weight the branch weight
     * @param value the branch value
     * @return this builder
     */
    public Builder<T> add(String id, double weight, T value) {
      branches.add(new RegularBranch<T>(
          checkNotNull(id),
          weight,
          checkNotNull(value)));
      return this;
    }

    /** Return a new {@code LogicTree}. */
    public LogicTree<T> build() {
      checkState(!built);
      built = true;

      ImmutableList<Branch<T>> branches = this.branches.build();
      checkState(!branches.isEmpty());

      double[] weights = branches.stream()
          .mapToDouble(Branch::weight)
          .toArray();
      Data.checkWeights(weights, false);
      Data.cumulate(weights);

      return new RegularLogicTree<T>(branches, weights);
    }
  }
}
