package org.opensha.data;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

/**
 * Skeletal {@code XY_Sequence} implementation providing support for data access
 * and iterators.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
abstract class AbstractXY_Sequence implements XY_Sequence {

	@Override public List<Double> xValues() { return new X_List(); }
	@Override public List<Double> yValues() { return new Y_List(); }
	@Override public Iterator<XY_Point> iterator() { return new XYIterator(); }

	private class XYIterator implements Iterator<XY_Point> {
		private int caret = 0;
		@Override public boolean hasNext() { return caret < size(); }
		@Override public XY_Point next() {
			return new XY_Point() {
				int index = caret++;
				@Override public double x() { return getX(index); }
				@Override public double y() { return getY(index); }
				@Override public void set(double y) { 
					AbstractXY_Sequence.this.set(index, y); }
				@Override public String toString() {
					return "XY_Point [" + x() + ", " + y() + "]"; 
				}
			};
		}
		@Override public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private class X_List extends AbstractList<Double> {
		@Override public Double get(int idx) { return getX(idx); }
		@Override public int size() { return AbstractXY_Sequence.this.size(); }
		@Override public Iterator<Double> iterator() {
			return new Iterator<Double>() {
				private int caret = 0;
				@Override public boolean hasNext() { 
					return caret < AbstractXY_Sequence.this.size();
				}
				@Override public Double next() { 
					return getX(caret++);
				}
				@Override public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	private class Y_List extends AbstractList<Double> {
		@Override public Double get(int idx) { return getY(idx); }
		@Override public int size() { return AbstractXY_Sequence.this.size(); }
		@Override public Iterator<Double> iterator() {
			return new Iterator<Double>() {
				private int caret = 0;
				@Override public boolean hasNext() { 
					return caret < AbstractXY_Sequence.this.size();
				}
				@Override public Double next() { 
					return getY(caret++);
				}
				@Override public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
	
	abstract double getXunchecked(int index);
	abstract double getYunchecked(int index);
	

}
