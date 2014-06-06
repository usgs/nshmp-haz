package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.forecast.SourceAttribute.*;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.toMap;
import static java.util.logging.Level.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.Magnitudes;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.LocationList;
import org.opensha.mfd.GaussianMFD;
import org.opensha.mfd.GutenbergRichterMFD;
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
import org.opensha.util.Logging;
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
class FaultSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(FaultSourceParser.class);
	private static final String LF = LINE_SEPARATOR.value();
	private final SAXParser sax;

	private Locator locator;

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
	private MFD_Helper mfdHelper;
	
	// Traces are the only text content in source files
	private boolean readingTrace = false;
	private StringBuilder traceBuilder = null;
	
	
	private FaultSourceParser(SAXParser sax) {
		this.sax = sax;
	}
	
	static FaultSourceParser create(SAXParser sax) {
		return new FaultSourceParser(checkNotNull(sax));
	}
	
	FaultSourceSet parse(File f) throws SAXException, IOException {
		sax.parse(f, this);
		checkState(sourceSet.size() > 0, "FaultSourceSet is empty");
		return sourceSet;
	}
	
	
	// TODO wrap all operations in a sSAXParseException so it gets passed up to logger in Loader
	
	@Override
	@SuppressWarnings("incomplete-switch")
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case FAULT_SOURCE_SET:
					String name = readString(NAME, atts);
					double weight = readDouble(WEIGHT, atts);
					sourceSetBuilder = new FaultSourceSet.Builder();
					sourceSetBuilder.name(name);
					sourceSetBuilder.weight(weight);
					if (log.isLoggable(INFO)) {
						log.info("Building fault set: " + name + " weight=" + weight);
					}
					break;
					
				case MAG_FREQ_DIST_REF:
					mfdHelper = MFD_Helper.create();
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
					break;
					
				case SOURCE:
					String srcName = readString(NAME, atts);
					sourceBuilder = new FaultSource.Builder();
					sourceBuilder.name(srcName);
					sourceBuilder.magScaling(msr);
					if (log.isLoggable(INFO)) {
						log.info("Creating source: " + srcName);
					}
					break;
	
				case MAG_FREQ_DIST:
					if (parsingDefaultMFDs) {
						mfdHelper.addDefault(atts);
						break;
					}
					sourceBuilder.mfds(buildMFD(atts, unc, sourceSet.weight()));
					break;
	
				case GEOMETRY:
					sourceBuilder.depth(readDouble(DEPTH, atts));
					sourceBuilder.dip(readDouble(DIP, atts));
					sourceBuilder.rake(readDouble(RAKE, atts));
					sourceBuilder.width(readDouble(WIDTH, atts));
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

	@Override
	@SuppressWarnings("incomplete-switch")
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}
		
		try {
			switch (e) {
	
				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = false;
					break;
					
				case SETTINGS:
					// may not have mag uncertainty element so create the uncertainty
					// container upon leaving 'Settings'
					unc = MagUncertainty.create(epiAtts, aleaAtts);
					if (log.isLoggable(FINE)) log.fine(unc.toString());
					break;
	
				case TRACE:
					readingTrace = false;
					sourceBuilder.trace(LocationList.fromString(traceBuilder.toString()));
					break;
					
				case SOURCE:
					sourceSetBuilder.source(sourceBuilder.buildFaultSource());
					break;
					
				case FAULT_SOURCE_SET:
					sourceSet = sourceSetBuilder.buildFaultSet();
					
			}
			
		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}
	}

	@Override
	public void characters(char ch[], int start, int length)
			throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}
		
	private List<IncrementalMFD> buildMFD(Attributes atts, MagUncertainty unc, double setWeight) {
		MFD_Type type = readEnum(TYPE, atts, MFD_Type.class);
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
	 * Builds GR MFDs. Method will throw IllegalStateException if attribute
	 * values yield an MFD with no magnitude bins.
	 */
	private static List<IncrementalMFD> buildGR(MFD_Helper.GR_Data data, MagUncertainty unc,
			double setWeight) {

		int nMag = MFDs.magCount(data.mMin, data.mMax, data.dMag);
		checkState(nMag > 0, "GR MFD with no mags");
		double tmr = MFDs.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);

		List<IncrementalMFD> mfds = Lists.newArrayList();

		if (log.isLoggable(INFO)) log.info("MFD: GR");
		
		if (unc.hasEpistemic) {
			for (int i = 0; i < unc.epiCount; i++) {
				// update mMax and nMag
				double mMaxEpi = data.mMax + unc.epiDeltas[i];
				int nMagEpi =  MFDs.magCount(data.mMin, mMaxEpi, data.dMag);
				if (nMagEpi > 0) {
					double weightEpi = setWeight * data.weight * unc.epiWeights[i];
					
					// epi branches preserve Mo between mMin and dMag(nMag-1),
					// not mMax to ensure that Mo is 'spent' on earthquakes
					// represented by the epi GR distribution with adj. mMax.
					
					GutenbergRichterMFD mfd = MFDs.newGutenbergRichterMoBalancedMFD(data.mMin,
						data.dMag, nMagEpi, data.b, tmr * weightEpi);
					mfds.add(mfd);
					if (log.isLoggable(FINE)) {
						log.fine(new StringBuilder()
							.append("M-branch ").append(i + 1)
							.append(": ").append(LF)
							.append(mfd.getMetadataString()).toString());
					}
				} else {
					log.warning("GR MFD epi branch with no mags");
				}
			}
		} else {
			GutenbergRichterMFD mfd = MFDs.newGutenbergRichterMoBalancedMFD(
				data.mMin, data.dMag, nMag, data.b, tmr * data.weight * setWeight);
			mfds.add(mfd);
			if (log.isLoggable(FINE)) {
				log.fine(new StringBuilder().append(mfd.getMetadataString()).toString());
			}
		}
		return mfds;
	}

	/*
	 * Builds single MFDs
	 */
	private static List<IncrementalMFD> buildSingle(MFD_Helper.SingleData data, MagUncertainty unc,
			double setWeight) {

		List<IncrementalMFD> mfds = Lists.newArrayList();

		if (log.isLoggable(INFO)) log.info("MFD: SINGLE");

		// total moment rate
		double tmr = data.a * Magnitudes.magToMoment(data.m);
		// total event rate
		double tcr = data.a;

		// @formatter:off
		// loop over epistemic uncertainties
		if (unc.hasEpistemic) {
			for (int i = 0; i < unc.epiCount; i++) {

				double epiMag = data.m + unc.epiDeltas[i];
				double mfdWeight = setWeight * data.weight * unc.epiWeights[i];

				if (unc.hasAleatory) {
					GaussianMFD mfd = (unc.moBalance) ? 
						MFDs.newGaussianMoBalancedMFD(epiMag, unc.aleaSigma, unc.aleaCount, mfdWeight * tmr) :
						MFDs.newGaussianMFD(epiMag, unc.aleaSigma, unc.aleaCount, mfdWeight * tcr);
					mfds.add(mfd);
					if (log.isLoggable(FINE)) {
						log.fine(new StringBuilder(
							"[+epi +ale], M-branch ").append(i + 1)
							.append(": ").append(LF)
							.append(mfd.getMetadataString()).toString());
					}
				} else {
					
					// single MFDs with epi uncertainty are moment balanced at the
					// central/single magnitude of the distribution
					
					double moRate = tmr * mfdWeight;
					IncrementalMFD mfd = MFDs.newSingleMoBalancedMFD(epiMag, moRate, data.floats);
					mfds.add(mfd);
					if (log.isLoggable(FINE)) {
						log.fine(new StringBuilder(
							"[+epi -ale], M-branch ").append(i + 1)
							.append(": ").append(LF)
							.append(mfd.getMetadataString()).toString());
					}
				}
			}
		} else {
			double mfdWeight = setWeight * data.weight;
			if (unc.hasAleatory) {
				GaussianMFD mfd = (unc.moBalance) ? 
					MFDs.newGaussianMoBalancedMFD(data.m, unc.aleaSigma, unc.aleaCount, mfdWeight * tmr) :
					MFDs.newGaussianMFD(data.m, unc.aleaSigma, unc.aleaCount, mfdWeight * tcr);
				mfds.add(mfd);
				if (log.isLoggable(FINE)) {
					log.fine(new StringBuilder("Single MFD [-epi +ale]: ")
						.append(LF).append(mfd.getMetadataString()).toString());
				}
			} else {
				IncrementalMFD mfd = MFDs.newSingleMFD(data.m, mfdWeight * data.a, data.floats);
				mfds.add(mfd);
				if (log.isLoggable(FINE)) {
					log.fine(new StringBuilder("Single MFD [-epi -ale]: ")
						.append(LF).append(mfd.getMetadataString()).toString());
				}
			}
		}
		// @formatter:on
		return mfds;
	}
	
	// TODO clean
