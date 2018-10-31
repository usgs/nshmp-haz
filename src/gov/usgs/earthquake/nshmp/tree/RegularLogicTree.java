package gov.usgs.earthquake.nshmp.tree;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * Regular modeling logic tree with a single node and multiple {@code Branch}es.
 *
 * <p> Use {@link LogicTree#builder()} to build a {@code LogicTree}.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
 * @param <T> The type of {@link Branch#value()}
 */
public class RegularLogicTree<T> implements LogicTree<T> {

  /*
   * TODO: For further consideration: serialization=, consider adding
   * LogicTree.asGraph(), see Guava graph classes
   */

  /* {@code Branch}es of the {@code LogicTree}. */
  private final List<Branch<T>> branches;

  /* Cumulative weights of the {@code Branch} weights */
  private final double[] cumulativeWeights;

  RegularLogicTree(List<Branch<T>> branches, double[] cumulativeWeights) {
    this.branches = branches;
    this.cumulativeWeights = cumulativeWeights;
  }

  @Override
  public Branch<T> sample(double sample) {
    int index = -1;

    for (double weight : cumulativeWeights) {
      index++;
      if (sample <= weight) break;
    }

    return branches.get(index);
  }

  @Override
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

}
