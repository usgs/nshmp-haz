package org.opensha2.calc;

import org.opensha2.mfd.Mfds;

import com.google.common.base.MoreObjects;

/**
 * Identifiers of common return periods, also referred to in PSHA as
 * probabilities of exceedance.
 * 
 * <p>Each identifier is structured as follows, for example {@code PE2IN50}:</p>
 * 
 * <ul><li>Reads as a 2% Poisson probability of exceeding some measure of ground
 * motion in a 50 year period.</li>
 * 
 * <li>Is equivalent to an annual rate of occurrence of ~0.000404 (see
 * {@link #annualRate()}).</li>
 * 
 * <li>Or once every 2475 years or so (see {@link #duration()} ).</li></ul>
 * 
 * <p>Note that the {@link Mfds} class includes useful static utilities for
 * working with Poisson probabilities, rates, and time spans.</p>
 *
 * @author Peter Powers
 * @see Mfds
 */
public enum ReturnPeriod {

  /*
   * TODO It would be really nice to add some commentary to each value about
   * what disciplines or industries use each value and why
   */

  /** A 1% probability of exceedance in 10,000 years. */
  PE1IN10000(0.000001),

  /** A 1% probability of exceedance in 1,000 years. */
  PE1IN1000(0.00001),

  /** A 1% probability of exceedance in 100 years. */
  PE1IN100(0.000101),

  /** A 1% probability of exceedance in 50 years. */
  PE1IN50(0.000201),

  /** A 2% probability of exceedance in 50 years. */
  PE2IN50(0.000404),

  /** A 5% probability of exceedance in 50 years. */
  PE5IN50(0.001026),

  /** A 10% probability of exceedance in 50 years. */
  PE10IN50(0.002107),

  /** A 40% probability of exceedance in 50 years. */
  PE40IN50(0.010217);

  private double annualRate;

  private ReturnPeriod(double annualRate) {
    this.annualRate = nameToAnnRate(name());
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(getClass().getSimpleName() + "." + name())
      .add("annualRate", annualRate)
      .add("duration", duration())
      .toString();
  }

  /**
   * Return the annual rate of occurrence represented by this time period.
   */
  public double annualRate() {
    return annualRate;
  }

  /**
   * Return the duration (number of years spanned) by this return period.
   */
  public int duration() {
    return (int) Math.rint(1.0 / annualRate);
  }

  private double nameToAnnRate(String name) {
    String[] values = name.substring(2).split("IN");
    double prob = Double.parseDouble(values[0]) / 100.0;
    double time = Double.parseDouble(values[1]);
    return Mfds.probToRate(prob, time);
  }
}
