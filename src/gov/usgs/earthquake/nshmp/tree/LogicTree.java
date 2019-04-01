package gov.usgs.earthquake.nshmp.tree;

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
   * developer notes:
   * 
   * For further consideration: serialization=, consider adding
   * LogicTree.asGraph(), see Guava graph classes
   * 
   * specialized trees and builders: threePointTree (THREE_POINT
   * | THREE_POINT_262) takes values (id, T, σ) | (id, double, σ)
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

  /**
   * Return a new {@code SingleBranchTree}.
   * 
   * @param id the branch identifier
   * @param value the branch value
   */
  static <T> SingleBranchTree<T> singleBranch(String id, T value) {
    return new SingleBranchTree<>(new RegularBranch<T>(id, value, 1.0));
  }

  /** Return a new logic tree builder. */
  static <T> Builder<T> builder() {
    return new Builder<T>();
  }

  /** A logic tree builder. */
  static class Builder<T> {
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
    public Builder<T> add(String id, T value, double weight) {
      branches.add(new RegularBranch<T>(id, value, weight));
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
