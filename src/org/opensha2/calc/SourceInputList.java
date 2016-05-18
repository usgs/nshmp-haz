package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import org.opensha2.eq.model.Source;

/**
 * A {@code List} of {@code HazardInput}s that contains a reference to the
 * single parent {@code Source} from which the inputs were derived. This allows
 * for downstream access to parent source properties.
 *
 * @author Peter Powers
 */
final class SourceInputList extends InputList {

  final Source parent;

  SourceInputList(Source parent) {
    this.parent = checkNotNull(parent);
  }

  @Override
  String parentName() {
    return parent.name();
  }

}