//	/*
//	 * Returns true if (1) multi-mag and mMax-epi < 6.5 or (2) single-mag and
//	 * mMax-epi-2s < 6.5
//	 */
//	boolean hasMagExceptions(Logger log, FaultData fd, MagUncertaintyData md) {
//		if (nMag > 1) {
//			// for multi mag consider only epistemic uncertainty
//			double mMaxAdj = mMax + md.epiDeltas[0];
//			if (mMaxAdj < 6.5) {
//				StringBuilder sb = new StringBuilder()
//					.append("Multi mag GR mMax [").append(mMax)
//					.append("] with epistemic unc. [").append(mMaxAdj)
//					.append("] is \u003C 6.5");
//				appendFaultDat(sb, fd);
//				log.warning(sb.toString());
//				return true;
//			}
//		} else if (nMag == 1) {
//			// for single mag consider epistemic and aleatory uncertainty
//			double mMaxAdj = md.aleaMinMag(mMax + md.epiDeltas[0]);
//			if (mMaxAdj < 6.5) {
//				StringBuilder sb = new StringBuilder()
//					.append("Single mag GR mMax [").append(mMax)
//					.append("] with epistemic and aleatory unc. [")
//					.append(mMaxAdj).append("] is \u003C 6.5");
//				appendFaultDat(sb, fd);
//				log.warning(sb.toString());
//				return true;
//			}
//		} else {
//			// log empty mfd
//			StringBuilder sb = new StringBuilder()
//				.append("GR MFD with no mags");
//			appendFaultDat(sb, fd);
//			log.warning(sb.toString());
//		}
//		return false;
//	}


}
