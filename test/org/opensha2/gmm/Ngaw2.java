package org.opensha2.gmm;

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

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class Ngaw2 extends GmmTest {

	private static String GMM_INPUTS = "NGA_inputs.csv";
	private static String GMM_RESULTS = "NGAW2_results.csv";

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

	public Ngaw2(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
		super(index, gmm, imt, exMedian, exSigma);
	}

	/* Result generation sets */
	private static Set<Gmm> gmms = EnumSet.of(ASK_14, BSSA_14, CB_14, CY_14, IDRISS_14);
	private static Set<Imt> imts = EnumSet.of(PGA, SA0P02, SA0P2, SA1P0, SA3P0);

	public static void main(String[] args) throws IOException {
		GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
	}

}
