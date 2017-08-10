package gov.usgs.earthquake.nshmp.eq.fault.surface;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * <b>Title:</b> Container2D<p>
 *
 * <b>Description:</b> Default Container2DAPI implementation class for a 2D
 * grid. This class actually determines the storage mechanism and access to the
 * data. This implementation doesn't allow the internal data structure to
 * resize. Can design a subclass to do this if needed in the future. <p>
 *
 * The internal storage is a one dimensional array which is actually accessed by
 * two coordinates x and y, i.e. row and column. The two dimensional indices are
 * translated to a one dimensional index by the mapping:<p>
 *
 * index = (the number of columns per row * row) + column. <p>
 *
 * Note: This data array stored Objects so the actually data that you store can
 * be any Java class. This container is flexible to hold any type of data at
 * each "grid" point. <p>
 *
 * @author Steven W. Rock
 */

class Container2DImpl<T> implements Container2D<T> {

  /** Array of data elements - 2D flattened into 1D Array */
  private Object[] data;

  /** The number of rows in this two dimensional matrix. */
  private int numRows = 0;

  /** The number of columns in this two dimensional matrix. */
  private int numCols = 0;

  /** The number of rows times the number of columns. */
  private long size = 0L;

  /**
   * Name assigned to an instance of this container. Can be used to display in a
   * GUI, or used as a key in a Java Hashtable.
   */
  private String name;

  /**
   * No Argument Constructor for the Container2D object. Set's a default row and
   * column size to = 100.
   */
  public Container2DImpl() {
    this(100, 100);
  }

  /**
   * Constructs a new <code>Container2D</code> with the supplied dimensions.
   *
   * @param numRows number of rows
   * @param numCols number of columns
   * @exception IllegalArgumentException if <code>numRows</code> or
   *            <code>numCols</code> are less than 1, or if <code>(numRows *
   *            numCols)</code> exceeds the allowable 32-bit address space
   *            [2147483647].
   */
  public Container2DImpl(int numRows, int numCols) {
    checkArgument(numRows > 0, "Number of rows must be greater than 0");
    checkArgument(numCols > 0, "Number of columns must be greater than 0");
    checkArgument((((long) numRows * (long) numCols) < 2147483647L),
        "Container size exceeds allowable 32-bit address space");
    this.numRows = numRows;
    this.numCols = numCols;
    size = (long) numRows * (long) numCols;
    data = new Object[numRows * numCols];
  }

  /** Sets the name of this container */
  @Override
  public void setName(String name) {
    this.name = name;
  }

  /** Gets the name of this container */
  @Override
  public String name() {
    return name;
  }

  /**
   * Places a Java object into one cell in this two dimensional matrix specified
   * by the row and column indices.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   * @param obj The Java object to place in the cell.
   * @exception ArrayIndexOutOfBoundsException Thrown if the row and column are
   *            beyond the two dimensional matrix range.
   */
  @Override
  public void set(int row, int column, T obj) throws ArrayIndexOutOfBoundsException {
    checkBounds(row, column);
    data[row * numCols + column] = obj;
  }

  /**
   * Sets the number of Rows and Cols of the 2D container object.
   * @param numRows int number of rows
   * @param numCols int number of cols
   */
  protected void setNumRowsAndNumCols(int numRows, int numCols) {
    this.numCols = numCols;
    this.numRows = numRows;
    size = (long) numRows * (long) numCols;
    data = new Object[numRows * numCols];

  }

  /**
   * Returns the number of rows int this two dimensional container.
   *
   * @return Number of rows.
   */
  @Override
  public int getNumRows() {
    return numRows;
  }

  /**
   * Returns the number of columns in this two dimensional container.
   *
   * @return Get number of columns.
   */
  @Override
  public int getNumCols() {
    return numCols;
  }

  /**
   * Returns the object stored in this two dimensional cell.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   */
  @Override
  @SuppressWarnings("unchecked")
  public T get(int row, int column) {
    checkBounds(row, column);
    return (T) data[row * numCols + column];
  }

