package org.opensha.eq.fault.surface;

import java.util.List;

import org.opensha.eq.model.Rupture;

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
	OFF, STRIKE_ONLY, NSHM_FAULT;
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
	
//	abstract List<Rupture> asRuptureList(GriddedSurfaceWithSubsets floatableSurface);

}
