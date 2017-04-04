package org.opensha2.calc;

import org.opensha2.internal.Parsing;

/**
 * Hazard curve value types.
 *
 * @author Peter Powers
 */
public enum CurveValue {

  /** Annual-rate. */
  ANNUAL_RATE,

  /** Poisson probability. */
  POISSON_PROBABILITY;
  
  @Override
  public String toString() {
    return Parsing.enumLabelWithSpaces(this, true);
  }
}