  /**
   * Returns an ordered list iterator over all columns associated with one row.
   * This returns all the objects in that row. The results are returned from
   * lowest index to highest along the interating row.
   *
   * @param row The x coordinate of the cell.
   * @return The columnIterator value
   * @exception ArrayIndexOutOfBoundsException Thrown if the row is beyond the
   *            two dimensional matrix range.
   */
  @Override
  public ListIterator<T> getColumnIterator(int row) throws ArrayIndexOutOfBoundsException {
    if (row >= numRows) {
      throw new ArrayIndexOutOfBoundsException("colIt: Row cannot be greater than max index");
    }

    ColumnIterator it = new ColumnIterator(row);
    return it;
  }

  /**
   * Returns an ordered list iterator over all rows associated with one column.
   * This returns all the objects in that column. The results are returned from
   * lowest index to highest along the interating column.
   *
   * @param column The y coordinate of the cell.
   * @return The rowIterator value
   * @exception ArrayIndexOutOfBoundsException Thrown if the column is beyond
   *            the two dimensional matrix range.
   */
  @Override
  public ListIterator<T> getRowIterator(int column) throws ArrayIndexOutOfBoundsException {
    if (column >= numCols) {
      throw new ArrayIndexOutOfBoundsException(
          "rowIt Column cannot be greater than max index");
    }

    RowIterator it = new RowIterator(column);
    return it;
  }

  /**
   * This returns an iterator of all the Java objects stored in this two
   * dimensional matrix iterating over all rows within a column and then moving
   * to the next column until iteration has been done over all rows and all
   * columns.
   *
   * @return The allByColumnsIterator value
   */
  @Override
  public ListIterator<T> getAllByColumnsIterator() {
    AllByColumnsIterator it = new AllByColumnsIterator();
    return it;
  }

  /**
   * This returns an iterator of all the Java objects stored in this two
   * dimensional matrix iterating over all columns within a rows and then moving
   * to the next column until iteration has been done over all columns and all
   * rows.
   *
   * @return The allByRowsIterator value
   */
  @Override
  public ListIterator<T> getAllByRowsIterator() {
    AllByRowsIterator it = new AllByRowsIterator();
    return it;
  }

  /**
   * Checks that the specified row and column are valid indices into the 2D
   * array. Internal helper function used only by this class.
   *
   * @param row check row value less that max row index
   * @param column check col value less that max col index
   * @exception ArrayIndexOutOfBoundsException Thrown if row or column < 0 or >
   *            max row or max col.
   */
  protected void checkBounds(int row, int column)
      throws ArrayIndexOutOfBoundsException {

    if (row < 0) {
      throw new ArrayIndexOutOfBoundsException("Container2D Row cannot be less than zero");
    }
    if (column < 0) {
      throw new ArrayIndexOutOfBoundsException("Container2D Column cannot be less than zero");
    }
    if (row >= numRows) {
      throw new ArrayIndexOutOfBoundsException(
          "Container2D Row cannot be greater than max index: " + numRows);
    }
    if (column >= numCols) {
      throw new ArrayIndexOutOfBoundsException(
          "Container2D Column cannot be greater than max index: " + numCols);
    }
  }

  /**
   * Removes all object reference stored in this container and resets all grid
   * metics back to 0. Doesn't delete the actual objects, only removes the
   * pointer references stored at each grid cell location.
   */
  @Override
  public void clear() {

    data = null;
    numRows = 0;
    numCols = 0;
    size = 0L;

  }

  /**
   * Removes the object's reference stored in the grid cell. Doesn't delete the
   * actual object, only removes the pointer reference. This class losses any
   * reference to the data object, but that object may still be referenced in
   * some other class.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   */
  // public void delete( int row, int column ) {
  //
  // String S = C + ": delete(): ";
  // checkBounds( row, column, S );
  // data[row * numCols + column] = null;
  //
  // }

