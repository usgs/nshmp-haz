package org.opensha2.mfd;

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

  /** An MFD defining multiple magnitudes with varyig rates. */
  INCR;
}
