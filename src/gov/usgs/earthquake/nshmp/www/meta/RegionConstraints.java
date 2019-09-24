package gov.usgs.earthquake.nshmp.www.meta;

import java.util.List;
import java.util.Set;

import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.gmm.Imt;

@SuppressWarnings("unused")
class RegionConstraints implements Constraints {

  private final List<String> imt;
  private final List<String> vs30;

  RegionConstraints(Set<Imt> imts, Set<Vs30> vs30s) {
    // converting to Strings here, otherwise EnumSerializer will be used
    // and we want a compact list of (possible modified) enum.name()s
    this.imt = Util.enumsToNameList(imts);
    this.vs30 = Util.enumsToStringList(vs30s, vs30 -> vs30.name().substring(3));
  }
}
