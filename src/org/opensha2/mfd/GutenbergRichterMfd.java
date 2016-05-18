package org.opensha2.mfd;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * <p>Title: GutenbergRichterMfd.java </p> <p>Description: This is a truncated
 * incremental Gutenberg-Richter distribution. Note that magLower and magUpper
 * must exactly equal one of the descrete x-axis values.</p>
 *
 * floats() always returns true.
 * 
 * @author Nitin Gupta
 * @author Vipin Gupta
 */
class GutenbergRichterMfd extends IncrementalMfd {

  public static String NAME = new String("Gutenberg Richter Dist");

  // TODO there were a billion constructors in here; really should use a
  // builder

  // for Debug purposes
  private boolean D = false;

  private double magLower; // lowest magnitude that has non zero rate
  private double magUpper; // highest magnitude that has non zero rate
  private double bValue; // the b value

  /**
   * constructor : this is same as parent class constructor
   * @param min
   * @param num
   * @param delta using the parameters we call the parent class constructors to
   *        initialise the parent class variables
   */

  public GutenbergRichterMfd(double min, int num, double delta) {
    super(min, num, delta, true);
    this.magLower = min;
  }

  /**
   * constructor: this is sameas parent class constructor
   * @param min
   * @param max
   * @param num using the min, max and num we calculate the delta
   */

  // public GutenbergRichterMfd(double min, double max, int num) {
  // super(min, max, num);
  //
  // }

  /**
   * constructor: this is sameas parent class constructor
   * @param min
   * @param max
   * @param num using the min, max and num we calculate the delta
   */

  // public GutenbergRichterMfd(double bValue, double totCumRate,
  // double min, double max, int num) {
  // super(min, max, num);
  // this.setAllButTotMoRate(min, max, totCumRate, bValue);
  // }

  /**
   * constructor: this constructor assumes magLower is minX and magUpper to be
   * maxX
   * @param min
   * @param num
   * @param delta
   * @param totMoRate : total Moment Rate
   * @param bValue : b value for this distribution
   */

  // public GutenbergRichterMfd(double min, int num, double delta,
  // double totMoRate, double bValue) {
  // super(min, num, delta);
  // // assumes magLower = minX and magUpper = maxX
  // setAllButTotCumRate(minX, maxX, totMoRate, bValue);
  // }

  /**
   * constructor:
   * @param min
   * @param num
   * @param delta
   * @param magLower : lowest magnitude that has non zero rate
   * @param magUpper : highest magnitude that has non zero rate
   * @param totMoRate : total Moment Rate
   * @param bValue : b value for this distribution
   */

  // public GutenbergRichterMfd(double min, int num, double delta,
  // double magLower, double magUpper,
  // double totMoRate, double bValue) {
  // super(min, num, delta);
  // setAllButTotCumRate(magLower, magUpper, totMoRate, bValue);
  // }

  /**
   * Set all values except Cumulative Rate
   * @param magLower : lowest magnitude that has non zero rate
   * @param magUpper : highest magnitude that has non zero rate
   * @param totMoRate : Total Moment Rate
   * @param bValue : b Value
   */
  public void setAllButTotCumRate(double magLower, double magUpper,
      double totMoRate, double bValue) {

    this.magLower = magLower;
    this.magUpper = magUpper;
    this.bValue = bValue;
    calculateRelativeRates();
    scaleToTotalMomentRate(totMoRate);
  }

  /**
   * Set all values except total moment rate
   * @param magLower : lowest magnitude that has non zero rate
   * @param magUpper : highest magnitude that has non zero rate
   * @param totCumRate : Total Cumulative Rate
   * @param bValue : b value
   */

  public void setAllButTotMoRate(double magLower, double magUpper,
      double totCumRate, double bValue) {

    this.magLower = magLower;
    this.magUpper = magUpper;
    this.bValue = bValue;
    calculateRelativeRates();
    scaleToCumRate(magLower, totCumRate);
  }

