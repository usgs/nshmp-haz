package gov.usgs.earthquake.nshmp.tree;

import static com.google.common.base.Preconditions.checkNotNull;

import gov.usgs.earthquake.nshmp.data.Data;

/**
 * Basic logic tree branch implementation.
 *
 * @author Brandon Clayton
 */
class RegularBranch<T> implements Branch<T> {
  private final String id;
  private final double weight;
  private final T value;

  RegularBranch(String id, T value, double weight) {
    this.id = checkNotNull(id);
    this.weight = Data.checkWeight(weight);
    this.value = checkNotNull(value);
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
