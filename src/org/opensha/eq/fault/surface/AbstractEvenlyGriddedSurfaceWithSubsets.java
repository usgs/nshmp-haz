package org.opensha.eq.fault.surface;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * <b>Title:</b> AbstractGriddedSurface<p>
 *
 * <b>Description:</b> This class extends GriddedSurface to included sampling subset regions.
 *
 */
public abstract class AbstractEvenlyGriddedSurfaceWithSubsets extends AbstractGriddedSurface  {
	
	// no argument constructor needed by subclasses
	public AbstractEvenlyGriddedSurfaceWithSubsets() {}


	/**
	 *  Constructor for the GriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractEvenlyGriddedSurfaceWithSubsets( int numRows, int numCols,double gridSpacing ) {
		super(numRows,numCols,gridSpacing );
	}
	
	/**
	 *  Constructor for the GriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractEvenlyGriddedSurfaceWithSubsets( int numRows, int numCols,double gridSpacingAlong, double gridSpacingDown) {
		super( numRows, numCols, gridSpacingAlong, gridSpacingDown );
	}





	/**
	 * Gets the Nth subSurface on the surface
	 *
	 * @param numSubSurfaceCols  Number of grid points in subsurface length
	 * @param numSubSurfaceRows  Number of grid points in subsurface width
	 * @param numSubSurfaceOffsetAlong Number of grid points for offset along strike
	 * @param numSubSurfaceOffsetDown Number of grid points for offset down dip
	 * @param n The index of the desired surface (from 0 to (getNumSubsetSurfaces - 1))
	 *
	 */
	public GriddedSubsetSurface getNthSubsetSurface(int numSubSurfaceCols,
			int numSubSurfaceRows, int numSubSurfaceOffsetAlong, int numSubSurfaceOffsetDown, int n) {
		
		// number of subSurfaces along the length of fault
		int nSubSurfaceAlong = (int)Math.floor((numCols-numSubSurfaceCols)/numSubSurfaceOffsetAlong +1);

		// there is only one subSurface
		if(nSubSurfaceAlong <=1) {
			nSubSurfaceAlong=1;
		}
		if(numSubSurfaceCols > getNumCols()) numSubSurfaceCols = getNumCols();
		if(numSubSurfaceRows > getNumRows()) numSubSurfaceRows = getNumRows();

		return getNthSubsetSurface(numSubSurfaceCols, numSubSurfaceRows, numSubSurfaceOffsetAlong, numSubSurfaceOffsetDown, nSubSurfaceAlong, n);
		//     throw new RuntimeException("EvenlyGriddeddsurface:getNthSubsetSurface::Inavlid n value for subSurface");
	}


	/**
	 * Gets the Nth subSurface on the surface
	 *
	 * @param numSubSurfaceCols  Number of grid points along length
	 * @param numSubSurfaceRows  Number of grid points along width
	 * @param numSubSurfaceOffsetAlong Number of grid points for offset along strike
	 * @param numSubSurfaceOffsetDown Number of grid points for offset down dip
	 * @param n The index of the desired surface (from 0 to (getNumSubsetSurfaces - 1))
	 *
	 */
	private GriddedSubsetSurface getNthSubsetSurface(int numSubSurfaceCols,int numSubSurfaceRows,
			int numSubSurfaceOffsetAlong,int numSubSurfaceOffsetDown,int nSubSurfaceAlong, int n){
		
		//getting the row number in which that subsetSurface is present
		int startRow = n/nSubSurfaceAlong * numSubSurfaceOffsetDown;

		//getting the column from which that subsetSurface starts
		int startCol = n%nSubSurfaceAlong * numSubSurfaceOffsetAlong;  // % gives the remainder: a%b = a-floor(a/b)*b; a%b = a if b>a

		return (new GriddedSubsetSurface((int)numSubSurfaceRows,(int)numSubSurfaceCols,startRow,startCol,this));
	}


