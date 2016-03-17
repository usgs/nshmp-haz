package org.opensha2.eq.fault.scaling.impl;

import org.opensha2.eq.fault.scaling.MagAreaRelationship;

/**
 * <b>Title:</b>Ellsworth_A_WG02_MagAreaRel<br>
 *
 * <b>Description:</b>  This implements Ross Stein's powerlaw fit that he made for 
 * WGCEP 2007.(Appendix D).  The equation is Mag=4.2775*A^0.0726.<p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public class WGCEP_2007_PowLaw_MagAreaRel extends MagAreaRelationship {

    final static String C = "WGCEP_2007_PowLaw_MagAreaRel";
    public final static String NAME = "WGCEP (2007) power law";

    /**
     * Computes the median magnitude from rupture area.
     * @param area in km
     * @return median magnitude
     */
    public double getMedianMag(double area){
    		return 4.2775*Math.pow(area, 0.0726);
    }

    /**
     * Gives the standard deviation for magnitude
     * @return standard deviation
     */
    public double getMagStdDev(){ return Double.NaN;}

    /**
     * Computes the median rupture area from magnitude
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag){
          return Math.pow(mag/4.2775,1.0/0.0726);
   }

    /**
     * This returns NaN because the value is not available
     * @return standard deviation
     */
    public double getAreaStdDev() {return  Double.NaN;}

    /**
     * Returns the name of the object
     *
     */
    public String name() {
      return NAME;
    }
}

