package org.opensha.data;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;

/**
 * Skeletal {@code XY_Sequence} implementation providing support for data access
 * and iterators.
 * 
 * @author Peter Powers
 */
abstract class AbstractXY_Sequence implements XY_Sequence {

	// @formatter:off
	
	@Override public List<Double> xValues() { return new X_List(); }
	@Override public List<Double> yValues() { return new Y_List(); }
	@Override public Iterator<XY_Point> iterator() { return new XYIterator(); }

	private class XYIterator implements Iterator<XY_Point> {
		private int caret = 0;
		@Override public boolean hasNext() { return caret < size(); }
		@Override public XY_Point next() {
			return new XY_Point() {
				int index = caret++;
				@Override public double x() {
					return AbstractXY_Sequence.this.x(index);
				}
				@Override public double y() {
					return AbstractXY_Sequence.this.y(index);
				}
				@Override public void set(double y) {
					AbstractXY_Sequence.this.set(index, y);
				}
				@Override public String toString() {
					return "XY_Point: [" + x() + ", " + y() + "]";
				}
			};
		}
		@Override public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private class X_List extends AbstractList<Double> implements RandomAccess {
		@Override public Double get(int index) { return x(index); }
		@Override public int size() { return AbstractXY_Sequence.this.size(); }
		@Override public Iterator<Double> iterator() {
			return new Iterator<Double>() {
				private int caret = 0;
				@Override public boolean hasNext() { 
					return caret < size();
				}
				@Override public Double next() { 
					return x(caret++);
				}
				@Override public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	private class Y_List extends AbstractList<Double> implements RandomAccess {
		@Override public Double get(int index) { return y(index); }
		@Override public int size() { return AbstractXY_Sequence.this.size(); }
		@Override public Iterator<Double> iterator() {
			return new Iterator<Double>() {
				private int caret = 0;
				@Override public boolean hasNext() { 
					return caret < size();
				}
				@Override public Double next() { 
					return y(caret++);
				}
				@Override public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
	
	abstract double xUnchecked(int index);
	abstract double yUnchecked(int index);

	private static final String LF = StandardSystemProperty.LINE_SEPARATOR.value();
	
	@Override
	public String toString() {
		return new StringBuilder(getClass().getSimpleName())
				.append(":")
				.append(LF)
				.append(Joiner.on(LF).join(this))
				.toString();
	}
		
}
