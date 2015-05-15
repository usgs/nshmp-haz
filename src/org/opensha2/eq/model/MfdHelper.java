package org.opensha2.eq.model;

import static org.opensha2.eq.model.SourceAttribute.*;
import static org.opensha2.util.Parsing.readBoolean;
import static org.opensha2.util.Parsing.readDouble;
import static org.opensha2.util.Parsing.readString;
import static org.opensha2.util.Parsing.toDoubleArray;

import org.opensha2.mfd.MfdType;
import org.xml.sax.Attributes;

/*
 * MFD data handler class. Stores default data and creates copies with
 * overridden (non-default) fields. This class ensures that all required
 * attributes for default Mfds are present.
 */
class MfdHelper {

	// mfd data instances
	private SingleData singleDefault;
	private GR_Data grDefault;
	private IncrData incrDefault;
	private TaperData taperDefault;

	static MfdHelper create() {
		return new MfdHelper();
	}

	private MfdHelper() {}

	/* 
	 * Add a new default MFD and return its spacing. Single MFD
	 * returns NaN
	 */
	double addDefault(Attributes atts) {
		MfdType type = MfdType.valueOf(atts.getValue("type"));
		switch (type) {
			case GR:
				grDefault = new GR_Data(atts);
				return grDefault.dMag;
			case INCR:
				incrDefault = new IncrData(atts);
				return incrDefault.mags[1] - incrDefault.mags[0];
			case SINGLE:
				singleDefault = new SingleData(atts);
				return 0.0;
			case GR_TAPER:
				taperDefault = new TaperData(atts);
				return taperDefault.dMag;
			default:
				throw new IllegalArgumentException("Unknown MFD type: " + type);
		}
	}

	SingleData getSingle(Attributes atts) {
		return (singleDefault == null) ? new SingleData(atts) : new SingleData(atts, singleDefault);
	}
	
	GR_Data getGR(Attributes atts) {
		return (grDefault == null) ? new GR_Data(atts) : new GR_Data(atts, grDefault);
	}
	
	IncrData getIncremental(Attributes atts) {
		return (incrDefault == null) ? new IncrData(atts) : new IncrData(atts, incrDefault);
	}

	TaperData getTapered(Attributes atts) {
		return (taperDefault == null) ? new TaperData(atts) : new TaperData(atts, taperDefault);
	}

	static class SingleData {

		final double a;
		final double m;
		final boolean floats;
		final double weight;

		/*
		 * Requires all attributes be present, but does not throw an exception
		 * for extra and unknown attributes.
		 */
		private SingleData(Attributes atts) {
			a = readDouble(A, atts);
			m = readDouble(M, atts);
			floats = readBoolean(FLOATS, atts);
			weight = readDouble(WEIGHT, atts);
		}

		/*
		 * Iterates supplied attributes; any unkown or extra attributes will
		 * result in an exception being thrown.
		 */
		private SingleData(Attributes atts, SingleData ref) {

			// set defaults locally
			double a = ref.a;
			double m = ref.m;
			boolean floats = ref.floats;
			double weight = ref.weight;

			for (int i = 0; i < atts.getLength(); i++) {
				SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
				switch (att) {
					case A:
						a = readDouble(A, atts);
						break;
					case M:
						m = readDouble(M, atts);
						break;
					case FLOATS:
						floats = readBoolean(FLOATS, atts);
						break;
					case WEIGHT:
						weight = readDouble(WEIGHT, atts);
						break;
					case TYPE:
						break; // ignore
					default:
						throw new IllegalStateException("Invalid attribute for SINGLE MFD: " + att);
				}
			}

			// export final fields
			this.a = a;
			this.m = m;
			this.floats = floats;
			this.weight = weight;
		}
	}

	static class GR_Data {
		final double a;
		final double b;
		final double dMag;
		final double mMin;
		final double mMax;
		final double weight;

		private GR_Data(Attributes atts) {
			a = readDouble(A, atts);
			b = readDouble(B, atts);
			dMag = readDouble(D_MAG, atts);
			mMax = readDouble(M_MAX, atts);
			mMin = readDouble(M_MIN, atts);
			weight = readDouble(WEIGHT, atts);
		}

