package gov.usgs.earthquake.nshmp.tree;

/**
 * Basic logic tree branch implementation.
 *
 * @author Brandon Clayton
 */
class RegularBranch<T> implements Branch<T> {
  private final String id;
  private final double weight;
  private final T value;

  RegularBranch(String id, double weight, T value) {
    this.id = id;
    this.weight = weight;
    this.value = value;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public T value() {
    return value;
  }

  @Override
  public double weight() {
    return weight;
  }

}
