package org.opensha.eq.fault.scaling;

import org.opensha.function.ArbitrarilyDiscretizedFunc;

/**
 * <b>Title:</b>MagAreaRelationship<br>
 *
 * <b>Description:</b>  This is an abstract class that gives the median and standard
 * deviation of magnitude as a function of area (km-squared) or visa versa.  The
 * values can also be a function of rake.  Note that the standard deviation for area
 * as a function of mag is given for natural-log(area) not area.  <p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public abstract class MagAreaRelationship extends MagScalingRelationship {

    /**
     * This returns an ArbitrarilyDiscretizedFunc with Mag on the y-axis and 
     * Area on the x-axis (sampled at the given mag increments)
     * @param minMag
     * @param deltaMag
     * @param numMag
     * @return
     */
    public ArbitrarilyDiscretizedFunc getMagAreaFunction(double areaMin, double areaMax, int numArea) {
    	ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
    	double deltaLogArea = (Math.log10(areaMax)-Math.log10(areaMin))/(numArea-1);
    	for(int i=0; i<numArea; i++) {
    		double area = Math.pow(10,Math.log10(areaMin)+deltaLogArea*i);
    		double mag = getMedianMag(area);
    		func.set(area, mag);
    	}
    	func.setName(this.name());
    	func.setInfo("Mag-Area relationship");
    	return func;
    }

    /**
     * Computes the median magnitude from rupture area
     * @param area in km-squared
     * @return median magnitude
     */
    public abstract double getMedianMag(double area);

    /**
     * Computes the median magnitude from rupture area & rake
     * @param area in km-squared
     * @param rake in degrees
     * @return median magnitude
     */
    public double getMedianMag(double area, double rake) {
      setRake(rake);
      return getMedianMag(area);
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area (for
     * the previously set or default rake)
     * @param area in km-squared
     * @return standard deviation
     */
    public abstract double getMagStdDev();

    /**
     * Gives the standard deviation for the magnitude as a function of area & rake
     * @param area in km-squared
     * @param rake in degrees
     * @return standard deviation
     */
    public double getMagStdDev(double rake) {
      setRake(rake);
      return getMagStdDev();
    }

    /**
     * Computes the median rupture area from magnitude (for the previously set or default rake)
     * @param mag - moment magnitude
     * @return median area in km-squared
     */
    public abstract double getMedianArea(double mag);

    /**
     * Computes the median rupture area from magnitude & rake
     * @param mag - moment magnitude
     * @param rake in degrees
     * @return median area in km-squared
     */
    public double getMedianArea(double mag, double rake) {
      setRake(rake);
      return getMedianArea(mag);
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude
     * (for the previously set or default rake)
     * @param mag - moment magnitude
     * @param rake in degrees
     * @return standard deviation
     */
    public abstract double getAreaStdDev();

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude & rake
     * @param mag - moment magnitude
     * @param rake in degrees
     * @return standard deviation
     */
    public double getAreaStdDev(double rake) {
      setRake(rake);
      return getAreaStdDev();
    }

    /**
     * over-ride parent method to call getMedainArea(mag) here
     */
    public double getMedianScale(double mag) {
      return getMedianArea(mag);
    }

    /**
     * over-ride parent method to call getAreaStdDev() here
     */
    public double getScaleStdDev() {
      return getAreaStdDev();
    }

}
