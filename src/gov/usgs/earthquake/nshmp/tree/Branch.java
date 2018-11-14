package gov.usgs.earthquake.nshmp.tree;

/**
 * A logic tree branch.
 * 
 * @author Brandon Clayton
 */
public interface Branch<T> {

  /** The branch id. */
  String id();

  /** The branch value. */
  T value();

  /** The branch weight. */
  double weight();

}
