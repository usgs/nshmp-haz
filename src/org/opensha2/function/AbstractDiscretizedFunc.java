package org.opensha2.function;

import static com.google.common.base.Preconditions.checkArgument;

import java.awt.geom.Point2D;

/**
 * <b>Title:</b> DiscretizedFunc<p>
 *
 * <b>Description:</b> Abstract implementation of the DiscretizedFuncAPI. Performs standard
 * simple or default functions so that subclasses don't have to keep reimplementing the
 * same function bodies.<p>
 *
 * A Discretized Function is a collection of x and y values grouped together as
 * the points that describe a function. A discretized form of a function is the
 * only ways computers can represent functions. Instead of having y=x^2, you
 * would have a sample of possible x and y values. <p>
 *
 * The basic functions this abstract class implements are:<br>
 * <ul>
 * <li>get, set Name()
 * <li>get, set, Info()
 * <li>get, set, Tolerance()
 * <li>equals() - returns true if all three fields have the same values.
 * </ul>
 *
 * See the interface documentation for further explanation of this framework<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public abstract class AbstractDiscretizedFunc extends AbstractXY_DataSet implements DiscretizedFunc {


	/** Class name used for debbuging */
	protected final static String C = "DiscretizedFunc";
	/** if true print out debugging statements */
	protected final static boolean D = false;


	/**
	 * The tolerance allowed in specifying a x-value near a real x-value,
	 * so that the real x-value is used. Note that the tolerance must be smaller
	 * than 1/2 the delta between data points for evenly discretized function, no
	 * restriction for arb discretized function, no standard delta.
	 */
	protected double tolerance = 0.0;

	/**Returns the tolerance of this function. */
	public double getTolerance() { return tolerance; }
	/**
	 * Sets the tolerance of this function.
	 * @throws IllegalArgumentException if tolerance is less than zero
	 */
	public void setTolerance(double newTolerance) {
		checkArgument(newTolerance >= 0, "Tolerance must be larger or equal to 0");
		tolerance = newTolerance;
	}
	
	private boolean areBothNull(String first, String second) {
		return first == null && second == null;
	}
	
	private boolean isOneNull(String first, String second) {
		return first == null || second == null;
	}
	
	private boolean isSameWithNull(String first, String second) {
		if (areBothNull(first, second))
			return true;
		if (isOneNull(first, second))
			return false;
		return first.equals(second);
	}

	/**
	 * Default equals for all Discretized Functions. Determines if two functions
	 * are the same by comparing that the name, info, and values are the same.
	 */
	public boolean equals(Object obj){
//		if (true)
//			return true;
		if (this == obj)
			return true;
		if (!(obj instanceof DiscretizedFunc))
			return false;
		DiscretizedFunc function = (DiscretizedFunc)obj;
		
		// now check names equal
		if (!isSameWithNull(name(), function.name()))
			return false;
			
		if ((name() == null && function.name() != null) ||
				(name() != null && !name().equals(function.name() )))
			return false;

		if( D ) {
			String S = C + ": equals(): ";
			System.out.println(S + "This info = " + getInfo() );
			System.out.println(S + "New info = " + function.getInfo() );

		}

		// now check info equal
		if (!isSameWithNull(getInfo(), function.getInfo()))
			return false;
//		if( !getInfo().equals(function.getInfo() )  ) return false;
		
		// now check size
		if (this.getNum() != function.getNum())
			return false;
		
		// now check that the points are equal
		for (int i=0; i<this.getNum(); i++) {
			Point2D pt1 = this.get(i);
			Point2D pt2 = function.get(i);
			if (!pt1.equals(pt2))
				return false;
		}
		return true;
	}

