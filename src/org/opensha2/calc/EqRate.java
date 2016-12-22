package org.opensha2.calc;

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.SourceType;

import java.util.Map;

/**
 * Magnitude-frequency distribution data container. The class may be used for a
 * single source or a region.
 *
 * @author Peter Powers
 */
public class EqRate {

  /*
   * Depending on operation, this could be the raw combination of multiple MFDs,
   * or resampled to some uniform spacing.
   */
  XySequence totalMfd;

  Map<SourceType, XySequence> sourceMfds;

}
