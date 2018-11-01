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

}
