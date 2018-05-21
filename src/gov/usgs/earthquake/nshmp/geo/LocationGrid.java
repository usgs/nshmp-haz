package gov.usgs.earthquake.nshmp.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.padStart;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

/**
 * An immutable, tabular grid of locations that supplies row and column data as
 * {@link LocationList}s.
 *
 * <p>Internally, the grid is backed by a Guava {@link ArrayTable}.
 *
 * @author Peter Powers
 */
public final class LocationGrid implements Iterable<Location> {

  private final ArrayTable<Integer, Integer, Location> grid;

  // starts are inclusive, ends are exclusive
  private final int rowStart;
  private final int rowWidth;
  private final int rowEnd;
  private final int columnStart;
  private final int columnWidth;
  private final int columnEnd;

  // true if entire grid is being used
  private final boolean master;

  private LocationGrid(
      ArrayTable<Integer, Integer, Location> grid,
      int rowStart,
      int rowWidth,
      int columnStart,
      int columnWidth) {

    this.grid = grid;

    this.rowStart = rowStart;
    this.rowWidth = rowWidth;
    this.rowEnd = rowStart + rowWidth;

    this.columnStart = columnStart;
    this.columnWidth = columnWidth;
    this.columnEnd = columnStart + columnWidth;

    this.master = rowStart == 0 && rowWidth == grid.rowKeyList().size() &&
        columnStart == 0 && columnWidth == grid.columnKeyList().size();
  }

  /**
   * Return the number of {@code Location}s in this grid.
   */
  public int size() {
    return rowWidth * columnWidth;
  }

  /**
   * Return the number of rows in this grid.
   */
  public int rows() {
    return rowWidth;
  }

  /**
   * Return the row at index.
   * @param index of the row to retrieve
   * @throws IndexOutOfBoundsException if {@code index < 0 || index >= rows()}
   */
  public LocationList row(int index) {
    return new Row(rowStart + checkElementIndex(index, rows()));
  }

  /**
   * Return the first row.
   */
  public LocationList firstRow() {
    return row(0);
  }

  /**
   * Return the last row.
   */
  public LocationList lastRow() {
    return row(rows() - 1);
  }

  /**
   * Return the number of columns in this grid.
   */
  public int columns() {
    return columnWidth;
  }

  /**
   * Return the column at index.
   * @param index of the column to retrieve
   * @throws IndexOutOfBoundsException if
   *         {@code index < 0 || index >= columns()}
   */
  public LocationList column(int index) {
    return new Column(columnStart + checkElementIndex(index, columns()));
  }

  /**
   * Return the first column.
   */
  public LocationList firstColumn() {
    return column(0);
  }

  /**
   * Return the last column.
   */
  public LocationList lastColumn() {
    return column(columns() - 1);
  }

  /**
   * Return a new grid that is a window into this one. The specified window
   * dimensions must be less than or equal to the dimensions of this grid.
   *
   * @param rowStart first row of window
   * @param rowWidth number of rows in the window
   * @param columnStart first column of window
   * @param columnWidth
   */
  public LocationGrid window(int rowStart, int rowWidth, int columnStart, int columnWidth) {
    checkElementIndex(rowStart, this.rowWidth);
    checkPositionIndex(rowStart + rowWidth, this.rowWidth);
    checkElementIndex(columnStart, this.columnWidth);
    checkPositionIndex(columnStart + columnWidth, this.columnWidth);
    return new LocationGrid(
        this.grid,
        this.rowStart + rowStart,
        rowWidth,
        this.columnStart + columnStart,
        columnWidth);
  }

  /**
   * Return the parent grid. Method returns itself unless it was created using
   * one or more calls to {@link #window(int, int, int, int)}. In this case, a
   * grid equivalent to this grid's greatest ancestor is returned.
   */
  public LocationGrid parent() {
    return master ? this : new LocationGrid(
        grid,
        0, grid.rowKeyList().size(),
        0, grid.columnKeyList().size());
  }

