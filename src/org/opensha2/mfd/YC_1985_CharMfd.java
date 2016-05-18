package org.opensha2.mfd;

import static com.google.common.base.Preconditions.checkArgument;

import java.awt.geom.Point2D;

/**
 * <p>Title: YC_1985_CharMfd.java </p>
 *
 * <p>Description: This is the "characteristic" magnitude-frequency distribution
 * defined by Youngs and Coppersmith (1985, Bull. Seism. Soc. Am., 939-964). The
 * distribution is Gutenberg-Richter between magLower and magPrime, and constant
 * between (magUpper-deltaMagChar) and magUpper with a rate equal to that of the
 * Gutenberg-Richter part at (magPrime-deltaMagPrime). See their figure 10 for a
 * graphical explanation of these parameters. Note that magLower, magUpper,
 * magPrime, magUpper-deltaMagChar, and magPrime-deltaMagPrime must all be
 * exactly equal one of the descrete x-axis points. </p>
 *
 * @author Edward H. Field Date: Sept. 26, 2002
 * @version 1.0
 */

class YC_1985_CharMfd extends IncrementalMfd {

  private String C = new String("YC_1985_CharMfd"); // for showing messages
  public static String NAME = new String("Youngs and Coppersmith Dist");
  private double magLower;
  private double magUpper;
  private double deltaMagChar;
  private double magPrime;
  private double deltaMagPrime;
  private double bValue;

  /**
   * Constructor : this is the same as the parent class constructor
   * @param min - minimum mag of distribution
   * @param num - number of points in distribution
   * @param delta - discretization interval
   */
  public YC_1985_CharMfd(double min, int num, double delta) {
    super(min, num, delta, true);
  }

  /**
   * Constructor: this is the same as the parent class constructor
   * @param min - minimum mag of distribution
   * @param max - maximum mag of distribution
   * @param num - number of points in distribution
   */
  public YC_1985_CharMfd(double min, double max, int num) {
    super(min, max, num, true);
  }

  /**
   * Constructor: this is the full constructor /** Constructor: this constructor
   * assumes magLower is minX and magUpper to be maxX
   * @param min - minimum mag of distribution
   * @param num - number of points in distribution
   * @param delta - discretization interval
   * @param magLower - the lowest non-zero-rate magnitude
   * @param magUpper - the highest non-zero-rate magnitude
   * @param deltaMagChar - the width of the characteristic part (below magUpper)
   * @param magPrime - the upper mag of the GR part
   * @param deltaMagPrime - the distance below magPrime where the rate equals
   *        that over the char-rate part
   * @param bValue - the b value
   * @param totMoRate - the total moment rate
   */
  public YC_1985_CharMfd(double min, int num, double delta, double magLower,
      double magUpper, double deltaMagChar, double magPrime,
      double deltaMagPrime, double bValue, double totMoRate) {
    super(min, num, delta, true);

    this.magLower = magLower;
    this.magUpper = magUpper;
    this.deltaMagChar = deltaMagChar;
    this.magPrime = magPrime;
    this.deltaMagPrime = deltaMagPrime;
    this.bValue = bValue;

    calculateRelativeRates();
    scaleToTotalMomentRate(totMoRate);
  }

  /**
   * Constructor: this constructor assumes magLower is minX and magUpper to be
   * maxX
   * @param min - minimum mag of distribution
   * @param num - number of points in distribution
   * @param delta - discretization interval
   * @param deltaMagChar - the width of the characteristic part (below magUpper)
   * @param magPrime - the upper mag of the GR part
   * @param deltaMagPrime - the distance below magPrime where the rate equals
   *        that over the char-rate part
   * @param bValue - the b value
   * @param totMoRate - the total moment rate
   */
  public YC_1985_CharMfd(double min, int num, double delta, double deltaMagChar, double magPrime,
      double deltaMagPrime, double bValue, double totMoRate) {
    super(min, num, delta, true);
    // assumes magLower = minX and magUpper = maxX
    magLower = minX;
    magUpper = maxX;

    this.deltaMagChar = deltaMagChar;
    this.magPrime = magPrime;
    this.deltaMagPrime = deltaMagPrime;
    this.bValue = bValue;

    calculateRelativeRates();
    scaleToTotalMomentRate(totMoRate);
  }

