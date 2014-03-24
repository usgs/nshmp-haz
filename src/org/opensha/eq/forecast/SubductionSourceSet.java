package org.opensha.eq.forecast;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class SubductionSourceSet implements SourceSet<SubductionSource> {

	List<SubductionSource> sources = Lists.newArrayList();
	
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
		return SourceType.SUBDUCTION;
	}

}
