package gov.usgs.earthquake.nshmp.tree;

/**
 * A logic tree branch.
 * 
 * @author Brandon Clayton
 */
public interface Branch<T> {

  /** The branch id. */
  public String id();

  /** The branch value. */
  public T value();

  /** The branch weight. */
  public double weight();

}
