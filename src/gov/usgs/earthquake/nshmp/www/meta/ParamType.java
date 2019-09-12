package gov.usgs.earthquake.nshmp.www.meta;

@SuppressWarnings("javadoc")
public enum ParamType {
  INTEGER,
  NUMBER,
  STRING;

  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