  /**
   * Set All but magUpper
   * @param magLower : lowest magnitude that has non zero rate
   * @param totMoRate : total moment rate
   * @param totCumRate : total cumulative rate
   * @param bValue : b value
   * @param relaxTotMoRate : It is "true" or "false". It accounts for tha fact
   *        that due to magnitude discretization, the specified totCumRate and
   *        totMoRate cannot both be satisfied simultaneously. if it is false,
   *        it means that match totMoRate exactly else it matches totCumRate
   *        exactly
   */
  public void setAllButMagUpper(double magLower, double totMoRate,
      double totCumRate,
      double bValue, boolean relaxTotMoRate) {

    if (D) System.out.println("magLower = " + magLower);
    if (D) System.out.println("totMoRate = " + totMoRate);
    if (D) System.out.println("totCumRate = " + totCumRate);
    if (D) System.out.println("bValue = " + bValue);
    if (D) System.out.println("relaxCumRate = " + relaxTotMoRate);

    // create variables for analytical moment integration
    double b = bValue;
    double N = totCumRate;
    double z = 1.5 - b;
    double X = N * b * Math.pow(10.0, 9.05) / z;
    double M1 = magLower;
    double M2;
    double tempTotMoRate = 0.0, lastMoRate = 0.0; // initialize this
    // temporary moment rate

    int index;

    // now we find magUpper by trying each mag as magUpper, computing the
    // total
    // moment rate analytically, and stopping when we get above the target
    // moment
    // rate.
    for (index = getXIndex(M1) + 1; tempTotMoRate < totMoRate && index < num; index++) {
      lastMoRate = tempTotMoRate;
      M2 = getX(index);
      tempTotMoRate = X * (Math.pow(10, z * M2) - Math.pow(10, z * M1)) /
        (Math.pow(10, -b * M1) - Math.pow(10, -b * M2));
    }

    index--;

    if (D) System.out.println("just above target: index=" + index + "; mag=" +
      getX(index));
    if (D) System.out.println("lastMoRate = " + lastMoRate);
    if (D) System.out.println("tempTotMoRate = " + tempTotMoRate);
    if (D) System.out.println("targetMoRate = " + totMoRate);

    // find which mag point it's closer:
    if (lastMoRate <= totMoRate && tempTotMoRate >= totMoRate) {
      double diff1 = tempTotMoRate - totMoRate;
      double diff2 = totMoRate - lastMoRate;

      // if it's closer to previous point
      if (diff2 < diff1) index--;
    } else
      throw new RuntimeException("Moment rate not attainable; totMoRate=" + totMoRate +
        "  totCumRate=" + totCumRate);

    magUpper = getX(index);

    if (D) System.out.println("chosen magUpper=" + magUpper);

    if (relaxTotMoRate)
      setAllButTotMoRate(magLower, magUpper, totCumRate, bValue);
    else
      setAllButTotCumRate(magLower, magUpper, totMoRate, bValue);
  }

  /**
   * private function to set the rate values
   */

  private void calculateRelativeRates() {

    // checks that magUpper, magLower lie between minX and maxX
    // it also checks that magUpper > magLower
    checkArgument(magLower >= minX && magLower <= maxX,
      "magLower (%s) should be between %s and %s", magLower, minX, maxX);
    checkArgument(magUpper >= magLower, "magLower must be < magUpper; magLower=%s", magLower);

    int indexLow = getXIndex(magLower); // find the index of magLower
    if (indexLow == -1)
      throw new RuntimeException("magLower is not within tolerance of an x-axis value");

    int indexUp = getXIndex(magUpper); // find the index of magUpper
    if (indexUp == -1)
      throw new RuntimeException("magUpper is not within tolerance of an x-axis value");

    int i;

    for (i = 0; i < indexLow; ++i) // set all rates below magLower to 0
      super.set(i, 0.0);

    for (i = indexLow; i <= indexUp; ++i) // assign correct values to rates
      // between magLower and magUpper
      super.set(i, Math.pow(10, -bValue * getX(i)));

    for (i = indexUp + 1; i < num; ++i) // set all rates above magUpper tp 0
      super.set(i, 0.0);
  }

  /**
   *
   * @return the cumulative rate at magLower
   */

  public double getTotCumRate() {
    return getCumRate(magLower);
  }

  /**
   * @return th bValue for this distribution
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
   * returns the name of this class
   */

  public String getDefaultName() {
    return NAME;
  }

  /**
   * this function returns String for drawing Legen in JFreechart
   * @return : returns the String which is needed for Legend in graph
   */
  public String getDefaultInfo() {
    return ("minMag=" + minX + "; maxMag=" + maxX + "; numMag=" + num +
      "; bValue=" + bValue + "; magLower=" + magLower + "; magUpper=" +
      (float) magUpper +
      "; totMoRate=" + (float) this.getTotalMomentRate() + "; totCumRate=" +
      (float) getCumRate(magLower));
  }

  // public static void main(String[] args) {
  // GutenbergRichterMfd grTest = new GutenbergRichterMfd(1d, 1d,0.0,10d,100);
  // System.out.println(grTest);
  // System.out.println("bVal="+grTest.compute_bValue(Double.NaN,Double.NaN));
  //
  //
  // }
  //

}
