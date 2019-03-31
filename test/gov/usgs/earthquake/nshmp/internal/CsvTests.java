package gov.usgs.earthquake.nshmp.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.internal.Csv.Record;

@SuppressWarnings("javadoc")
public class CsvTests {

  private static final Path DATA_PATH = Paths.get(
      "test/gov/usgs/earthquake/nshmp/internal/data");

  @Test(expected = NullPointerException.class)
  public void testCreateNPE() {
    Csv.create(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateIAE() {
    Csv.create(DATA_PATH.resolve("no-file.csv"));
  }

  @Test(expected = IllegalStateException.class)
  public void testCreateISE1() {
    Csv.create(DATA_PATH.resolve("csv-tests-empty.csv"));
  }

  @Test(expected = IllegalStateException.class)
  public void testCreateISE2() {
    Csv.create(DATA_PATH.resolve("csv-tests-bad-header-1.csv"));
  }

  @Test(expected = IllegalStateException.class)
  public void testCreateISE3() {
    Csv.create(DATA_PATH.resolve("csv-tests-bad-header-2.csv"));
  }

  private static final List<String> COLUMN_KEYS = ImmutableList.of(
      "lon", "lat", "a", "b", "c", "d");

  @Test
  public void testColumnKeys() {
    Csv csv = Csv.create(DATA_PATH.resolve("csv-tests.csv"));
    assertEquals(COLUMN_KEYS, csv.columnKeys());
  }

  @Test
  public void testRecords() throws IOException {
    Csv csv = Csv.create(DATA_PATH.resolve("csv-tests.csv"));
    List<Record> records = csv.records().collect(Collectors.toList());
    assertEquals(-100, records.get(0).getDouble("lon"), 0.0);
    assertEquals(2, records.get(0).getDouble("b"), 0.0);
    assertEquals(false, records.get(1).getBoolean("d"));
    assertEquals("test", records.get(2).get("c"));
    assertSame(OptionalDouble.empty(), records.get(2).getOptionalDouble("a"));
    assertEquals(20, records.get(2).getOptionalDouble("lat").getAsDouble(), 0.0);
  }

  @Test(expected = IllegalStateException.class)
  public void testRecordsISE() throws IOException {
    Csv csv = Csv.create(DATA_PATH.resolve("csv-tests-bad-record.csv"));
    csv.records().collect(Collectors.toList());
  }

  @Test
  public void testToString() throws IOException {
    String expected = "[-110, 30, , , true, false]";
    String actual = Csv.create(DATA_PATH.resolve("csv-tests.csv"))
        .records()
        .skip(1)
        .findFirst()
        .get()
        .toString();
    assertEquals(expected, actual);
  }

}
