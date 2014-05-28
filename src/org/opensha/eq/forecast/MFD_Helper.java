package org.opensha.eq.forecast;

import static org.opensha.eq.forecast.SourceAttribute.*;
import static org.opensha.util.Parsing.readBoolean;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.toDoubleArray;


import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.mfd.MFD_Type;
import org.xml.sax.Attributes;

/*
 * MFD data handler class. Stores default data and creates copies with
 * overridden (non-default) fields. This class ensures that all required
 * attributes for default MFDs are present.
 */
class MFD_Helper {

	// mfd data instances
	private SingleData singleDefault;
	private GR_Data grDefault;
	private IncrData incrDefault;

	// private TaperedData taperedDefault;

	static MFD_Helper create() {
		return new MFD_Helper();
	}

	private MFD_Helper() {}

	/* Add a new default MFD */
	void addDefault(Attributes atts) {
		MFD_Type type = MFD_Type.valueOf(atts.getValue("type"));
		switch (type) {
			case GR:
				grDefault = new GR_Data(atts);
				break;
			case INCR:
				incrDefault = new IncrData(atts);
				break;
			case SINGLE:
				singleDefault = new SingleData(atts);
				break;
			case GR_TAPER:
				throw new UnsupportedOperationException("GR_TAPER not yet implemented");
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

	static class SingleData {

		final double a;
		final double m;
		final boolean floats;
		final MagScalingType magScaling;
		final double weight;

		/*
		 * Requires all attributes be present, but does not throw an exception
		 * for extra and unknown attributes.
		 */
		private SingleData(Attributes atts) {
			a = readDouble(A, atts);
			m = readDouble(M, atts);
			floats = readBoolean(FLOATS, atts);
			magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
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
			MagScalingType magScaling = ref.magScaling;
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
					case MAG_SCALING:
						magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
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
			this.magScaling = magScaling;
			this.weight = weight;
		}
	}

	static class GR_Data {
		final double a;
		final double b;
		final double dMag;
		final double mMin;
		final double mMax;
		final MagScalingType magScaling;
		final double weight;

		private GR_Data(Attributes atts) {
			a = readDouble(A, atts);
			b = readDouble(B, atts);
			dMag = readDouble(D_MAG, atts);
			mMax = readDouble(M_MAX, atts);
			mMin = readDouble(M_MIN, atts);
			magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
			weight = readDouble(WEIGHT, atts);
		}

		private GR_Data(Attributes atts, GR_Data ref) {

			// set defaults locally
			double a = ref.a;
			double b = ref.b;
			double dMag = ref.dMag;
			double mMax = ref.mMax;
			double mMin = ref.mMin;
			MagScalingType magScaling = ref.magScaling;
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
					case MAG_SCALING:
						magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
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
			this.dMag = dMag;
			this.mMax = mMax;
			this.mMin = mMin;
			this.magScaling = magScaling;
			this.weight = weight;
		}
	}
	
	static class IncrData {
		
		final double[] mags;
		final double[] rates;
		MagScalingType magScaling;
		final double weight;
		
		private IncrData(Attributes atts) {
			mags = toDoubleArray(readString(MAGS, atts));
			rates = toDoubleArray(readString(RATES, atts));
			magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
			weight = readDouble(WEIGHT, atts);
		}
		
		private IncrData(Attributes atts, IncrData ref) {
			
			// set defaults locally
			double[] mags = ref.mags;
			double[] rates = ref.rates;
			MagScalingType magScaling = ref.magScaling;
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
					case MAG_SCALING:
						magScaling = MagScalingType.valueOf(readString(MAG_SCALING, atts));
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
			this.mags = mags;
			this.rates = rates;
			this.magScaling = magScaling;
			this.weight = weight;
		}

	}

}