	/**
	 * Gets the Nth subSurface on the surface.
	 *
	 * @param subSurfaceLength  subsurface length in km
	 * @param subSurfaceWidth  subsurface width in km
	 * @param subSurfaceOffset offset in km
	 * @param n The index of the desired surface (from 0 to (getNumSubsetSurfaces - 1))
	 *
	 */
	public GriddedSubsetSurface getNthSubsetSurface(double subSurfaceLength,
			double subSurfaceWidth,
			double subSurfaceOffset,
			int n) {
		return getNthSubsetSurface((int)Math.rint(subSurfaceLength/gridSpacingAlong+1),
				(int)Math.rint(subSurfaceWidth/gridSpacingDown+1),
				(int)Math.rint(subSurfaceOffset/gridSpacingAlong), 
				(int)Math.rint(subSurfaceOffset/gridSpacingDown), n);
	}


	/**
	 * Gets the Nth subSurface centered down dip on the surface. If surface is not perfectly centered,
	 * (numRows-numRowsInRup != even number), rupture is one grid increment closer to top then to bottom.
	 *
	 * @param subSurfaceLength  subsurface length in km
	 * @param subSurfaceWidth  subsurface width in km
	 * @param subSurfaceOffset offset in km
	 * @param n The index of the desired surface (from 0 to (getNumSubsetSurfaces - 1))
	 *
	 */
	public GriddedSubsetSurface getNthSubsetSurfaceCenteredDownDip(double subSurfaceLength,
			double subSurfaceWidth,
			double subSurfaceOffset,
			int n) {

		int numSubSurfaceCols =  (int)Math.rint(subSurfaceLength/gridSpacingAlong+1);
		int startCol = -1;

		// make sure it doesn't extend beyond the end
		if(numSubSurfaceCols>getNumCols()){
			numSubSurfaceCols=getNumCols();
			startCol=0;
		}
		else {
			startCol = n * (int)Math.rint(subSurfaceOffset/gridSpacingAlong);
		}

		int numSubSurfaceRows = (int)Math.rint(subSurfaceWidth/gridSpacingDown+1);
		int startRow=-1;

		// make sure it doesn't extend beyone the end
		if(numSubSurfaceRows >= getNumRows()){
			numSubSurfaceRows=getNumRows();
			startRow=0;
		}
		else {
			startRow = (int)Math.floor((getNumRows()-numSubSurfaceRows)/2);  		
		}

		/*
		 System.out.println("subSurfaceLength="+subSurfaceLength+", subSurfaceWidth="+subSurfaceWidth+", subSurfaceOffset="+
				subSurfaceOffset+", numRows="+numRows+", numCols="+numCols+", numSubSurfaceRows="+
				numSubSurfaceRows+", numSubSurfaceCols="+numSubSurfaceCols+", startRow="+startRow+", startCol="+startCol);
		*/
		return (new GriddedSubsetSurface(numSubSurfaceRows,numSubSurfaceCols,startRow,startCol,this));
	}





	/**
	 * Get the subSurfaces on this fault
	 *
	 * @param numSubSurfaceCols  Number of grid points according to length
	 * @param numSubSurfaceRows  Number of grid points according to width
	 * @param numSubSurfaceOffset Number of grid points for offset
	 *
	 */
	public Iterator<GriddedSubsetSurface> getSubsetSurfacesIterator(int numSubSurfaceCols, int numSubSurfaceRows,
			int numSubSurfaceOffsetAlong, int numSubSurfaceOffsetDown) {

		//vector to store the GriddedSurface
		ArrayList<GriddedSubsetSurface> v = new ArrayList<GriddedSubsetSurface>();

		// number of subSurfaces along the length of fault
		int nSubSurfaceAlong = (int)Math.floor((getNumCols()-numSubSurfaceCols)/numSubSurfaceOffsetAlong +1);

		// there is only one subSurface
		if(nSubSurfaceAlong <=1) {
			nSubSurfaceAlong=1;
			numSubSurfaceCols = getNumCols();
		}

		// number of subSurfaces along fault width
		int nSubSurfaceDown =  (int)Math.floor((getNumRows()-numSubSurfaceRows)/numSubSurfaceOffsetDown +1);

		// one subSurface along width
		if(nSubSurfaceDown <=1) {
			nSubSurfaceDown=1;
			numSubSurfaceRows = getNumRows();
		}

		//getting the total number of subsetSurfaces
		int totalSubSetSurface = nSubSurfaceAlong * nSubSurfaceDown;
		//emptying the vector
		v.clear();

		//adding each subset surface to the ArrayList
		for(int i=0;i<totalSubSetSurface;++i)
			v.add(getNthSubsetSurface(numSubSurfaceCols,numSubSurfaceRows,numSubSurfaceOffsetAlong,numSubSurfaceOffsetDown,nSubSurfaceAlong,i));

		return v.iterator();
	}