  /**
   * Lazily compute the bounds of the {@code Location}s in this grid. Method
   * delegates to {@link Locations#bounds(Iterable)}.
   */
  public Bounds bounds() {
    return Locations.bounds(this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("LocationGrid [")
        .append(rowWidth).append(" x ")
        .append(columnWidth).append("]")
        .append(" window=").append(!master);
    if (!master) {
      sb.append(" [parent ")
          .append(grid.rowKeyList().size()).append("r x ")
          .append(grid.columnKeyList().size()).append("c]");
    }
    sb.append(NEWLINE);
    LocationList firstRow = firstRow();
    int lastColumnIndex = rowWidth - 1;
    int lastRowIndex = columnWidth - 1;
    appendCorner(sb, 0, 0, firstRow.first());
    appendCorner(sb, 0, lastColumnIndex, firstRow.last());
    LocationList lastRow = lastRow();
    appendCorner(sb, lastRowIndex, 0, lastRow.first());
    appendCorner(sb, lastRowIndex, lastColumnIndex, lastRow.last());
    if (size() < 1024) {
      sb.append("Locations:").append(NEWLINE);
      for (int i = 0; i < rows(); i++) {
        for (int j = 0; j < columns(); j++) {
          appendLocation(sb, i, j, grid.at(i, j));
        }
        sb.append(NEWLINE);
      }
    }
    return sb.toString();
  }

  private static void appendCorner(StringBuilder builder, int row, int column, Location loc) {
    builder.append("Corner: ");
    appendLocation(builder, row, column, loc);
  }

  private static void appendLocation(StringBuilder builder, int row, int column, Location loc) {
    builder.append(padStart(Integer.toString(row), 5, ' '))
        .append(padStart(Integer.toString(column), 5, ' '))
        .append("    ").append(loc)
        .append(NEWLINE);
  }

  @Override
  public Iterator<Location> iterator() {
    return new Iterator<Location>() {

      private int rowIndex = rowStart;
      private int columnIndex = columnStart;

      @Override
      public boolean hasNext() {
        return columnIndex < columnEnd && rowIndex < rowEnd;
      }

      @Override
      public Location next() {
        Location loc = grid.at(rowIndex, columnIndex++);
        if (columnIndex == columnEnd) {
          rowIndex++;
        }
        return loc;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private class Row extends LocationList {

    private final int rowIndex;

    private Row(int rowIndex) {
      this.rowIndex = rowIndex;
    }

    @Override
    public int size() {
      return grid.columnKeyList().size();
    }

    @Override
    public Location get(int index) {
      return grid.at(rowIndex, index);
    }

    @Override
    public Iterator<Location> iterator() {
      return new Iterator<Location>() {
        private int columnIndex;

        @Override
        public boolean hasNext() {
          return columnIndex < columnEnd;
        }

        @Override
        public Location next() {
          return grid.at(rowIndex, columnIndex++);
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  private class Column extends LocationList {

    private final int columnIndex;

    private Column(int columnIndex) {
      this.columnIndex = columnIndex;
    }

    @Override
    public int size() {
      return grid.rowKeyList().size();
    }

    @Override
    public Location get(int index) {
      return grid.at(index, columnIndex);
    }

    @Override
    public Iterator<Location> iterator() {
      return new Iterator<Location>() {
        private int rowIndex;

        @Override
        public boolean hasNext() {
          return rowIndex < rowEnd;
        }

        @Override
        public Location next() {
          return grid.at(rowIndex++, columnIndex);
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  /**
   * Return a new builder.
   *
   * @param rows expected number of rows
   * @param columns expected number of columns
   */
  public static Builder builder(int rows, int columns) {
    return new Builder(rows, columns);
  }

  /**
   * A single-use builder of {@code LocationGrid}s. Use
   * {@link LocationGrid#builder(int, int)} to create new builder instances.
   */
  public static class Builder {

    private final ArrayTable<Integer, Integer, Location> grid;
    private boolean built = false;

    private Builder(int rows, int columns) {
      grid = ArrayTable.create(
          ContiguousSet.create(Range.closedOpen(0, rows), DiscreteDomain.integers()),
          ContiguousSet.create(Range.closedOpen(0, columns), DiscreteDomain.integers()));
    };

    /**
     * Set the Location at the specified {@code row} and {@code column} indices.
     *
     * @param row index of location to set
     * @param column index of location to set
     * @param loc to set
     * @return this {@code Builder}
     */
    public Builder set(int row, int column, Location loc) {
      grid.set(row, column, loc);
      return this;
    }

    /**
     * Fill a row with the specified {@code Location}s.
     *
     * @param index of row to fill
     * @param locs to fill row with
     * @return this {@code Builder}
     */
    public Builder fillRow(int index, LocationList locs) {
      checkArgument(locs.size() == grid.columnKeyList().size());
      int column = 0;
      for (Location loc : locs) {
        grid.set(index, column++, loc);
      }
      return this;
    }

    /**
     * Fill a row with the specified {@code Location}s.
     *
     * @param index of column to fill
     * @param locs to fill column with
     * @return this {@code Builder}
     */
    public Builder fillColumn(int index, LocationList locs) {
      checkArgument(locs.size() == grid.rowKeyList().size());
      int row = 0;
      for (Location loc : locs) {
        grid.set(row++, index, loc);
      }
      return this;
    }

    /**
     * Return a newly created {@code LocationGrid}.
     */
    public LocationGrid build() {
      checkState(!grid.containsValue(null), "Some Locations have not been set");
      checkState(!built, "This builder has already been used");
      return new LocationGrid(
          grid,
          0, grid.rowKeyList().size(),
          0, grid.columnKeyList().size());
    }
  }

  // TODO clean
  public static void main(String[] args) {

    Set<Integer> strikeIndices = ContiguousSet.create(
        Range.closedOpen(0, 9),
        DiscreteDomain.integers());

    Set<Integer> dipIndices = ContiguousSet.create(
        Range.closedOpen(0, 4),
        DiscreteDomain.integers());

    ArrayTable<Integer, Integer, Location> t = ArrayTable.create(dipIndices, strikeIndices);

    for (int dipIndex : dipIndices) {
      for (int strikeIndex : strikeIndices) {
        Location loc = Location.create(
            34.0 + 0.2 * strikeIndex,
            -117.4 + 0.1 * dipIndex);
        t.set(dipIndex, strikeIndex, loc);
      }
    }

    LocationGrid grid2 = new LocationGrid(t, 0, dipIndices.size(), 0, strikeIndices.size());
    System.out.println(grid2);

    int rows = 4;
    int cols = 9;
    Builder b = builder(4, 9);

    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        Location loc = Location.create(
            34.0 + 0.2 * j,
            -117.4 + 0.1 * i);
        b.set(i, j, loc);
      }
    }
    System.out.println(b.grid);
    LocationGrid grid = b.build();
    System.out.println(grid.grid.rowKeyList());
    System.out.println(grid.grid.columnKeyList());
    System.out.println(grid);

    // System.out.println(grid.lastColumn());
    // Iterator<Location> it = grid.firstRow().iterator();
    // System.out.println(it);
    // TODO test itertator.remove()
  }
}
