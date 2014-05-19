package org.opensha.gmm;

/**
 * Implemented by ground motion models (GMMs) that perform a magnitude
 * conversion prior to using the value.
 * 
 * <p>At present such conversions are restricted to the 7 ground motion models
 * (GMMs) used for 2008 CEUS gridded seismicity sources. All are designed to
 * work with M<sub>w</sub> but the grid source magnitudes are presented as
 * m<sub>b</sub>. Note that {@link ToroEtAl_1997} provides m<sub>b</sub>
 * specific coefficients and does not need to implement to this interface.
 * 
 * @author Peter Powers
 * @see MagConverter
 * @see CEUS_Mb
 * @see AtkinsonBoore_2006
 * @see Campbell_2003
 * @see FrankelEtAl_1996
 * @see SilvaEtAl_2002
 * @see TavakoliPezeshk_2005
 */
public interface ConvertsMag {

	/**
	 * Returns a magnitude scale converter.
	 * @return a magnitude converter
	 */
	public MagConverter converter();
		
}
