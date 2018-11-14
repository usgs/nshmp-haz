package gov.usgs.earthquake.nshmp.tree;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * Basic logic tree implementation.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
 */
class RegularLogicTree<T> implements LogicTree<T> {

  private final List<Branch<T>> branches;
  private final double[] cumulativeWeights;

  RegularLogicTree(List<Branch<T>> branches, double[] cumulativeWeights) {
    this.branches = branches;
    this.cumulativeWeights = cumulativeWeights;
  }

  @Override
  public Branch<T> sample(double probability) {
    for (int i = 0; i < cumulativeWeights.length; i++) {
      if (probability < cumulativeWeights[i]) {
        return branches.get(i);
      }
    }
    return branches.get(cumulativeWeights.length - 1);
  }

  @Override
  public List<Branch<T>> sample(double[] probabilities) {
    ImmutableList.Builder<Branch<T>> samples =
        ImmutableList.builderWithExpectedSize(probabilities.length);
    for (double probability : probabilities) {
      samples.add(sample(probability));
    }
    return samples.build();
  }

  @Override
  public Iterator<Branch<T>> iterator() {
    return branches.iterator();
  }

}
