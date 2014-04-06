package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.forecast.SourceElement.ALEATORY;
import static org.opensha.eq.forecast.SourceElement.EPISTEMIC;
import static org.opensha.eq.forecast.SourceElement.MAG_UNCERTAINTY;
import static org.opensha.util.Parsing.addElement;

import java.util.Arrays;
import java.util.Map;

import org.opensha.eq.Magnitudes;
import org.opensha.util.Parsing;
import org.w3c.dom.Element;

/**
 * Wrapper class for magnitude uncertainty data. Uncertainty
 * flags are initialized based on input data, however, due to quirky nshmp
 * rules, they may be overriden at some point and should always be checked prior
 * to calculation regardless of any uncertainty values present.
 */
public class MagUncertainty {

	boolean hasEpistemic;
	int epiCount;
	double[] epiDeltas;
	double[] epiWeights;
	double epiCutoff;

	boolean hasAleatory;
	int aleaCount;
	double aleaSigma;
	boolean moBalance;
	double aleaCutoff;

	// no public instantiation
	MagUncertainty() {}

	/**
	 * Factory magnitude uncertainty container constructor.
	 * 
	 * @param epiDeltas epistemic change to magnitude (M +/- delta)
	 * @param epiWeights weight for each change; must be same length as {@code epiDeltas}
	 * @param epiCutoff minimum magnitude for which epistemic uncertainty applies, below which it is disabled
	 * @param aleaSigma standard deviation of aleatory Gaussian uncertainty
	 * @param aleaCount number of aleatory uncertainty magnitude bins across a normal distribution
	 * @param moBalance whether to preserve moment across aleatory uncertainty bins
	 * @param aleaCutoff minimum magnitude for which aleatory uncertainty applies, below which it is disabled
	 * @return a magnitude uncertainty container
	 */
	public static MagUncertainty create(double[] epiDeltas,
			double[] epiWeights, double epiCutoff, double aleaSigma,
			int aleaCount, boolean moBalance, double aleaCutoff) {

		MagUncertainty mu = new MagUncertainty();

		checkArgument(epiDeltas.length > 0);
		checkArgument(epiWeights.length > 0);
		checkArgument(epiDeltas.length == epiWeights.length);
		mu.epiDeltas = epiDeltas;
		mu.epiWeights = epiWeights;
		mu.epiCount = mu.epiDeltas.length;
		mu.hasEpistemic = mu.epiCount > 1;
		mu.epiCutoff = Magnitudes.validateMag(epiCutoff);

		checkArgument(aleaSigma >= 0);
		checkArgument(aleaCount < 40);
		mu.aleaSigma = aleaSigma;
		mu.aleaCount = aleaCount;
		mu.moBalance = moBalance;
		mu.hasAleatory = mu.aleaCount > 1 && mu.aleaSigma != 0.0;
		mu.aleaCutoff = Magnitudes.validateMag(aleaCutoff);

		return mu;
	}
	
	/**
	 * Create an exact copy of the supplied magnitude uncertainty container.
	 * TODO this may/should not be needed
	 * @param src to copy
	 * @return a magnitude uncertainty container
	 */
	public static MagUncertainty copyOf(MagUncertainty src) {

		MagUncertainty mu = new MagUncertainty();
		
		mu.hasEpistemic = src.hasEpistemic;
		mu.epiCount = src.epiCount;
		mu.epiDeltas = Arrays.copyOf(src.epiDeltas, src.epiDeltas.length);
		mu.epiWeights =  Arrays.copyOf(src.epiWeights, src.epiWeights.length);
		mu.epiCutoff = src.epiCutoff;
		
		mu.hasAleatory = src.hasAleatory;
		mu.aleaSigma = src.aleaSigma;
		mu.aleaCount = src.aleaCount;
		mu.moBalance = src.moBalance;
		mu.aleaCutoff = src.aleaCutoff;
		
		return mu;
	}