	/**
	 * Get the subSurfaces on this fault
	 *
	 * @param subSurfaceLength  Sub Surface length in km
	 * @param subSurfaceWidth   Sub Surface width in km
	 * @param subSurfaceOffset  Sub Surface offset
	 * @return           Iterator over all subSurfaces
	 */
	public Iterator<GriddedSubsetSurface> getSubsetSurfacesIterator(double subSurfaceLength,
			double subSurfaceWidth,
			double subSurfaceOffset) {

		return getSubsetSurfacesIterator((int)Math.rint(subSurfaceLength/gridSpacingAlong+1),
				(int)Math.rint(subSurfaceWidth/gridSpacingDown+1),
				(int)Math.rint(subSurfaceOffset/gridSpacingAlong),
				(int)Math.rint(subSurfaceOffset/gridSpacingDown));

	}

	/**
	 *
	 * @param subSurfaceLength subSurface length in km
	 * @param subSurfaceWidth  subSurface Width in km
	 * @param subSurfaceOffset subSurface offset in km
	 * @return total number of subSurface along the fault
	 */
	public int getNumSubsetSurfaces(double subSurfaceLength,double subSurfaceWidth,double subSurfaceOffset){

		int lengthCols =  (int)Math.rint(subSurfaceLength/gridSpacingAlong+1);
		int widthCols =    (int)Math.rint(subSurfaceWidth/gridSpacingDown+1);
		int offsetColsAlong =   (int)Math.rint(subSurfaceOffset/gridSpacingAlong);
		int offsetColsDown =   (int)Math.rint(subSurfaceOffset/gridSpacingDown);

		// number of subSurfaces along the length of fault
		int nSubSurfaceAlong = (int)Math.floor((getNumCols()-lengthCols)/offsetColsAlong +1);

		// there is only one subSurface
		if(nSubSurfaceAlong <=1) {
			nSubSurfaceAlong=1;
		}

		// nnmber of subSurfaces along fault width
		int nSubSurfaceDown =  (int)Math.floor((getNumRows()-widthCols)/offsetColsDown +1);

		// one subSurface along width
		if(nSubSurfaceDown <=1) {
			nSubSurfaceDown=1;
		}

		return nSubSurfaceAlong * nSubSurfaceDown;
	}



	/**
	 * This computes the number of subset surfaces along the length only (not down dip)
	 * @param subSurfaceLength subSurface length in km
	 * @param subSurfaceOffset subSurface offset
	 * @return total number of subSurface along the fault
	 */
	public int getNumSubsetSurfacesAlongLength(double subSurfaceLength,double subSurfaceOffset){
		int lengthCols =  (int)Math.rint(subSurfaceLength/gridSpacingAlong+1);
		int offsetCols =   (int)Math.rint(subSurfaceOffset/gridSpacingAlong);

		// number of subSurfaces along the length of fault
		int nSubSurfaceAlong = (int)Math.floor((getNumCols()-lengthCols)/offsetCols +1);

		// there is only one subSurface
		if(nSubSurfaceAlong <=1) {
			nSubSurfaceAlong=1;
		}

		return nSubSurfaceAlong;
	}

}
