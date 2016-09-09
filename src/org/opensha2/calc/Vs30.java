package org.opensha2.calc;

/**
 * Identifiers for commonly used values of Vs30. These correspond to the NEHERP
 * site classes that have histroically been supported by the NSHMp in hazard
 * calculations.
 *
 * @author Peter Powers
 */
public enum Vs30 {

  /** NEHRP site class A. */
  VS_2000("Site class A"),

  /** NEHRP site class B. */
  VS_1150("Site class B"),

  /** NEHRP B/C boundary site class. */
  VS_760("B/C boundary"),

  /** NEHRP site class C. */
  VS_537("Site class C"),

  /** NEHRP C/D boundary site class. */
  VS_360("C/D boundary"),

  /** NEHRP site class D. */
  VS_259("Site class D"),

  /** NEHRP D/E boundary site class. */
  VS_180("D/E boundary");

  private String label;
  private double value;

  private Vs30(String label) {
    this.label = label;
    this.value = Double.valueOf(name().substring(3));
  }

  @Override
  public String toString() {
    return this.name().substring(3) + " m/s (" + label + ")";
  }

  /**
   * Return the Vs30 value for this identifier.
   */
  public double value() {
    return value;
  }

  /**
   * Create a Vs30 constant from a Vs30 {@code value}.
   * @param value to process
   */
  public static Vs30 fromValue(double value) {
    String name = "VS_" + (int) value;
    return Enum.valueOf(Vs30.class, name);
  }

}
