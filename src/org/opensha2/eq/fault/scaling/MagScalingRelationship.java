package org.opensha2.eq.fault.scaling;

import org.opensha2.eq.fault.Faults;
import org.opensha2.util.Named;

/**
 * This is an abstract class that gives the median and standard
 * deviation of magnitude as a function of some scalar value (or the median and
 * standard deviation of the scalar value as a function of magnitude).  The values
 * can also be a function of rake</b>  <p>
 * 
 * TODO can these all be made stateless??
 *
 * @author Edward H. Field
 * @version 1.0
 */

public abstract class MagScalingRelationship implements Named {

    final static String C = "MagScalingRelationship";

    protected final static double lnToLog = 0.434294;


    /**
     * The rupture rake in degrees.  The default is Double.NaN
     */
    protected double rake = Double.NaN;

    /**
     * Computes the median magnitude from some scalar value (for the previously set or default rake)
     * @return median magnitude
     */
    public abstract double getMedianMag(double scale);

    /**
     * Computes the median magnitude from some scalar value & rupture rake
     * @return median magnitude
     */
    public double getMedianMag(double scale, double rake) {
      setRake(rake);
      return getMedianMag(scale);
    }

    /**
     * This gives the  magnitude standard deviation (for the previously set or default rake)
     * @return median magnitude
     */
    public abstract double getMagStdDev();

    /**
     * This gives the magnitude standard deviation according to the given rake
     * @return median magnitude
     */
    public double getMagStdDev(double rake) {
      setRake(rake);
      return getMagStdDev();
    }

    /**
     * Computes the median scalar value from magnitude (for a previously set or default rake)
     */
    public abstract double getMedianScale(double mag);

    /**
     * Computes the median scalar value from magnitude & rake
     */
    public double getMedianScale(double mag, double rake) {
      setRake(rake);
      return getMedianScale(mag);
    }

    /**
     * Computes the standard deviation of the scalar-value from magnitude (for a
     * previously set or default rake)
     */
    public abstract double getScaleStdDev();

    /**
     * Computes the standard deviation of the scalar-value from rake
     */
    public double getScaleStdDev(double rake) {
      setRake(rake);
      return getScaleStdDev();
    }

    public void setRake(double rake) {
      Faults.validateRake(rake);
      this.rake = rake;
    }

}
