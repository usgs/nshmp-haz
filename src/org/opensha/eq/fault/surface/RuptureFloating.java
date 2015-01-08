package org.opensha.eq.fault.surface;

import static org.opensha.eq.model.FloatStyle.CENTERED;
import static org.opensha.eq.model.FloatStyle.FULL_DOWN_DIP;

import java.util.List;

import org.opensha.eq.model.Rupture;
import org.opensha.mfd.IncrementalMfd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Rupture floating model identifiers. Each can convert a {@link GriddedSurfaceWithSubsets}
 * to an immutable list of {@link Rupture}s.
 *
 * @author Peter Powers
 */
public enum RuptureFloating {

	/*
	 * NSHM fortran: if zTop is >1 km, no downdip floaters; how to do this? also
	 * need to handle cal_fl(oater): no down dip
	 * 
	 * Include downdip rupture scenarios only if original
	 *  top of fault is at or near Earth surface. Deep blind thrusts dont need this.
	 *  
	 *  Is there any way to have these enums do anything?
	 *  Should be able to take a GriddedSurfaceWithSubsets and 
	 *  loop mfd
	 *  	get magnitude and scaling model dependent dimensions
	 *  	create list of ruptures
	 *  
	 */
	OFF, STRIKE_ONLY, NSHM;
//	OFF {
//		@Override List<Rupture> asRuptureList(GriddedSurfaceWithSubsets floatableSurface, IncrementalMfd mfd) {
//			Builder<Rupture> rupListbuilder = ImmutableList.builder();
//			rupListbuilder.add(Rupture.create(mag, rate, rake, surface));
//			return null;
//			// TODO do nothing
//			
//		}
//	},
//	STRIKE_ONLY, // full down dip width
//	NSHM_FAULT {
//		// M<6.5 6 4 2 1, M≤6.75 4 2 1, M≤7.0 2 1 else 1
//		@Override List<Rupture> asRuptureList(GriddedSurfaceWithSubsets floatableSurface) {
//			return null;
//			// TODO do nothing
//			
//		}
//	};
	
	abstract List<Rupture> createRuptureList(GriddedSurfaceWithSubsets floatableSurface, IncrementalMfd mfd);

	
	private List<Rupture> createRuptureList(IncrementalMfd mfd) {
		ImmutableList.Builder<Rupture> rupListbuilder = ImmutableList.builder();

		// @formatter:off
		for (int i = 0; i < mfd.getNum(); ++i) {
			double mag = mfd.getX(i);
			double rate = mfd.getY(i);

			// TODO do we really want to do this??
			if (rate < 1e-14) continue; // shortcut low rates

			if (mfd.floats()) {

				// get global floating model
				// rupture dimensions
				double maxWidth = surface.width();
				double length = computeRuptureLength(msr, mag, maxWidth, aspectRatio);
				double width = Math.min(length / aspectRatio, maxWidth);

				// 2x width ensures full down-dip rupture
				if (floatStyle == FULL_DOWN_DIP) {
					width = 2 * maxWidth;
				}

				GriddedSurfaceWithSubsets surf = (GriddedSurfaceWithSubsets) surface;
				
				// rupture count
				double numRup = (floatStyle != CENTERED) ?
					surf.getNumSubsetSurfaces(length, width, offset) :
					surf.getNumSubsetSurfacesAlongLength(length, offset);

				for (int r = 0; r < numRup; r++) {
					RuptureSurface floatingSurface = (floatStyle != CENTERED) ?
						surf.getNthSubsetSurface(length, width, offset, r) :
						surf.getNthSubsetSurfaceCenteredDownDip(length, width, offset, r);
					double rupRate = rate / numRup;
					Rupture rup = Rupture.create(mag, rake, rupRate, floatingSurface);
					rupListbuilder.add(rup);
				}
			} else {
				Rupture rup = Rupture.create(mag, rate, rake, surface);
				rupListbuilder.add(rup);
			}
		}
		// @formatter:on
		return rupListbuilder.build();
	}
	
	private List<Rupture> createRuptureList(IncrementalMfd mfd) {
		ImmutableList.Builder<Rupture> rupListbuilder = ImmutableList.builder();

		// @formatter:off
		for (int i = 0; i < mfd.getNum(); ++i) {
			double mag = mfd.getX(i);
			double rate = mfd.getY(i);

			// TODO do we really want to do this??
			if (rate < 1e-14) continue; // shortcut low rates

			if (mfd.floats()) {

				// get global floating model
				// rupture dimensions
				double maxWidth = surface.width();
				double length = computeRuptureLength(msr, mag, maxWidth, aspectRatio);
				double width = Math.min(length / aspectRatio, maxWidth);

				// 2x width ensures full down-dip rupture
				if (floatStyle == FULL_DOWN_DIP) {
					width = 2 * maxWidth;
				}

				GriddedSurfaceWithSubsets surf = (GriddedSurfaceWithSubsets) surface;
				
				// rupture count
				double numRup = (floatStyle != CENTERED) ?
					surf.getNumSubsetSurfaces(length, width, offset) :
					surf.getNumSubsetSurfacesAlongLength(length, offset);

				for (int r = 0; r < numRup; r++) {
					RuptureSurface floatingSurface = (floatStyle != CENTERED) ?
						surf.getNthSubsetSurface(length, width, offset, r) :
						surf.getNthSubsetSurfaceCenteredDownDip(length, width, offset, r);
					double rupRate = rate / numRup;
					Rupture rup = Rupture.create(mag, rake, rupRate, floatingSurface);
					rupListbuilder.add(rup);
				}
			} else {
				Rupture rup = Rupture.create(mag, rate, rake, surface);
				rupListbuilder.add(rup);
			}
		}
		// @formatter:on
		return rupListbuilder.build();
	}


}
