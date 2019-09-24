package gov.usgs.earthquake.nshmp.www.meta;

@SuppressWarnings({ "javadoc", "unused" })
public final class DoubleParameter {

  private final String label;
  private final ParamType type;
  private final Values values;

  public DoubleParameter(String label, ParamType type, double min, double max) {
    this.label = label;
    this.type = type;
    this.values = new Values(min, max);
  }

  private final static class Values {

    final double minimum;
    final double maximum;

    Values(double min, double max) {
      this.minimum = min;
      this.maximum = max;
    }
  }

}
