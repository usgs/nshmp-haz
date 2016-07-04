package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import org.opensha2.eq.model.SystemSourceSet;

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
  // TODO package privacy - or move to SYstemSourceSet
  // TODO how to get back to parent to mine info; index?
  // TODO need index reference

  final SystemSourceSet parent;

  public SystemInputList(SystemSourceSet parent) {
    this.parent = checkNotNull(parent);
  }

  @Override
  String parentName() {
    return parent.name();
  }

}
