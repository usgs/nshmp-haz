package gov.usgs.earthquake.nshmp.tree;

/**
 * A single branch of a {@code LogicTree}.
 *
 * @author Brandon Clayton
 * @param <T> The type of {@code Branch}
 */
public class RegularBranch<T> implements Branch<T> {
  private final String key;
  private final double weight;
  private final T value;

  RegularBranch(String key, double weight, T value) {
    this.key = key;
    this.weight = weight;
    this.value = value;
  }

  @Override
  public String key() {
    return key;
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