//	public Element toXMLMetadata(Element root) {
//		return toXMLMetadata(root, AbstractDiscretizedFunc.XML_METADATA_NAME);
//	}
//	
//	public Element toXMLMetadata(Element root, String elementName) {
//		Element xml = root.addElement(elementName);
//		
//		xml.addAttribute("info", this.getInfo());
//		xml.addAttribute("name", this.getName());
//
//		xml.addAttribute("tolerance", this.getTolerance() + "");
//		xml.addAttribute("xAxisName", this.getXAxisName());
//		xml.addAttribute("yAxisName", this.getYAxisName());
//		xml.addAttribute("num", this.getNum() + "");
//		xml.addAttribute("minX", this.getMinX() + "");
//		xml.addAttribute("maxX", this.getMaxX() + "");
//		if (this instanceof EvenlyDiscretizedFunc) {
//			xml.addAttribute("delta", ((EvenlyDiscretizedFunc)this).getDelta()+"");
//		}
//
//		Element points = xml.addElement(AbstractDiscretizedFunc.XML_METADATA_POINTS_NAME);
//		for (int i=0; i<this.getNum(); i++) {
//			Element point = points.addElement(AbstractDiscretizedFunc.XML_METADATA_POINT_NAME);
//			point.addAttribute("x", this.getX(i) + "");
//			point.addAttribute("y", this.getY(i) + "");
//		}
//
//		return root;
//	}
//
//	public static AbstractDiscretizedFunc fromXMLMetadata(Element funcElem) {
//		String info = funcElem.attributeValue("info");
//		String name = funcElem.attributeValue("name");
//		String xAxisName = funcElem.attributeValue("xAxisName");
//		String yAxisName = funcElem.attributeValue("yAxisName");
//		
//		AbstractDiscretizedFunc func;
//		Attribute deltaAtt = funcElem.attribute("delta");
//		if (deltaAtt == null) {
//			func = new ArbitrarilyDiscretizedFunc();
//		} else {
//			int num = Integer.parseInt(funcElem.attributeValue("num"));
//			double minX = Double.parseDouble(funcElem.attributeValue("minX"));
//			double delta = Double.parseDouble(deltaAtt.getStringValue());
//			func = new EvenlyDiscretizedFunc(minX, num, delta);
//		}
//
//		double tolerance = Double.parseDouble(funcElem.attributeValue("tolerance"));
//
//		func.setInfo(info);
//		func.setName(name);
//		func.setXAxisName(xAxisName);
//		func.setYAxisName(yAxisName);
//		func.setTolerance(tolerance);
//
//		Element points = funcElem.element(AbstractDiscretizedFunc.XML_METADATA_POINTS_NAME);
//		Iterator<Element> it = points.elementIterator();
//		while (it.hasNext()) {
//			Element point = it.next();
//			double x = Double.parseDouble(point.attributeValue("x"));
//			double y = Double.parseDouble(point.attributeValue("y"));
//			func.set(x, y);
//		}
//
//		return func;
//	}

//	public static void writeSimpleFuncFile(DiscretizedFunc func, String fileName) throws IOException {
//		writeSimpleFuncFile(func, new File(fileName));
//	}
//
//	public static void writeSimpleFuncFile(DiscretizedFunc func, File outFile) throws IOException {
//		FileWriter fr = new FileWriter(outFile);
//		for (int i = 0; i < func.getNum(); ++i)
//			fr.write(func.getX(i) + " " + func.getY(i) + "\n");
//		fr.close();
//	}

//	public static ArbitrarilyDiscretizedFunc loadFuncFromSimpleFile(String fileName) throws FileNotFoundException, IOException {
//		ArrayList<String> fileLines = FileUtils.loadFile(fileName);
//		String dataLine;
//		StringTokenizer st;
//		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
//
//		for(int i=0;i<fileLines.size();++i) {
//			dataLine=(String)fileLines.get(i);
//			st=new StringTokenizer(dataLine);
//			//using the currentIML and currentProb we interpolate the iml or prob
//			//value entered by the user.
//			double x = Double.parseDouble(st.nextToken());
//			double y= Double.parseDouble(st.nextToken());
//			func.set(x, y);
//		}
//		return func;
//	}
//
//	public static ArbitrarilyDiscretizedFunc loadFuncFromSimpleFile(InputStream is) throws FileNotFoundException, IOException {
//	    if (!(is instanceof BufferedInputStream))
//	    	is = new BufferedInputStream(is);
//	    Scanner scanner = new Scanner(is);
//	    
//		StringTokenizer st;
//		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
//		
//	    try {
//	    	while (scanner.hasNextLine()){
//	    		st=new StringTokenizer(scanner.nextLine());
//				//using the currentIML and currentProb we interpolate the iml or prob
//				//value entered by the user.
//				double x = Double.parseDouble(st.nextToken());
//				double y = Double.parseDouble(st.nextToken());
//				func.set(x, y);
//	    	}
//	    }
//	    finally{
//	    	scanner.close();
//	    }
//		return func;
//	}
	
	public double calcSumOfY_Vals() {
		double sum=0;
		for(int i=0; i<getNum();i++) sum += getY(i);
		return sum;
	}
	
	@Override
	public void scale(double val) {
		for(int i=0; i<getNum();i++) this.set(i, val*getY(i));
	}


}
