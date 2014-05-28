package org.opensha.eq.forecast;

import static org.opensha.util.Parsing.*;
import static org.opensha.eq.forecast.SourceAttribute.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import static java.util.logging.Level.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.Magnitudes;
import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.Location;
import org.opensha.mfd.GaussianMFD;
import org.opensha.mfd.GutenbergRichterMFD;
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
import org.opensha.util.Logging;
import org.opensha.util.Parsing;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/*
 * Non-validating fault source parser; results are undefined for multiple
 * entries for which there should only be singletons (e.g. MagUncertainty)
 * 
 * NOTE: parser 'Attributes' are reused and cannot be stored
 * NOTE: not thread safe
 * 
 * @author Peter Powers
 */
class GridSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(GridSourceParser.class);
	private static final String LF = LINE_SEPARATOR.value();
	private final SAXParser sax;

	private Locator locator;

	private GridSourceSet sources;

	// Data applying to all sources
	private Map<MFD_Type, MFD_Data> mfdDataMap = null;

	// This will build an entire GridSourceSet
	private GridSourceSet.Builder sourceBuilder;
	
	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;
	
	// Per-node MFD
	IncrementalMFD nodeMFD = null;
	
	
	private GridSourceParser(SAXParser sax) {
		this.sax = checkNotNull(sax);
		sources = new GridSourceSet();
	}
	
	static GridSourceParser create(SAXParser sax) {
		return new GridSourceParser(sax);
	}
	
	GridSourceSet parse(File f) throws SAXException, IOException {
		sax.parse(f, this);
		return sources;
	}
	
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

		switch (e) {

			case GRID_SOURCE_SET:
				String name = atts.getValue("file");
				double weight = Double.valueOf(atts.getValue("weight"));
				sourceBuilder = new GridSourceSet.Builder();
				sourceBuilder.name(name);
				if (log.isLoggable(INFO)) log.info("Building: " + name + " weight=" + weight);
				break;
				
			case SOURCE_PROPERTIES:
				sourceBuilder.depthMap(stringToValueValueWeightMap(atts.getValue("depthMap")));
				sourceBuilder.mechs(stringToEnumWeightMap(atts.getValue("mechs"), FocalMech.class));
				break;
				
			case SETTINGS:
				mfdDataMap = Maps.newEnumMap(MFD_Type.class);
				break;
				
			case MAG_FREQ_DIST:
				saveDefaultMFDdata(atts);
				break;
				
			case NODE:
				readingLoc = true;
				locBuilder = new StringBuilder();
				nodeMFD = processNode(atts);
				break;
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

		switch (e) {

			case SETTINGS:
				checkState(mfdDataMap.isEmpty(), "No default MFDs are defined");
				break;
			
			case NODE:
				readingLoc = false;
				sourceBuilder.location(Location.fromString(locBuilder.toString()), nodeMFD);
				break;
		}
	}

	@Override
	public void characters(char ch[], int start, int length)
			throws SAXException {
		if (readingLoc) locBuilder.append(ch, start, length);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}
	
	private void saveDefaultMFDdata(Attributes atts) {
		MFD_Type type = MFD_Type.valueOf(atts.getValue(TYPE.toString()));
		checkState(!mfdDataMap.containsKey(type), "%s defualt already defined", type);
		
		switch (type) {
			case GR:
				MFD_Data_GR grDat = new MFD_Data_GR();
				grDat.b = Double.valueOf(atts.getValue(B.toString()));
				grDat.mMin = Double.valueOf(atts.getValue(M_MIN.toString()));
				grDat.mMax = Double.valueOf(atts.getValue(M_MAX.toString()));
				grDat.dMag = Double.valueOf(atts.getValue(D_MAG.toString()));
				mfdDataMap.put(type, grDat);
				break;
			case SINGLE:
				MFD_Data_SINGLE singleDat = new MFD_Data_SINGLE();
				singleDat.m = Double.valueOf(atts.getValue(M.toString()));
				mfdDataMap.put(type, singleDat);
				break;
			case INCR:
				MFD_Data_INCR incrDat = new MFD_Data_INCR();
				incrDat.mags = Parsing.toDoubleArray(atts.getValue(MAGS.toString()));
				mfdDataMap.put(type, incrDat);
				break;
			case GR_TAPER:
				throw new UnsupportedOperationException("GR_TAPER not yet implemented");
				
		}
	}
	
	// TODO need to test if things like MFD_Type are missing from node if more
	// than 1 default defined, type string is incorrect etc...
	private IncrementalMFD processNode(Attributes atts) {
		String typeAtt = atts.getValue(TYPE.toString());
		// Iterables.getOnlyElement() throws IllegalArgumentException if
		// mfdDataMap does not contain a single entry, which is required if
		// a node does not define a type
		MFD_Type type = (typeAtt == null) ? Iterables.getOnlyElement(mfdDataMap.keySet()) : MFD_Type.valueOf(typeAtt);
		checkState(type != null, "MFD description incomplete");
		
		switch (type) {
			case GR:
				
				MFD_Data_GR grDat = (MFD_Data_GR) mfdDataMap.get(type);
				
				// must have a-value
				double aGR = Double.valueOf(atts.getValue(A.toString()));
				String bStr = atts.getValue(B.toString());
				String mMinStr = atts.getValue(M_MIN.toString());
				String mMaxStr = atts.getValue(M_MAX.toString());
				String dMagStr = atts.getValue(D_MAG.toString());
				
				double b = (bStr == null) ? grDat.b : Double.valueOf(bStr);
				double mMin = (mMinStr == null) ? grDat.mMin : Double.valueOf(mMinStr);
				double mMax = (mMaxStr == null) ? grDat.mMax : Double.valueOf(mMaxStr);
				double dMag = (dMagStr == null) ? grDat.dMag : Double.valueOf(dMagStr);
				int nMag = MFDs.magCount(mMin, mMax, dMag);
				IncrementalMFD mfdGR = MFDs.newGutenbergRichterMFD(mMin, dMag, nMag, b, 1.0);
				mfdGR.scaleToIncrRate(mMin, MFDs.incrRate(aGR, b, mMin));
				return mfdGR;

			case INCR:
				MFD_Data_INCR incrDat = (MFD_Data_INCR) mfdDataMap.get(type);
				
				// must have rates
				double[] rates = Parsing.toDoubleArray(atts.getValue(RATES.toString()));
				
				String magsStr = atts.getValue(MAGS.toString());
				double[] mags = (magsStr == null) ? incrDat.mags : Parsing.toDoubleArray(magsStr);
				
				return MFDs.newIncrementalMFD(mags, rates);
				
			case SINGLE:
				MFD_Data_SINGLE singleDat = (MFD_Data_SINGLE) mfdDataMap.get(type);
				
				// must have a-value
				double aSINGLE = Double.valueOf(atts.getValue(A.toString()));
				
				String mStr = atts.getValue(M.toString());
				double m = (mStr == null) ? singleDat.m : Double.valueOf(mStr);
				
				return MFDs.newSingleMFD(m, aSINGLE);
			
			case GR_TAPER:
				throw new UnsupportedOperationException("GR_TAPER not yet implemented");
				
		}
		return null;
				
	}
		

	private static List<IncrementalMFD> buildMFD(Attributes atts,
			MagUncertainty unc) {
		MFD_Type type = MFD_Type.valueOf(atts.getValue("type"));
		switch (type) {
			case GR:
				return buildGR(atts, unc);
			case INCR:
				System.out.println("MFD: INCR");
				return null;
			case SINGLE:
				return buildSingle(atts, unc);
			case GR_TAPER:
				System.out.println("MFD: GR_TAPER");
				return null;
			default:
				throw new IllegalStateException("Unhandled MFD type: " + type);
		}
	}

	/*
	 * Builds GR MFDs. Method will throw IllegalStateException if attribute
	 * values yield an MFD with no magnitude bins.
	 */
	private static List<IncrementalMFD> buildGR(Attributes atts,
			MagUncertainty unc) {
		double a = Parsing.readDouble(SourceAttribute.A, atts);
		double b = Parsing.readDouble(SourceAttribute.B, atts);
		double mMin = Parsing.readDouble(SourceAttribute.M_MIN, atts);
		double mMax = Parsing.readDouble(SourceAttribute.M_MAX, atts);
		double dMag = Parsing.readDouble(SourceAttribute.D_MAG, atts);
		double weight = Parsing.readDouble(SourceAttribute.WEIGHT, atts);
		boolean floats = Parsing.readBoolean(SourceAttribute.FLOATS, atts);

		int nMag = MFDs.magCount(mMin, mMax, dMag);
		checkState(nMag > 0, "GR MFD with no mags");
		double tmr = MFDs.totalMoRate(mMin, nMag, dMag, a, b);

		List<IncrementalMFD> mfds = Lists.newArrayList();

		if (log.isLoggable(INFO)) log.info("MFD: GR");
		
		if (unc.hasEpistemic) {
			for (int i = 0; i < unc.epiCount; i++) {
				// update mMax and nMag
				double mMaxEpi = mMax + unc.epiDeltas[i];
				int nMagEpi =  MFDs.magCount(mMin, mMaxEpi, dMag);
				if (nMagEpi > 0) {
					double weightEpi = weight * unc.epiWeights[i];
					
					// epi branches preserve Mo between mMin and dMag(nMag-1),
					// not mMax to ensure that Mo is 'spent' on earthquakes
					// represented by the epi GR distribution with adj. mMax.
					
					GutenbergRichterMFD mfd = MFDs
						.newGutenbergRichterMoBalancedMFD(mMin, dMag, nMagEpi,
							b, tmr * weightEpi);
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
				mMin, dMag, nMag, b, tmr * weight);
			mfds.add(mfd);
			if (log.isLoggable(FINE)) {
				log.fine(new StringBuilder().append(mfd.getMetadataString())
					.toString());
			}
		}
		return mfds;
	}

	/*
	 * Builds single MFDs
	 */
	private static List<IncrementalMFD> buildSingle(Attributes atts,
			MagUncertainty unc) {
		double a = Parsing.readDouble(SourceAttribute.A, atts);
		double m = Parsing.readDouble(SourceAttribute.M, atts);
		double weight = Parsing.readDouble(SourceAttribute.WEIGHT, atts);
		boolean floats = Parsing.readBoolean(SourceAttribute.FLOATS, atts);

		List<IncrementalMFD> mfds = Lists.newArrayList();

		if (log.isLoggable(INFO)) log.info("MFD: SINGLE");

		// total moment rate
		double tmr = a * Magnitudes.magToMoment(m);
		// total event rate
		double tcr = a;

		// @formatter:off
		// loop over epistemic uncertainties
		if (unc.hasEpistemic) {
			for (int i = 0; i < unc.epiCount; i++) {

				double epiMag = m + unc.epiDeltas[i];
				double mfdWeight = weight * unc.epiWeights[i];

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
					// aleatory switch added to handle floating M7.4 CH ruptures
					// on Wasatch that do not have long aleatory tails, may
					// be used elsewhere
					
					double moRate = tmr * mfdWeight;
					IncrementalMFD mfd = MFDs.newSingleMoBalancedMFD(epiMag, moRate);
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
			if (unc.hasAleatory) {
				GaussianMFD mfd = (unc.moBalance) ? 
					MFDs.newGaussianMoBalancedMFD(m, unc.aleaSigma, unc.aleaCount, weight * tmr) :
					MFDs.newGaussianMFD(m, unc.aleaSigma, unc.aleaCount, weight * tcr);
				mfds.add(mfd);
				if (log.isLoggable(FINE)) {
					log.fine(new StringBuilder("Single MFD [-epi +ale]: ")
						.append(LF).append(mfd.getMetadataString()).toString());
				}
			} else {
				IncrementalMFD mfd = MFDs.newSingleMoBalancedMFD(m, weight * a);
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

	// marker interface for MFD data containers defined below
	private static interface MFD_Data {}
	
	private static class MFD_Data_GR implements MFD_Data {
		private double b;
		private double mMin;
		private double mMax;
		private double dMag;
	}
	
	private static class MFD_Data_SINGLE implements MFD_Data {
		private double m;
	}
	
	private static class MFD_Data_INCR implements MFD_Data {
		private double[] mags;
	}

}
