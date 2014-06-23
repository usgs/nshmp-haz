package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static org.opensha.eq.forecast.SourceAttribute.DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.DIP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.RAKE;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceAttribute.WIDTH;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.Magnitudes;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.LocationList;
import org.opensha.mfd.GaussianMfd;
import org.opensha.mfd.GutenbergRichterMfd;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.MfdType;
import org.opensha.mfd.Mfds;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Lists;

/*
 * Non-validating fault source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class FaultParser extends DefaultHandler {

	private final Logger log = Logger.getLogger(FaultParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	private GmmSet gmmSet;

	private FaultSourceSet sourceSet;
	private FaultSourceSet.Builder sourceSetBuilder;
	private FaultSource.Builder sourceBuilder;

	private MagScalingRelationship msr;

	// Data applying to all sourceSet
	private MagUncertainty unc = null;
	private Map<String, String> epiAtts = null;
	private Map<String, String> aleaAtts = null;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MfdHelper mfdHelper;

	// Traces are the only text content in source files
	private boolean readingTrace = false;
	private StringBuilder traceBuilder = null;

	private FaultParser(SAXParser sax) {
		this.sax = sax;
	}

	static FaultParser create(SAXParser sax) {
		return new FaultParser(checkNotNull(sax));
	}

	FaultSourceSet parse(InputStream in, GmmSet gmmSet) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		sax.parse(in, this);
		checkState(sourceSet.size() > 0, "FaultSourceSet is empty");
		used = true;
		return sourceSet;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case FAULT_SOURCE_SET:
					String name = readString(NAME, atts);
					double weight = readDouble(WEIGHT, atts);
					sourceSetBuilder = new FaultSourceSet.Builder();
					sourceSetBuilder.name(name).weight(weight).gmms(gmmSet);
					if (log.isLoggable(FINE)) {
						log.fine("");
						log.fine("       Name: " + name);
						log.fine("     Weight: " + weight);
					}
					break;

				case MAG_FREQ_DIST_REF:
					mfdHelper = MfdHelper.create();
					parsingDefaultMFDs = true;
					break;

				case EPISTEMIC:
					epiAtts = toMap(atts);
					break;

				case ALEATORY:
					aleaAtts = toMap(atts);
					break;

				case SOURCE_PROPERTIES:
					MagScalingType msrType = readEnum(MAG_SCALING, atts, MagScalingType.class);
					sourceSetBuilder.magScaling(msrType);
					msr = msrType.instance();
					log.fine("Mag scaling: " + msrType);
					break;

				case SOURCE:
					String srcName = readString(NAME, atts);
					sourceBuilder = new FaultSource.Builder()
						.name(srcName)
						.magScaling(msr);
					log.fine("     Source: " + srcName);
					break;

				case MAG_FREQ_DIST:
					if (parsingDefaultMFDs) {
						mfdHelper.addDefault(atts);
						break;
					}
					sourceBuilder.mfds(buildMFD(atts, unc, sourceSetBuilder.weight));
					break;

				case GEOMETRY:
					sourceBuilder.depth(readDouble(DEPTH, atts))
						.dip(readDouble(DIP, atts))
						.rake(readDouble(RAKE, atts))
						.width(readDouble(WIDTH, atts));
					break;

				case TRACE:
					readingTrace = true;
					traceBuilder = new StringBuilder();
					break;
			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}
	}

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = false;
					break;

				case SETTINGS:
					// may not have mag uncertainty element so create the
					// uncertainty container upon leaving 'Settings'
					unc = MagUncertainty.create(epiAtts, aleaAtts);
					if (log.isLoggable(FINE)) log.fine(unc.toString());
					break;

				case TRACE:
					readingTrace = false;
					sourceBuilder.trace(LocationList.fromString(traceBuilder.toString()));
					break;

				case SOURCE:
					sourceSetBuilder.source(sourceBuilder.buildFaultSource());
					log.finer(""); // insert blank line for detailed source output
					break;

				case FAULT_SOURCE_SET:
					sourceSet = sourceSetBuilder.buildFaultSet();
					break;
			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}
	}

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	private List<IncrementalMfd> buildMFD(Attributes atts, MagUncertainty unc, double setWeight) {
		MfdType type = readEnum(TYPE, atts, MfdType.class);
		switch (type) {
			case GR:
				return buildGR(mfdHelper.getGR(atts), unc, setWeight);
			case SINGLE:
				return buildSingle(mfdHelper.getSingle(atts), unc, setWeight);
			default:
				throw new IllegalStateException(type + " not yet implemented");
		}
	}

	/*
	 * Builds GR Mfds. Method will throw IllegalStateException if attribute
	 * values yield an MFD with no magnitude bins.
	 */
	private List<IncrementalMfd> buildGR(MfdHelper.GR_Data data, MagUncertainty unc,
			double setWeight) {

		int nMag = Mfds.magCount(data.mMin, data.mMax, data.dMag);
		checkState(nMag > 0, "GR MFD with no mags [%s]", sourceBuilder.name);

		double tmr = Mfds.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);

		List<IncrementalMfd> mfds = Lists.newArrayList();

		// this was handled previously by GR_Data.hasMagExceptions()
		// TODO edge cases (Rush Peak in brange.3dip.gr.in) suggest
		// data.mMin should be used instead of unc.epiCutoff
		boolean uncertAllowed = unc.hasEpistemic && (data.mMax + unc.epiDeltas[0]) >= unc.epiCutoff;

		if (unc.hasEpistemic && uncertAllowed) {
			for (int i = 0; i < unc.epiCount; i++) {
				// update mMax and nMag
				double mMaxEpi = data.mMax + unc.epiDeltas[i];
				int nMagEpi = Mfds.magCount(data.mMin, mMaxEpi, data.dMag);
				if (nMagEpi > 0) {
					double weightEpi = data.weight * unc.epiWeights[i] * setWeight;

					// epi branches preserve Mo between mMin and dMag(nMag-1),
					// not mMax to ensure that Mo is 'spent' on earthquakes
					// represented by the epi GR distribution with adj. mMax.

					GutenbergRichterMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin,
						data.dMag, nMagEpi, data.b, tmr * weightEpi);
					mfds.add(mfd);
					log.finer("   MFD type: GR [+epi -alea] " + epiBranch(i));
					if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
				} else {
					log.warning("GR MFD epi branch with no mags [" + sourceBuilder.name + "]");

				}
			}
		} else {
			double weight = data.weight * setWeight;
			GutenbergRichterMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin, data.dMag,
				nMag, data.b, tmr * weight);
			mfds.add(mfd);
			log.finer("   MFD type: GR [-epi -alea]");
			if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
		}
		return mfds;
	}

	/*
	 * Builds single Mfds
	 */
	private List<IncrementalMfd> buildSingle(MfdHelper.SingleData data, MagUncertainty unc,
			double setWeight) {

		List<IncrementalMfd> mfds = Lists.newArrayList();

		// total moment rate
		double tmr = data.a * Magnitudes.magToMoment(data.m);
		// total event rate
		double tcr = data.a;

		// this was handled previously by GR_Data.hasMagExceptions()
		// need to catch the single floaters that are less than 6.5
		// note: unc.epiDeltas[0] may be null
		double minUncertMag = data.m + (unc.hasEpistemic ? unc.epiDeltas[0] : 0.0);
		boolean uncertAllowed = !((minUncertMag < unc.epiCutoff) && data.floats);

		// @formatter:off
		// loop over epistemic uncertainties
		if (unc.hasEpistemic && uncertAllowed) {
			for (int i = 0; i < unc.epiCount; i++) {

				double epiMag = data.m + unc.epiDeltas[i];
				double mfdWeight = data.weight * unc.epiWeights[i] * setWeight;

				if (unc.hasAleatory) {
					GaussianMfd mfd = (unc.moBalance) ? 
						Mfds.newGaussianMoBalancedMFD(epiMag, unc.aleaSigma, unc.aleaCount, mfdWeight * tmr) :
						Mfds.newGaussianMFD(epiMag, unc.aleaSigma, unc.aleaCount, mfdWeight * tcr);
					mfds.add(mfd);
					log.finer("   MFD type: SINGLE [+epi +alea] " + epiBranch(i));
					if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
				} else {
					
					// single Mfds with epi uncertainty are moment balanced at the
					// central/single magnitude of the distribution
					
					double moRate = tmr * mfdWeight;
					IncrementalMfd mfd = Mfds.newSingleMoBalancedMFD(epiMag, moRate, data.floats);
					mfds.add(mfd);
					log.finer("   MFD type: SINGLE [+epi -alea] " + epiBranch(i));
					if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
				}
			}
		} else {
			double mfdWeight = data.weight * setWeight;
			if (unc.hasAleatory && uncertAllowed) {
				GaussianMfd mfd = (unc.moBalance) ? 
					Mfds.newGaussianMoBalancedMFD(data.m, unc.aleaSigma, unc.aleaCount, mfdWeight * tmr) :
					Mfds.newGaussianMFD(data.m, unc.aleaSigma, unc.aleaCount, mfdWeight * tcr);
				mfds.add(mfd);
				log.finer("   MFD type: SINGLE [-epi +alea]");
				if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
			} else {
				IncrementalMfd mfd = Mfds.newSingleMFD(data.m, mfdWeight * data.a, data.floats);
				mfds.add(mfd);
				log.finer("   MFD type: SINGLE [-epi -alea]");
				if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
			}
		}
		// @formatter:on
		return mfds;
	}
	
	private static String epiBranch(int i) {
		return (i == 0) ? "(M-epi)" : (i == 2) ? "(M+epi)" : "(M)";
	}

}
