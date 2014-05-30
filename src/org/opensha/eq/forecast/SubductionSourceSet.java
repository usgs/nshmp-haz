package org.opensha.eq.forecast;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;

import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class SubductionSourceSet implements SourceSet<SubductionSource> {

	List<SubductionSource> sources = Lists.newArrayList();
	private final String name;
	private final double weight;
	private final MagScalingType magScaling;

	SubductionSourceSet(String name, double weight, MagScalingType magScaling) {
		this.name = name;
		this.weight = weight;
		this.magScaling = magScaling;
	}
	
	void add(SubductionSource source) {
		sources.add(source);
	}
	
	
	@Override
	public Iterator<SubductionSource> iterator() {
		return sources.iterator();
	}

	@Override
	public String name() {
		return null;
		// TODO do nothing
		
	}

	//TODO tmp delete
	public int size() { return sources.size(); }

	@Override
	public SourceType type() {
		return SourceType.INTERFACE;
	}

}
