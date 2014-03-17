package org.opensha.gmm;

/**
 * Magnitude conversion identifiers. At present these are only used in some
 * flavors of CEUS GMMs that are used with gridded seismicity sources that are
 * based on mblg catalogs.
 * 
 * @author Peter Powers
 * @see ConvertsMag
 */
enum MagConverter {

	/**
	 * m<sub>b</sub> to M<sub>w</sub> conversion of Johnston (1996).
	 * 
	 * <p>See: Johnston, A.C., 1996, Seismic moment assessment of earthquakes in
	 * stable continental regions—I. Instrumental seismicity: Geophysical
	 * Journal International, v. 126, p. 381–414.</p>
	 */
	MB_TO_MW_JOHNSTON {
		@Override
		double convert(double M) {
			return 1.14 + 0.24 * M + 0.0933 * M * M;
		}
	},

	/**
	 * m<sub>b</sub> to M<sub>w</sub> conversion of Atkinson &amp; Boore (1995).
	 * 
	 * <p>See: Atkinson, G.M., and Boore, D.M., 1995, Ground motion relations
	 * for eastern North America: Bulletin of the Seismological Society of
	 * America, v. 85, p. 17–30.</p>
	 */
	MB_TO_MW_ATKIN_BOORE {
		@Override
		double convert(double M) {
			return 2.715 - 0.277 * M + 0.127 * M * M;
		}
	},

	NONE {
		@Override
		double convert(double M) {
			return M;
		}
	};

	abstract double convert(double M);
}