	/* Package-private constructor using XML attribute strings */
	static MagUncertainty create(Map<String, String> epiAtts,
			Map<String, String> aleaAtts) {

		MagUncertainty mu = new MagUncertainty();

		// epistemic
		if (epiAtts != null) {
			mu.epiDeltas = Parsing.toDoubleArray(epiAtts.get("deltas"));
			mu.epiWeights = Parsing.toDoubleArray(epiAtts.get("weights"));
			checkArgument(mu.epiDeltas.length == mu.epiWeights.length,
				"Epistemic deltas and mags are different lengths [%s, %s]",
				mu.epiDeltas.length, mu.epiWeights.length);
			mu.epiCount = mu.epiDeltas.length;
			mu.hasEpistemic = Boolean.valueOf(epiAtts.get("enable")) &&
				mu.epiCount > 1;
		}

		// aleatory
		if (aleaAtts != null) {
			mu.aleaSigma = Double.valueOf(aleaAtts.get("sigma"));
			mu.aleaCount = Integer.valueOf(aleaAtts.get("count"));
			checkArgument(
				mu.aleaCount % 2 == 1,
				"Aleatory bins [%s] should be odd so they center on mean magnitude",
				mu.aleaCount);
			mu.moBalance = Boolean.valueOf(aleaAtts.get("balance"));
			// two ways to kill aleatory
			mu.hasAleatory = Boolean.valueOf(aleaAtts.get("enable")) &&
				mu.aleaCount > 1 && mu.aleaSigma != 0.0;
		}

		return mu;
	}
	
	/**
	 * Returns whether aleatory magnitude uncertainty is enabled.
	 * @return {@code true} if aleatory uncertainty is enabled; {@code false}
	 *         otherwise
	 */
	public boolean hasAleatory() {
		return hasAleatory;
	}

	/**
	 * Returns whether epistemic magnitude uncertainty is enabled.
	 * @return {@code true} if epistemic uncertainty is enabled; {@code false}
	 *         otherwise
	 */
	public boolean hasEpistemic() {
		return hasEpistemic;
	}

	private static final String LF = LINE_SEPARATOR.value();

	@Override
	public String toString() {
		// @formatter:off
		return new StringBuilder()
		.append("MFD Data").append(LF)
		.append("Epistemic unc: ").append(hasEpistemic).append(LF)
		.append("     M deltas: ").append(Arrays.toString(epiDeltas)).append(LF)
		.append("    M weights: ").append(Arrays.toString(epiWeights)).append(LF)
		.append("     M cutoff: ").append(epiCutoff).append(LF)
		.append(" Aleatory unc: ").append(hasAleatory).append(LF)
		.append("      M sigma: ").append(aleaSigma).append(LF)
		.append("      M count: ").append(aleaCount).append(LF)
		.append("   Mo balance: ").append(moBalance).append(LF)
		.append("     M cutoff: ").append(aleaCutoff).toString();
		// @formatter:on
	}
	
	/**
	 * Appends the XML form of this magnitude uncertainty data to the supplied
	 * {@code Element}.
	 * @param node to append to
	 * @return a reference to the newly created {@code Element}
	 */
	public Element appendTo(Element node) {
		if (!hasAleatory && !hasEpistemic) return null;
		Element e = addElement(MAG_UNCERTAINTY, node);
		if (hasEpistemic) {
			Element eEpistemic = addElement(EPISTEMIC, e);
			eEpistemic.setAttribute("deltas", Arrays.toString(epiDeltas));
			eEpistemic.setAttribute("weights", Arrays.toString(epiWeights));
			eEpistemic.setAttribute("cutoff", Double.toString(epiCutoff));
		}
		if (hasAleatory) {
			Element eAleatory = addElement(ALEATORY, e);
			eAleatory.setAttribute("sigma", Double.toString(aleaSigma));
			eAleatory.setAttribute("count", Integer.toString(aleaCount));
			eAleatory.setAttribute("moBalance", Boolean.toString(moBalance));
			eAleatory.setAttribute("cutoff", Double.toString(aleaCutoff));
		}
		return e;
	}
	
	
	
}