  /**
   * check if this grid cell has a java object stored in it. Returns false if
   * this data point is null.
   *
   * @param row The x coordinate of the cell.
   * @param column The y coordinate of the cell.
   * @return True if an object has been set in this cell.
   */
  @Override
  public boolean exist(int row, int column) {
    checkBounds(row, column);

    if (get(row, column) == null) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * returns the number of cells in this two dimensional matrix, i.e. numwRows *
   * numCols.
   *
   * @return The number of cells.
   */
  @Override
  public long size() {
    return size;
  }

  // /**
  // * The most generic iterator that returns all Java stored in this two
  // * dimensional matrix with no guarantee of ordering either by rows or by
  // * columns. Internally this function will probably just call get
  // * allByRowsIterator
  // *
  // * @return Description of the Return Value
  // */
  // public ListIterator<T> listIterator() {
  // AllByRowsIterator it = new AllByRowsIterator();
  // return it;
  // }

  /**
   * Converts our internal data structure to the Java 2 dimensional array.
   * Helper translation function.
   * @return 2D Object array - Object[][]
   */
  public Object[][] toJava2D() {

    Object[][] d = new Object[numRows][numCols];
    for (int j = 0; j < numRows; j++) {
      for (int i = 0; i < numCols; i++) {
        d[i][j] = get(i, j);
      }
    }
    return d;
  }

  // /**
  // * The main program for the Container2D class. Simple tester application
  // * that verifies the container class is working as expected. Not a unit
  // * test case harness, just simple and quick user verification by eye,
  // *
  // * @param args The command line arguments
  // */
  // public static void main( String[] args ) {
  //
  // String S = C + ": Main(): ";
  // System.out.println( S + "Starting" );
  //
  // int xsize = 5;
  // int ysize = 10;
  //
  // Container2DImpl<String> con = new Container2DImpl<String>( xsize, ysize
  // );
  // for ( int x = 0; x < xsize; x++ ) {
  // for ( int y = 0; y < ysize; y++ ) {
  // con.set( x, y, "" + x + ", " + y );
  // }
  // }
  //
  // System.out.println( S + "(1,1) = " + con.get( 1, 1 ) );
  //
  // System.out.println( S );
  // System.out.println( S );
  // System.out.println( S + "getRowIterator" );
  //
  // ListIterator<String> it = con.getRowIterator( 2 );
  // while ( it.hasNext() ) {
  //
  // String obj = it.next();
  // System.out.println( S + obj.toString() );
  // }
  //
  // System.out.println( S );
  // System.out.println( S );
  // System.out.println( S + "getColumnIterator" );
  //
  // it = con.getColumnIterator( 2 );
  // while ( it.hasNext() ) {
  //
  // String obj = it.next();
  // System.out.println( S + obj.toString() );
  //
  // }
  //
  // System.out.println( S );
  // System.out.println( S );
  // System.out.println( S + "getAllByColumnsIterator" );
  //
  // it = con.getAllByColumnsIterator();
  // while ( it.hasNext() ) {
  //
  // String obj = it.next();
  // System.out.println( S + obj.toString() );
  //
  // }
  //
  // System.out.println( S );
  // System.out.println( S );
  // System.out.println( S + "getAllByRowsIterator" );
  //
  // it = con.getAllByRowsIterator();
  // while ( it.hasNext() ) {
  //
  // String obj = it.next();
  // System.out.println( S + obj.toString() );
  //
  // }
  //
  // System.out.println( S );
  // System.out.println( S );
  // System.out.println( S + "List Iterator" );
  //
  // it = con.listIterator();
  // while ( it.hasNext() ) {
  //
  // String obj = it.next();
  // System.out.println( S + obj.toString() );
  // }
  //
  // System.out.println( S + "Ending" );
  //
  // }

  /**
   * <b>Title:</b> Container2DListIterator<p>
   *
   * <b>Description:</b> Base abstract class for all iterators. Stores the
   * indexes, etc, and implements nextIndex() and hasNext(). All unsupported
   * methods throws Exceptions. <p>
   *
   * This is how iterators should be handled, i.e. the class should be an inner
   * class so that the outside world only ever sees a ListIterator.<p>
   *
   * The iterator shouldn't be in a seperate class file because it needs
   * intimate knowledge to the data structure (in this case a java array) which
   * is usually hidden to the outside world. By making it an inner class, the
   * iterator has full access to the private variables of the data class.<p>
   *
   * This implementation allows read only access to the collection as iterating
   * over it, in other words, several write functions are not implemented ere,
   * just throws UnsupportedException.<p>
   *
   * @author Steven W. Rock
   * @created February 25, 2002
   * @version 1.0
   */

  abstract class Container2DListIterator implements ListIterator<T> {

    /** Current index into the collection */
    int cursor = 0;

    /**  */
    int lastRet = -1;

    /**  */
    int lastIndex = 0;

    /**
     * No arg constructgor - returns full column to iterate over, pinned to one
     * row
     */
    public Container2DListIterator() {}

    /**
     * Not implemented, but part of the ListIterator API
     *
     * @param obj New value at current iteration index
     * @exception UnsupportedOperationException Funciton not currently
     *            implemented .
     */
    @Override
    public void set(Object obj) throws UnsupportedOperationException {
      throw new UnsupportedOperationException("set(Object obj) Not implemented.");
    }

    /**
     * Returns true if there are more elements in this container, else returns
     * false.
     */
    @Override
    public boolean hasNext() {
      return cursor != lastIndex;
    }

    /** Returns the index value of the next iteration. */
    @Override
    public int nextIndex() {
      return cursor;
    }

    /**
     * Returns the next object from this collection container.
     *
     * @return The object stored at the next index
     * @exception NoSuchElementException No more elements available, at end of
     *            list.
     */
    @Override
    public abstract T next() throws NoSuchElementException;

    /**
     * Rolls back the iterator to the previous index, returning the previous
     * object stored at that index.
     *
     * @return The object stored at the previous index
     * @exception UnsupportedOperationException Function currently not
     *            implemented.
     */
    @Override
    public T previous() throws UnsupportedOperationException {
      throw new UnsupportedOperationException("hasPrevious() Not implemented.");
    }

    /**
     * Returns the index value of the next iteration.
     *
     * @exception UnsupportedOperationException Function currently not
     *            implemented.
     */
    @Override
    public int previousIndex() throws UnsupportedOperationException {
      throw new UnsupportedOperationException("hasPrevious() Not implemented.");
    }

    /**
     * Checks if not at begining of iteration.
     *
     * @return True if previous, false otherwise.
     * @exception UnsupportedOperationException Function currently not
     *            implemented.
     */
    @Override
    public boolean hasPrevious() throws UnsupportedOperationException {
      throw new UnsupportedOperationException("hasPrevious() Not implemented.");
    }

    /**
     * Allows updating object in the container as iteration over elements
     *
     * @param obj New value at iteration point.
     * @exception UnsupportedOperationException Function currently not
     *            implemented.
     */
    @Override
    public void add(Object obj) throws UnsupportedOperationException {
      throw new UnsupportedOperationException("add(Object obj) Not implemented.");
    }

    /**
     * Deletes the next object in the iteration
     *
     * @exception UnsupportedOperationException Function currently not
     *            implemented.
     */
    @Override
    public void remove() throws UnsupportedOperationException {
      throw new UnsupportedOperationException("remove() Not implemented.");
    }

  }

  /**
   * <b>Title:</b> ColumnIterator<p> <b>Description:</b> Returns all column
   * points for one row<p>
   *
   * @author Steven W. Rock
   * @created February 25, 2002
   * @version 1.0
   */
  class ColumnIterator extends Container2DListIterator {

    /**
     * Description of the Field
     */
    int pinnedRow;

    /**
     * returns full column to iterate over, pinned to one row
     *
     * @param row Description of the Parameter
     */
    public ColumnIterator(int row) {
      super();
      this.pinnedRow = row;
      lastIndex = numCols;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     * @exception NoSuchElementException Description of the Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public T next() throws NoSuchElementException {
      try {
        T object = (T) data[pinnedRow * numCols + cursor];
        lastRet = cursor++;
        return object;
      } catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException(
            "You have iterated past the last element." + e.toString());
      }
    }

  }

  /**
   * <b>Title:</b> RowIterator<> <b>Description:</b> Returns all column points
   * for one row<p>
   *
   *
   * @author Steven W. Rock
   * @created February 25, 2002
   * @version 1.0
   */
  class RowIterator extends Container2DListIterator {

    /**
     * Description of the Field
     */
    int pinnedColumn;

    /**
     * returns full column to iterate over, pinned to one row
     *
     * @param column Description of the Parameter
     */
    public RowIterator(int column) {
      super();
      this.pinnedColumn = column;
      lastIndex = numRows;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     * @exception NoSuchElementException Description of the Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public T next() throws NoSuchElementException {
      try {
        // T object = (T)data[cursor * numCols + pinnedColumn];
        T object = get(cursor, pinnedColumn);
        lastRet = cursor++;
        return object;
      } catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException(
            "You have iterated past the last element." + e.toString());
      }
    }

  }

  /**
   * <b>Title:</b> AllByColumnsIterator<p> <b>Description:</b> Returns all rows
   * for a column, then moves to the next column<p>
   *
   * @author Steven W. Rock
   * @created February 25, 2002
   * @version 1.0
   */
  class AllByColumnsIterator extends Container2DListIterator {

    /**
     * Description of the Field
     */
    int currentColumn = 0;
    /**
     * Description of the Field
     */
    int currentRow = 0;

    /**
     * Constructor for the AllByColumnsIterator object
     */
    public AllByColumnsIterator() {
      super();
      lastIndex = numCols * numRows;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     * @exception NoSuchElementException Description of the Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public T next() throws NoSuchElementException {

      try {

        // T object = (T)data[currentRow * numCols + currentColumn];
        T object = get(currentRow, currentColumn);

        currentRow++;
        if (currentRow == numRows) {
          currentRow = 0;
          currentColumn++;
        }

        lastRet = cursor++;
        return object;
      } catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException(
            "You have iterated past the last element. " + e.toString());
      }

    }
  }

  /**
   * <b>Title:</b> AllByRowsIterator<p> <b>Description:</b> Returns all columns
   * for a row, then moves to the next row<p>
   *
   * @author Steven W. Rock
   * @created February 25, 2002
   * @version 1.0
   */
  class AllByRowsIterator extends Container2DListIterator {

    /**
     * Constructor for the AllByRowsIterator object
     */
    public AllByRowsIterator() {
      super();
      lastIndex = numCols * numRows;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     * @exception NoSuchElementException Description of the Exception
     */
    @Override
    @SuppressWarnings("unchecked")
    public T next() throws NoSuchElementException {

      try {
        int row = cursor / numCols;
        int col = cursor % numCols;
        // T object = (T)data[cursor];
        // System.out.println("cursor="+cursor+",
        // row="+row+"/"+numRows+", col="+col+"/"+numCols);
        T object = get(row, col);
        lastRet = cursor++;
        return object;
      } catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException(
            "You have iterated past the last element. " + e.toString());
      }

    }

  }

  private final static char TAB = '\t';

  /** Prints out each location and fault information for debugging */
  @Override
  public String toString() {

    StringBuffer b = new StringBuffer();
    b.append('\n');

    int i = 0, j, counter = 0;
    while (i < numRows) {

      j = 0;
      while (j < numCols) {

        b.append("" + i + TAB + j + TAB);
        Object obj = this.get(i, j);
        if (obj != null) {
          b.append(obj.toString());
          counter++;
        } else {
          b.append("NULL");
        }
        b.append('\n');

        j++;
      }
      i++;

    }
    b.append("\nNumber of Rows = " + numRows + '\n');
    b.append("Number of Columns = " + numCols + '\n');
    b.append("Size = " + size + '\n');
    b.append("Number of non-null objects = " + counter + '\n');
    return b.toString();
  }

  @Override
  public Iterator<T> iterator() {
    // return listIterator();
    return new AllByRowsIterator();
  }

}
