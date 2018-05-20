package gov.usgs.earthquake.nshmp.gmm;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

import java.util.function.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.common.primitives.Doubles;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;

@SuppressWarnings("javadoc")
@Ignore("Parameterized Use Only")
public class GmmTest {

  private static final String DATA_DIR = "data/";
  private static final double TOL = 1e-6;
  static List<GmmInput> inputsList;

  private int index;
  private Gmm gmm;
  private Imt imt;
  private double exMedian;
  private double exSigma;

  public GmmTest(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {

    this.index = index;
    this.gmm = gmm;
    this.imt = imt;
    this.exMedian = exMedian;
    this.exSigma = exSigma;
  }

  @Test
  public void test() {
    ScalarGroundMotion sgm = gmm.instance(imt).calc(inputsList.get(index));
    assertEquals(exMedian, Math.exp(sgm.mean()), TOL);
    assertEquals(exSigma, sgm.sigma(), TOL);
  }

  /* Use to generate Gmm result file */
  static void generateResults(
      Set<Gmm> gmms, 
      Set<Imt> imts, 
      String inputsFileName,
      String resultsFileName) throws IOException {
    
    List<GmmInput> inputs = loadInputs(inputsFileName);
    List<String> lines = new ArrayList<>();
    for (Gmm gmm : gmms) {
      for (Imt imt : imts) {
        GroundMotionModel gmModel = gmm.instance(imt);
        int modelIndex = 0;
        String id = gmm.name() + "-" + imt.name();
        for (GmmInput input : inputs) {
          ScalarGroundMotion sgm = gmModel.calc(input);
          String result = Parsing.join(
              Lists.newArrayList(modelIndex++ + "-" + id,
                  String.format("%.6f", Math.exp(sgm.mean())),
                  String.format("%.6f", sgm.sigma())),
              Delimiter.COMMA);
          lines.add(result);
        }
      }
    }
    Path out = Paths.get("tmp/Gmm-tests/", resultsFileName);
    Files.write(out, lines, StandardCharsets.UTF_8);
  }

  static List<Object[]> loadResults(String resource) throws IOException {
    URL url = Resources.getResource(GmmTest.class, DATA_DIR + resource);
    return FluentIterable
        .from(Resources.readLines(url, StandardCharsets.UTF_8))
        .transform(ResultsToObjectsFunction.INSTANCE::apply)
        .toList();
  }

  private enum ResultsToObjectsFunction implements Function<String, Object[]> {
    INSTANCE;
    @Override
    public Object[] apply(String line) {
      Iterator<String> lineIt = Parsing.split(line, Delimiter.COMMA).iterator();
      Iterator<String> idIt = Parsing.split(lineIt.next(), Delimiter.DASH).iterator();
      return new Object[] {
          Integer.valueOf(idIt.next()), // inputs index
          Gmm.valueOf(idIt.next()), // Gmm
          Imt.valueOf(idIt.next()), // Imt
          Double.valueOf(lineIt.next()), // median
          Double.valueOf(lineIt.next()) // sigma
      };
    }
  }

  static List<GmmInput> loadInputs(String resource) throws IOException {
    URL url = Resources.getResource(GmmTest.class, DATA_DIR + resource);
    return FluentIterable
        .from(Resources.readLines(url, StandardCharsets.UTF_8))
        .skip(1)
        .transform(ArgsToInputFunction.INSTANCE::apply)
        .toList();
  }

  private enum ArgsToInputFunction implements Function<String, GmmInput> {
    INSTANCE;
    @Override
    public GmmInput apply(String line) {

      Iterator<Double> it = FluentIterable
          .from(Parsing.split(line, Delimiter.COMMA))
          .transform(Doubles.stringConverter())
          .iterator();

      return GmmInput.builder()
          .mag(it.next())
          .distances(it.next(), it.next(), it.next())
          .dip(it.next())
          .width(it.next())
          .zTop(it.next())
          .zHyp(it.next())
          .rake(it.next())
          .vs30(it.next(), it.next() > 0.0)
          .z1p0(it.next())
          .z2p5(it.next())
          .build();
    }
  }

}
