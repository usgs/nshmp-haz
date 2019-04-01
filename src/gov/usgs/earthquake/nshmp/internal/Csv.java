package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Container class for streaming comma-delimited data. Each row in a
 * {@code *.csv} file is a {@link Record Record}, which can be queried for
 * values using the {@link #columnKeys()}.
 * 
 * <p><ul><li>Csv files may contain comment rows marked with {@code '#'}.</li>
 * 
 * <li>The first non-comment row must contain column identifiers.</li>
 * 
 * <li>Empty column <i>values</i> are permitted (e.g.
 * {@code '-117.1,34.0,,,'}).</li></ul>
 * 
 * <p>Example usage:
 * 
 * <pre>
 * try (Stream<Record> records = Csv.create(path).records()) {
 *   records.forEach(System.out::println);
 * } catch (IOException ioe) {
 *   throw new RuntimeException(ioe);
 * }
 * </pre>
 * 
 * @author pmpowers
 */
public class Csv {

  private final Path path;
  private final Map<String, Integer> columnKeys;
  private static final Splitter SPLITTER = Splitter.on(',').trimResults();

  private Csv(Path path) {
    // checkArgument(
    // Files.exists(checkNotNull(path)),
    // "Invalid csv path: %s", path);
    this.path = path;
    this.columnKeys = readHeader();
  }

  /**
   * Create a new comma-delimited file wrapper for reading.
   * 
   * @param path to csv file
   * @throws IllegalArgumentException if {@code path} is invalid
   * @throws IllegalStateException if the resource at {@code path} is empty or
   *         contains empty or duplicate header fields
   */
  public static Csv create(Path path) {
    return new Csv(path);
  }

  /**
   * Return the list of column keys as declared in the header row of this file.
   */
  public List<String> columnKeys() {
    return ImmutableList.copyOf(columnKeys.keySet());
  }

  /**
   * Return a stream of the records in this file. Callers should wrap references
   * to the returned stream in a try-with-resources statement to ensure proper
   * closing of the underlying csv resource.
   * 
   * @throws IOException if there is a problem reading the underlying csv
   *         resource once the stream's terminal operation is called
   * @throws IllegalStateException if a record is encountered as the stream is
   *         processed that is not the same size as the resource header
   */
  public Stream<Record> records() throws IOException {
    return Files.lines(path)
        .filter(Csv::filterComments)
        .skip(1)
        .map(this::toRecord);
  }

  /* Coment skipping predicate. */
  private static boolean filterComments(String line) {
    return !line.startsWith("#");
  }

  private Map<String, Integer> readHeader() {
    try (Stream<String> lines = Files.lines(path)) {
      Optional<String> header = lines
          .filter(Csv::filterComments)
          .findFirst();
      checkState(
          header.isPresent(),
          "Empty csv file: %s", path);
      List<String> keyList = Splitter.on(',').trimResults().splitToList(header.get());
      Set<String> keySet = ImmutableSet.copyOf(keyList);
      checkState(
          keyList.size() == keySet.size(),
          "Csv contains duplicate columns/keys: %s", keyList);
      checkState(
          !keyList.contains(""),
          "Csv contains unlabeled columns", keyList);

      return IntStream.range(0, keyList.size())
          .boxed()
          .collect(ImmutableMap.toImmutableMap(
              keyList::get,
              Function.identity()));

    } catch (IOException ioe) {
      throw new IllegalArgumentException("Problem reading csv: " + path, ioe);
    }
  }

  private Record toRecord(String line) {
    return new Record(line);
  }

  /**
   * An entry in a csv file.
   */
  public class Record {

    List<String> values;

    private Record(String line) {
      this.values = SPLITTER.splitToList(line);
      checkState(
          values.size() == columnKeys.size(),
          "Bad record: %s", line);
    }

    /**
     * Return the specified record value as a {@code boolean}.
     * @param key for value
     */
    public boolean getBoolean(String key) {
      return Boolean.parseBoolean(get(key));
    }

    /**
     * Return the specified record value as a {@code double}.
     * @param key for value
     */
    public double getDouble(String key) {
      return Double.parseDouble(get(key));
    }

    /**
     * Return the specified record value as an optional {@code double}.
     * @param key for value
     */
    public OptionalDouble getOptionalDouble(String key) {
      return get(key).equals("")
          ? OptionalDouble.empty()
          : OptionalDouble.of(getDouble(key));
    }

    /**
     * Return the specified record string value.
     * @param key for value
     */
    public String get(String key) {
      return values.get(columnKeys.get(key));
    }

    @Override
    public String toString() {
      return values.toString();
    }
  }
}