  /**
   * Update distribution (using total moment rate rather than the total rate of
   * char events)
   * @param magLower - the lowest non-zero-rate magnitude
   * @param magUpper - the highest non-zero-rate magnitude
   * @param deltaMagChar - the width of the characteristic part (below magUpper)
   * @param magPrime - the upper mag of the GR part
   * @param deltaMagPrime - the distance below magPrime where the rate equals
   *        that over the char-rate part
   * @param bValue - the b value
   * @param totMoRate - the total moment rate
   */

  public void setAllButTotCharRate(double magLower, double magUpper, double deltaMagChar,
      double magPrime, double deltaMagPrime, double bValue,
      double totMoRate) {

    this.magLower = magLower;
    this.magUpper = magUpper;
    this.deltaMagChar = deltaMagChar;
    this.magPrime = magPrime;
    this.deltaMagPrime = deltaMagPrime;
    this.bValue = bValue;

    calculateRelativeRates();
    scaleToTotalMomentRate(totMoRate);
  }

  /**
   * Update distribution (using total rate of char events rather than total
   * moment rate)
   * @param magLower - the lowest non-zero-rate magnitude
   * @param magUpper - the highest non-zero-rate magnitude
   * @param deltaMagChar - the width of the characteristic part (below magUpper)
   * @param magPrime - the upper mag of the GR part
   * @param deltaMagPrime - the distance below magPrime where the rate equals
   *        that over the char-rate part
   * @param bValue - the b value
   * @param totCharRate - the total rate of characteristic events (cum rate at
   *        magUpper-deltaMagChar).
   */

  public void setAllButTotMoRate(double magLower, double magUpper, double deltaMagChar,
      double magPrime, double deltaMagPrime, double bValue,
      double totCharRate) {

    this.magLower = magLower;
    this.magUpper = magUpper;
    this.deltaMagChar = deltaMagChar;
    this.magPrime = magPrime;
    this.deltaMagPrime = deltaMagPrime;
    this.bValue = bValue;

    calculateRelativeRates();
    this.scaleToCumRate(magUpper - deltaMagChar, totCharRate);
  }

  /**
   * Overriden to prevent value setting.
   * @throws UnsupportedOperationException
   */
  @Override
  public void set(Point2D point) {
    throw new UnsupportedOperationException();
  }

  /**
   * Overriden to prevent value setting.
   * @throws UnsupportedOperationException
   */
  @Override
  public void set(double x, double y) {
    throw new UnsupportedOperationException();
  }

  /**
   * Overriden to prevent value setting.
   * @throws UnsupportedOperationException
   */
  @Override
  public void set(int index, double y) {
    throw new UnsupportedOperationException();
  }

  /**
   * private function to set the rate values
   */

