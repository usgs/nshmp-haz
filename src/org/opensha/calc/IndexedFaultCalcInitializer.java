package org.opensha.calc;

import static java.lang.Math.sin;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.eq.forecast.DistanceType.*;

import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.data.DataUtils;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.forecast.DistanceType;
import org.opensha.eq.forecast.FaultSource;
import org.opensha.eq.forecast.IndexedFaultSource;
import org.opensha.eq.forecast.Rupture;
import org.opensha.gmm.GmmInput;

import com.google.common.collect.Lists;
import com.google.common.collect.Table;

/**
 * Compiles source and site data into a {@code List} of {@code GmmInput}s.
 * @author Peter Powers
 */
final class IndexedFaultCalcInitializer implements Callable<GmmInput> {

	// TODO clean
	
	// TODO this should take some derivative object IdxdFltSrcData
	// Does the work of determining which distances to use from the supplied
	// Table and indices and building various M variants either due to the use
	// of Aleatory Uncertainty or, conceivably, due to combining logic tree
	// branches. TODO we will want to resurrect List return type, but it is
	// overkill however in the dev/base case 
	
	private final IndexedFaultSource source;
	private final Site site;
	private final Table<DistanceType, Integer, Double> rTable;
	private final List<Integer> sectionIDs;
	
	IndexedFaultCalcInitializer(IndexedFaultSource source, Site site,
		Table<DistanceType, Integer, Double> rTable, List<Integer> sectionIDs) {
		this.source = source;
		this.site = site;
		this.rTable = rTable;
		this.sectionIDs = sectionIDs;
	}	
	
	
	@Override
	public GmmInput call() throws Exception {
		// List<GmmInput> inputs = Lists.newArrayList(); // if aleatory flag
		// or perhaps we have lists of mag or rake variants
		
		int rjbKey = DataUtils.minKey(rTable.row(R_JB), sectionIDs);
		int rRupKey = DataUtils.minKey(rTable.row(R_RUP), sectionIDs);
		// rRup key is used for rX
		
		GmmInput.Builder builder = GmmInput.builder()
				.mag(source.mag())
				.distances(
					rTable.get(R_JB, rjbKey),
					rTable.get(R_RUP, rRupKey),
					rTable.get(R_X, rRupKey))
				.dip(source.dip())
				.width(source.width())
				.zTop(source.zTop())
				.zHyp(source.zHyp())
				.rake(source.rake())
				.vs30(site.vs30, site.vsInferred)
				.z2p5(site.z2p5)
				.z1p0(site.z1p0);
		
		return builder.build();
	}

}
