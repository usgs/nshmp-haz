package org.opensha.calc;

import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.Imt.PGV;
import static org.opensha.gmm.Imt.SA0P075;

import org.opensha.gmm.Imt;

/**
 * Intensity measure limit identifiers. These are models that provide
 * {@link Imt}-dependent maxima and that may be used whn computing hazard
 * curves.
 * 
 * <p>This class exists to support 'clamps' on ground motions that have
 * historically been applied in the CEUS NSHM due to sometimes unreasonably high
 * ground motions implied by {@code μ + 3σ}.</p>
 *
 * @author Peter Powers
 */
public enum IntensityMeasureLimit {

	OFF {
		@Override double value(Imt imt) {
			/*
			 * Alternatively, this could be implemented to return some large
			 * Imt-dependent value.
			 */
			throw new UnsupportedOperationException();
		}
	},

	NSHM_CEUS {
		@Override double value(Imt imt) {
			/*
			 * Clamping/limiting is turned off at and above 0.75 sec.
			 * 
			 * TODO few CEUS Gmms support PGV; only Atkinson 06p and 08p.
			 * Revisit as it may just be more appropriate to throw a UOE.
			 */
			if (imt.isSA()) return imt.ordinal() < SA0P075.ordinal() ? 6.0 : Double.MAX_VALUE;
			if (imt == PGA) return 3.0;
			if (imt == PGV) return 400.0;
			throw new UnsupportedOperationException();
		}
	};

	abstract double value(Imt imt);

}
