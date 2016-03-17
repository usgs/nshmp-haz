package org.opensha2.eq.fault.scaling.impl;

import org.opensha2.eq.fault.scaling.MagAreaRelationship;

/**
 * <b>Title:</b>Ellsworth_B_WG02_MagAreaRel<br>
 *
 * <b>Description:</b>  This implements the "Ellsworth-B Mag-Area Rel." 
 * published as Equation 4.5b in WGCEP-2002 
 * (http://pubs.usgs.gov/of/2003/of03-214/WG02_OFR-03-214_Chapter4.pdf).
 * The equation is Mag=4.2+log10(Area).<p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public class Ellsworth_B_WG02_MagAreaRel extends MagAreaRelationship {

    final static String C = "Ellsworth_B_WG02_MagAreaRel";
    public final static String NAME = "Ellsworth-B (WGCEP, 2002, Eq 4.5b)";

    /**
     * Computes the median magnitude from rupture area.
     * @param area in km
     * @return median magnitude
     */
    public double getMedianMag(double area){
    		return  4.2 + Math.log(area)*lnToLog;
    }

    /**
     * Gives the standard deviation for magnitude
     * @return standard deviation
     */
    public double getMagStdDev(){ return 0.12;}

    /**
     * Computes the median rupture area from magnitude
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag){
          return Math.pow(10.0,mag-4.2);
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

