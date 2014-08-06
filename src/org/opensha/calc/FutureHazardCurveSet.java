package org.opensha.calc;

import java.util.List;
import java.util.Map;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.gmm.Gmm;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class FutureHazardCurveSet extends AbstractFuture<HazardCurveSet> {

	List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> futureCurves;
	
	// want to be able to listen and add curves to builder as they become available
	// once all are added, call build() and set(V)
}
