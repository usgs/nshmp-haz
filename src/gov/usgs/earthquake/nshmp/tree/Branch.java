package gov.usgs.earthquake.nshmp.tree;

/**
 * Branch of a {@link LogicTree}.
 * 
 * @author Brandon Clayton
 * @param <T> The type of {@code Branch}
 */
public interface Branch<T> {

  /** Return the {@code Branch} identifier. */
  public String key();

  /** Return the {@code Branch} value. */
  public T value();

  /** Return the {@code Branch} weight. */
  public double weight();

}
