package org.opensha2.eq.fault;

import org.opensha2.util.Parsing;

/**
 * Generalized identifier for different focal mechanism types.
 * @author Peter Powers
 */
public enum FocalMech {

  // TODO can this be reconciled with FaultStyle

  /** A strike-slip focal mechanism. */
  STRIKE_SLIP(90.0, 0.0),

  /** A reverse slip (or thrust) focal mechanism. */
  REVERSE(50.0, 90.0),

  /** A normal slip focal mechanism. */
  NORMAL(50.0, -90.0);

  private double dip;
  private double rake;

  private FocalMech(double dip, double rake) {
    this.dip = dip;
    this.rake = rake;
  }

  /**
   * Returns a 'standard' dip value for this mechanism.
   * @return the dip
   */
  public double dip() {
    return dip;
  }

  /**
   * Returns a 'standard' rake value for this mechanism.
   * 
   * <p><b>NOTE:</b> This value may not be appropriate for future PSHA if
   * directivity is considered. For example, {@code STRIKE_SLIP} currently
   * returns a left-lateral rake. Furthermore, oblique focal mechanisms will
   * need to specify right- or left-lateral reverse and normal combinations.</p>
   * 
   * @return the rake
   */
  public double rake() {
    return rake;
  }

  @Override
  public String toString() {
    return Parsing.enumLabelWithDashes(this, true);
  }

}