		private GR_Data(Attributes atts, GR_Data ref) {

			// set defaults locally
			double a = ref.a;
			double b = ref.b;
			double dMag = ref.dMag;
			double mMax = ref.mMax;
			double mMin = ref.mMin;
			double weight = ref.weight;

			for (int i = 0; i < atts.getLength(); i++) {
				SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
				switch (att) {
					case A:
						a = readDouble(A, atts);
						break;
					case B:
						b = readDouble(B, atts);
						break;
					case D_MAG:
						dMag = readDouble(D_MAG, atts);
						break;
					case M_MIN:
						mMin = readDouble(M_MIN, atts);
						break;
					case M_MAX:
						mMax = readDouble(M_MAX, atts);
						break;
					case WEIGHT:
						weight = readDouble(WEIGHT, atts);
						break;
					case TYPE:
						break; // ignore
					default:
						throw new IllegalStateException("Invalid attribute for GR MFD: " + att);
				}
			}

			// export final fields
			this.a = a;
			this.b = b;
			this.dMag = dMag;
			this.mMax = mMax;
			this.mMin = mMin;
			this.weight = weight;
		}
	}
	
	static class TaperData {
		final double a;
		final double b;
		final double cMag;
		final double dMag;
		final double mMin;
		final double mMax;
		final double weight;

		private TaperData(Attributes atts) {
			a = readDouble(A, atts);
			b = readDouble(B, atts);
			cMag = readDouble(C_MAG, atts);
			dMag = readDouble(D_MAG, atts);
			mMax = readDouble(M_MAX, atts);
			mMin = readDouble(M_MIN, atts);
			weight = readDouble(WEIGHT, atts);
		}

		private TaperData(Attributes atts, TaperData ref) {

			// set defaults locally
			double a = ref.a;
			double b = ref.b;
			double cMag = ref.cMag;
			double dMag = ref.dMag;
			double mMax = ref.mMax;
			double mMin = ref.mMin;
			double weight = ref.weight;

			for (int i = 0; i < atts.getLength(); i++) {
				SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
				switch (att) {
					case A:
						a = readDouble(A, atts);
						break;
					case B:
						b = readDouble(B, atts);
						break;
					case C_MAG:
						dMag = readDouble(D_MAG, atts);
						break;
					case D_MAG:
						dMag = readDouble(D_MAG, atts);
						break;
					case M_MIN:
						mMin = readDouble(M_MIN, atts);
						break;
					case M_MAX:
						mMax = readDouble(M_MAX, atts);
						break;
					case WEIGHT:
						weight = readDouble(WEIGHT, atts);
						break;
					case TYPE:
						break; // ignore
					default:
						throw new IllegalStateException("Invalid attribute for SINGLE MFD: " + att);
				}
			}

			// export final fields
			this.a = a;
			this.b = b;
			this.cMag = cMag;
			this.dMag = dMag;
			this.mMax = mMax;
			this.mMin = mMin;
			this.weight = weight;
		}
	}

	static class IncrData {
		
		final double[] mags;
		final double[] rates;
		final double weight;
		
		private IncrData(Attributes atts) {
			mags = toDoubleArray(readString(MAGS, atts));
			rates = toDoubleArray(readString(RATES, atts));
			weight = readDouble(WEIGHT, atts);
		}
		
		private IncrData(Attributes atts, IncrData ref) {
			
			// TODO array sizes aren't checked downstream
			// add checks below; also, the values in ref
			// arrays are mutable
			
			// set defaults locally
			double[] mags = ref.mags;
			double[] rates = ref.rates;
			double weight = ref.weight;
			
			for (int i = 0; i < atts.getLength(); i++) {
				SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
				switch (att) {
					case MAGS:
						mags = toDoubleArray(readString(MAGS, atts));
						break;
					case RATES:
						rates = toDoubleArray(readString(RATES, atts));
						break;
					case WEIGHT:
						weight = readDouble(WEIGHT, atts);
						break;
					case TYPE:
						break; // ignore
					case FOCAL_MECH_MAP:
						break; // SYSTEM (UCERF3) grid sources; ignore
					default:
						throw new IllegalStateException("Invalid attribute for INCR MFD: " + att);
				}
			}
			
			// export final fields
			this.mags = mags;
			this.rates = rates;
			this.weight = weight;
		}

	}

}