  private void calculateRelativeRates() {

    checkArgument(deltaMagChar >= 0.0, "deltaMagChar must be positive");
    checkArgument(deltaMagPrime >= 0.0, "deltaMagPrime must be positive");
    checkArgument(magLower >= minX && magLower <= maxX,
        "magLower should lie between minX and maxX");
    checkArgument(magLower <= magUpper, "magLower cannot be less than magUpper");
    checkArgument(magPrime <= magUpper && magPrime >= magLower,
        "magPrime must be between magLower and magUpper");
    checkArgument((magPrime - deltaMagPrime) >= magLower,
        "magPrime-deltaMagPrime must be greater than magLower");
    checkArgument(deltaMagChar <= (magUpper - magPrime + deltaMagPrime),
        "deltaMagChar > (magUpper-magPrime+deltaMagPrime), which is not allowed");
    checkArgument(magPrime <= (magUpper - deltaMagChar),
        "magPrime > (magUpper-deltaMagChar), which is not allowed");

    // checks that magUpper, magLower, magPrime, deltaMagPrime, and
    // deltaMagChar
    // are well posed.
    // if( deltaMagChar < 0 )
    // throw new InvalidRangeException("deltaMagChar must be positive");
    // if( deltaMagPrime < 0 )
    // throw new InvalidRangeException("deltaMagPrime must be positive");
    // if(magLower < minX || magLower > maxX)
    // throw new Point2DException("magLower should lie between minX and
    // maxX");
    // if(magLower > magUpper)
    // throw new InvalidRangeException("magLower cannot be less than
    // magUpper");
    // if(magPrime > magUpper || magPrime < magLower)
    // throw new InvalidRangeException("magPrime must be between magLower
    // and magUpper");
    // if( (magPrime-deltaMagPrime) < magLower)
    // throw new InvalidRangeException("magPrime-deltaMagPrime must be
    // greater than magLower");
    // if( deltaMagChar > (magUpper-magPrime+deltaMagPrime) )
    // throw new InvalidRangeException("deltaMagChar >
    // (magUpper-magPrime+deltaMagPrime), which is not allowed");
    // if( magPrime > (magUpper-deltaMagChar) )
    // throw new InvalidRangeException("magPrime > (magUpper-deltaMagChar),
    // which is not allowed");

    double magForRate = magPrime - deltaMagPrime;

    int indexLower = getXIndex(magLower);
    int indexUpper = getXIndex(magUpper);
    int indexMagPrime = getXIndex(magPrime);
    int indexForRate = getXIndex(magForRate);
    int indexCharStart = getXIndex(magUpper - deltaMagChar);

    int i;

    for (i = 0; i < num; ++i) {
      super.set(i, 0.0);
    }

    for (i = indexLower; i <= indexMagPrime; ++i) {
      // to rates between
      // magLower and magPrime
      super.set(i, Math.pow(10, -bValue * getX(i)));
    }

    for (i = indexCharStart; i <= indexUpper; ++i) {
      // characteristic-mag
      // range
      super.set(i, Math.pow(10, -bValue * magForRate));
    }

  }

  /**
   *
   * @return the cumulative rate at magLower
   */

  public double getTotCumRate() {
    return getCumRate(magLower);
  }

  /**
   * @return the bValue for this distribution
   */
  public double get_bValue() {
    return bValue;
  }

  /**
   *
   * @return the magLower : lowest magnitude that has non zero rate
   */
  public double getMagLower() {
    return magLower;
  }

  /**
   *
   * @return the magUpper : highest magnitude that has non zero rate
   */
  public double getMagUpper() {
    return magUpper;
  }

  /**
   *
   * @return the magPrime
   */
  public double getMagPrime() {
    return magPrime;
  }

  /**
   *
   * @return the deltaMagPrime
   */
  public double getDeltaMagPrime() {
    return deltaMagPrime;
  }

  /**
   *
   * @return the deltaMagChar
   */
  public double getDeltaMagChar() {
    return deltaMagChar;
  }

  /**
   * returns the name of this class
   */

  @Override
  public String getDefaultName() {
    return NAME;
  }

  /**
   * this function returns String for drawing Legen in JFreechart
   * @return : returns the String which is needed for Legend in graph
   */
  @Override
  public String getDefaultInfo() {
    return ("minMag=" + minX + "; maxMag=" + maxX + "; numMag=" + num + "; magLower=" +
        magLower + "; magUpper=" +
        magUpper + "; deltaMagChar=" + this.getDeltaMagChar() +
        "; magPrime=" + this.getMagPrime() + "; deltaMagPrime=" + getDeltaMagPrime() +
        " bValue=" + bValue + "; totMoRate=" + (float) this.getTotalMomentRate() +
        "; totCumRate=" + (float) getCumRate(magLower));

  }

  /**
   * this method (defined in parent) is deactivated here (name is finalized)
   *
   * public void setName(String name) throws UnsupportedOperationException{
   * throw new UnsupportedOperationException(
   * "setName not allowed for MagFreqDist.");
   *
   * }
   *
   *
   * this method (defined in parent) is deactivated here (name is finalized)
   *
   * public void setInfo(String info)throws UnsupportedOperationException{ throw
   * new UnsupportedOperationException( "setInfo not allowed for MagFreqDist.");
   *
   * }
   */

}
