package gov.usgs.earthquake.nshmp.www.meta;

import java.util.Set;

@SuppressWarnings({ "javadoc", "unused" })
public final class EnumParameter<E extends Enum<E>> {

  private final String label;
  private final ParamType type;
  private final Set<E> values;

  public EnumParameter(String label, ParamType type, Set<E> values) {
    this.label = label;
    this.type = type;
    this.values = values;
  }

}
