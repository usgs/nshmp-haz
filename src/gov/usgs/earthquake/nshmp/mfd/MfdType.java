package gov.usgs.earthquake.nshmp.mfd;

/**
 * Magnitude-frequency distribution (MFD) type identifier.
 *
 * @author Peter Powers
 * @see Mfds
 */
public enum MfdType {

  /** An MFD with a single magnitude and rate. */
  SINGLE,

  /** An incremental Gutenberg-Richter MFD. */
  GR,

  /** A Gutenberg-Richter MFD with a tapered upper tail. */
  GR_TAPER,

  /**
   * An MFD defining multiple magnitudes with varying rates. This is only used
   * in the 2008 California model where the rates of magnitudes above 6.5 were
   * reduced by a fixed amount, yielding MFDs that no longer had a clean
   * functional form.
   */
  INCR;
}
