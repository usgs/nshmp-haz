package org.opensha2.gmm;

import static org.junit.Assert.assertEquals;
import static org.opensha2.gmm.Gmm.ASK_14;
import static org.opensha2.gmm.Gmm.BSSA_14;
import static org.opensha2.gmm.Gmm.CB_14;
import static org.opensha2.gmm.Gmm.CY_14;
import static org.opensha2.gmm.Gmm.IDRISS_14;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.SA0P02;
import static org.opensha2.gmm.Imt.SA0P2;
import static org.opensha2.gmm.Imt.SA1P0;
import static org.opensha2.gmm.Imt.SA3P0;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.primitives.Doubles;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class Tests_NGAW2 {

	private static final String D_DIR = "data/";
	static final String GMM_INPUTS = "NGA_inputs.csv";
	private static final String GMM_RESULTS = "NGAW2_results.csv";
	private static final double TOL = 0.000001; // results precision = 1e-6
	private static List<GmmInput> inputsList;

	static {
		try {
			inputsList = loadInputs(GMM_INPUTS);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	@Parameters(name = "{index}: {0} {2} {1}") public static Collection<Object[]> data()
			throws IOException {
		return loadResults(GMM_RESULTS);
	}

	private int index;
	private Gmm gmm;
	private Imt imt;
	private double exMedian;
	private double exSigma;

	public Tests_NGAW2(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {

		this.index = index;
		this.gmm = gmm;
		this.imt = imt;
		this.exMedian = exMedian;
		this.exSigma = exSigma;
	}

	@Test public void test() {
		ScalarGroundMotion sgm = gmm.instance(imt).calc(inputsList.get(index));
		assertEquals(exMedian, Math.exp(sgm.mean()), TOL);
		assertEquals(exSigma, sgm.sigma(), TOL);
	}

	public static void main(String[] args) throws IOException {
		computeGM();
	}

	/* Result generation sets */
	private static Set<Gmm> gmms = EnumSet.of(ASK_14, BSSA_14, CB_14, CY_14, IDRISS_14);
	private static Set<Imt> imts = EnumSet.of(PGA, SA0P02, SA0P2, SA1P0, SA3P0);

	/* Use to generate Gmm result file */
	private static void computeGM() throws IOException {
		List<GmmInput> inputs = loadInputs(GMM_INPUTS);
		File out = new File("tmp/Gmm-tests/" + GMM_RESULTS);
		Files.write("", out, StandardCharsets.UTF_8);
		for (Gmm gmm : gmms) {
			for (Imt imt : imts) {
				GroundMotionModel gmModel = gmm.instance(imt);
				int modelIndex = 0;
				String id = gmm.name() + "-" + imt;
				for (GmmInput input : inputs) {
					ScalarGroundMotion sgm = gmModel.calc(input);
					String result = Parsing.join(
						Lists.newArrayList(modelIndex++ + "-" + id,
							String.format("%.6f", Math.exp(sgm.mean())),
							String.format("%.6f", sgm.sigma())), Delimiter.COMMA) +
						StandardSystemProperty.LINE_SEPARATOR.value();
					Files.append(result, out, StandardCharsets.UTF_8);
				}
			}
		}
	}

//	double[] hcVals = new double[] { 0.0010, 0.0013, 0.0018, 0.0024, 0.0033,
//		0.0044, 0.0059, 0.0080, 0.0108, 0.0145, 0.0195, 0.0263, 0.0353, 0.0476,
//		0.0640, 0.0862, 0.1160, 0.1562, 0.2102, 0.2829, 0.3808, 0.5125, 0.6898,
//		0.9284, 1.2496, 1.6819, 2.2638, 3.0470, 4.1011, 5.5200, 7.4296, 10.0000 };

	private static List<Object[]> loadResults(String resource) throws IOException {
		URL url = Resources.getResource(Tests_NGAW2.class, D_DIR + resource);
		return FluentIterable
			.from(Resources.readLines(url, StandardCharsets.UTF_8))
			.transform(ResultsToObjectsFunction.INSTANCE)
			.toList();
	}

	private enum ResultsToObjectsFunction implements Function<String, Object[]> {
		INSTANCE;
		@Override public Object[] apply(String line) {
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
		URL url = Resources.getResource(Tests_NGAW2.class, D_DIR + resource);
		return FluentIterable
			.from(Resources.readLines(url, StandardCharsets.UTF_8))
			.skip(1)
			.transform(ArgsToInputFunction.INSTANCE)
			.toList();
	}

	private enum ArgsToInputFunction implements Function<String, GmmInput> {
		INSTANCE;
		@Override public GmmInput apply(String line) {

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
				.z2p5(it.next())
				.z1p0(it.next())
				.build();
		}
	}

}
