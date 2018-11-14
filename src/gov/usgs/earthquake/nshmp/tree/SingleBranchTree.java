package gov.usgs.earthquake.nshmp.tree;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;

/**
 * Basic logic tree with a single branch.
 * 
 * <p> Use {@link LogicTree#singleBranch(String, double, Object)} for new
 * instance.
 * 
 * @author Brandon Clayton
 */
class SingleBranchTree<T> implements LogicTree<T> {

  private final Branch<T> branch;

  SingleBranchTree(Branch<T> branch) {
    this.branch = branch;
  }

  @Override
  public Branch<T> sample(double probability) {
    return branch;
  }

  @Override
  public List<Branch<T>> sample(double[] probabilities) {
    return Collections.nCopies(probabilities.length, branch);
  }

  @Override
  public Iterator<Branch<T>> iterator() {
    return Iterators.singletonIterator(branch);
  }

}
