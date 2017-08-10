package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import gov.usgs.earthquake.nshmp.eq.model.SystemSourceSet;

/**
 * A {@code List} of {@code HazardInput}s that contains a reference to the
 * parent {@code SystemSourceSet} from which the inputs were derived. This
 * allows for downstream access to parent source properties.
 *
 * <p>Presently, a {@code SystemSourceSet} consists of sources for which there
 * is only a single rupture. Note that this could change in the future if some
 * magnitude variability were imposed on each source.
 *
 * @author Peter Powers
 */
public final class SystemInputList extends InputList {

  /*
   * TODO
   * 
   * This is messy, cross package interaction is requiring exposure of some
   * things we'd rather not expose. This isn't really meant to be a public
   * class.
   * 
   * package privacy - or move to SYstemSourceSet how to get back to parent to
   * mine info; index? need index reference comment bitset array is going to be
   * reallocating because we don't know it's size at creation time; using linked
   * list
   * 
   * Well suited for a builder
   */

  final SystemSourceSet parent;
  final Set<Integer> sectionIndices; // ascending in rRup
  final List<BitSet> bitsets; // source/rupture bisets

  public SystemInputList(
      SystemSourceSet parent,
      Set<Integer> sectionIndices) {

    this.parent = checkNotNull(parent);
    this.sectionIndices = sectionIndices; // may be null for empty only
    this.bitsets = new ArrayList<>();
  }

  public static SystemInputList empty(SystemSourceSet parent) {
    return new SystemInputList(parent, null);
  }

  public void addBitset(BitSet bitset) {
    bitsets.add(bitset);
  }

  @Override
  String parentName() {
    return parent.name();
  }

}
