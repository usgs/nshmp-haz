package org.opensha2.function;

import java.awt.geom.Point2D;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractXY_DataSet implements XY_DataSet {

  /**
   * Information about this function, will be used in making the legend from a
   * parameter list of variables
   */
  protected String info = "";

  /**
   * Name of the function, useful for differentiation different instances of a
   * function, such as in an array of functions.
   */
  protected String name = "";

  @Override
  public String name() {
    return name;
  }

  /** Sets the name of this function. */
  public void setName(String name) {
    this.name = name;
  }

  /** Returns the info of this function. */
  public String getInfo() {
    return info;
  }

  /** Sets the info string of this function. */
  public void setInfo(String info) {
    this.info = info;
  }

  // X and Y Axis name
  protected String xAxisName, yAxisName;

  public void setXAxisName(String xName) {
    xAxisName = xName;
  }

  public String getXAxisName() {
    return xAxisName;
  }

  public void setYAxisName(String yName) {
    yAxisName = yName;
  }

  public String getYAxisName() {
    return yAxisName;
  }

  @Override
  public double getClosestX(double y) {
    double x = Double.NaN;
    double dist = Double.POSITIVE_INFINITY;
    for (int i = 0; i < getNum(); i++) {
      double newY = getY(i);
      double newDist = Math.abs(newY - y);
      if (newDist < dist) {
        dist = newDist;
        x = getX(i);
      }
    }
    return x;
  }

  @Override
  public double getClosestY(double x) {
    double y = Double.NaN;
    double dist = Double.POSITIVE_INFINITY;
    for (int i = 0; i < getNum(); i++) {
      double newX = getX(i);
      double newDist = Math.abs(newX - x);
      if (newDist < dist) {
        dist = newDist;
        y = getY(i);
      }
    }
    return y;
  }

  /**
   * It finds out whether the X values are within tolerance of an integer value
   * @param tolerance value to consider rounding errors
   *
   * @return true if all X values are within the tolerance of an integer value
   *         else returns false
   */
  public boolean areAllXValuesInteger(double tolerance) {
    int num = getNum();
    double x, diff;
    for (int i = 0; i < num; ++i) {
      x = getX(i);
      diff = Math.abs(x - Math.rint(x));
      if (diff > tolerance) return false;
    }
    return true;
  }

  /**
   * Returns an iterator over all x-values in the list. Results returned in
   * sorted order. Returns null if no points present.
   */
  public Iterator<Double> getXValuesIterator() {
    return new Iterator<Double>() {

      int index = 0;

      @Override
      public boolean hasNext() {
        return index < getNum();
      }

      @Override
      public Double next() {
        return getX(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Returns an iterator over all y-values in the list. Results returned in
   * sorted order along the x-axis. Returns null if no points present.
   */
  public Iterator<Double> getYValuesIterator() {
    return new Iterator<Double>() {

      int index = 0;

      @Override
      public boolean hasNext() {
        return index < getNum();
      }

      @Override
      public Double next() {
        return getY(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public Iterator<Point2D> iterator() {
    return new Iterator<Point2D>() {

      int index = 0;

      @Override
      public boolean hasNext() {
        return index < getNum();
      }

      @Override
      public Point2D next() {
        return get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public List<Double> xValues() {
    return new AbstractList<Double>() {
      @Override
      public Double get(int index) {
        return getX(index);
      }

      @Override
      public int size() {
        return getNum();
      }

      @Override
      public Iterator<Double> iterator() {
        final Iterator<Point2D> it = AbstractXY_DataSet.this.iterator();
        return new Iterator<Double>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public Double next() {
            return it.next().getX();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public List<Double> yValues() {
    // doublecheck AbstractList docs and check/test immutability of list
    return new AbstractList<Double>() {
      @Override
      public Double get(int index) {
        return getY(index);
      }

      @Override
      public int size() {
        return getNum();
      }

      @Override
      public Iterator<Double> iterator() {
        final Iterator<Point2D> it = AbstractXY_DataSet.this.iterator();
        return new Iterator<Double>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public Double next() {
            return it.next().getY();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

}
