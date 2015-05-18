package org.opensha2.eq.fault.surface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <b>Title:</b> AbstractGriddedSurface<p>
 *
 * <b>Description:</b> This class extends GriddedSurface to included sampling subset regions.
 *
 */
@Deprecated
public abstract class AbstractGriddedSurfaceWithSubsets extends AbstractGriddedSurface  {
	
	// no argument constructor needed by subclasses TODO this blows
	public AbstractGriddedSurfaceWithSubsets() {}

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
		return getNthSubsetSurface((int)Math.rint(subSurfaceLength/strikeSpacing+1),
				(int)Math.rint(subSurfaceWidth/dipSpacing+1),
				(int)Math.rint(subSurfaceOffset/strikeSpacing), 
				(int)Math.rint(subSurfaceOffset/dipSpacing), n);
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

		int numSubSurfaceCols =  (int)Math.rint(subSurfaceLength/strikeSpacing+1);
		int startCol = -1;

		// make sure it doesn't extend beyond the end
		if(numSubSurfaceCols>getNumCols()){
			numSubSurfaceCols=getNumCols();
			startCol=0;
		}
		else {
			startCol = n * (int)Math.rint(subSurfaceOffset/strikeSpacing);
		}

		int numSubSurfaceRows = (int)Math.rint(subSurfaceWidth/dipSpacing+1);
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



	// TODO the naming below isn't very good
	// there is totally unecessary flooring and int-casting below

	/**
	 * Get the subSurfaces on this fault
	 *
	 * @param numSubSurfaceCols  Number of grid points according to length
	 * @param numSubSurfaceRows  Number of grid points according to width
	 * @param numSubSurfaceOffset Number of grid points for offset
	 *
	 */
	private List<GriddedSubsetSurface> getSubsetSurfacesIterator(int numSubSurfaceCols, int numSubSurfaceRows,
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

		return v;
	}


	public static List<GriddedSubsetSurface> createFloatingSurfaceList(
			AbstractGriddedSurface parent, double floatLength, double floatWidth) {

		int floaterColSize = (int) Math.rint(floatLength / parent.strikeSpacing + 1);
		int floaterRowSize = (int) Math.rint(floatWidth / parent.dipSpacing + 1);

		List<GriddedSubsetSurface> surfaceList = new ArrayList<>();

		// along-strike count
		int alongCount = parent.getNumCols() - floaterColSize + 1;
		if (alongCount <= 1) {
			alongCount = 1;
			floaterColSize = parent.getNumCols();
		}

		// down-dip count
		int downCount = parent.getNumRows() - floaterRowSize + 1;
		if (downCount <= 1) {
			downCount = 1;
			floaterRowSize = parent.getNumRows();
		}

		for (int startCol = 0; startCol < alongCount; startCol++) {
			for (int startRow = 0; startRow < downCount; startRow++) {
				GriddedSubsetSurface gss = new GriddedSubsetSurface(floaterRowSize, floaterColSize,
					startRow, startCol, parent);
				surfaceList.add(gss);
			}
		}

		return surfaceList;
	}

	/**
	 * Get the subSurfaces on this fault. float unit = grid spacing unit
	 *
	 * @param subSurfaceLength  Sub Surface length in km
	 * @param subSurfaceWidth   Sub Surface width in km
	 * @param subSurfaceOffset  Sub Surface offset
	 * @return           Iterator over all subSurfaces
	 */
	public List<GriddedSubsetSurface> createFloatingSurfaceList(double floatLength,
			double floatWidth) {

		return getSubsetSurfacesIterator(
			(int) Math.rint(floatLength / strikeSpacing + 1),
			(int) Math.rint(floatWidth / dipSpacing + 1), 1, 1);

	}

	/**
	 * Get the subSurfaces on this fault
	 *
	 * @param subSurfaceLength  Sub Surface length in km
	 * @param subSurfaceWidth   Sub Surface width in km
	 * @param subSurfaceOffset  Sub Surface offset
	 * @return           Iterator over all subSurfaces
	 */
	public List<GriddedSubsetSurface> getSubsetSurfaces(double subSurfaceLength,
			double subSurfaceWidth,
			double subSurfaceOffset) {

		return getSubsetSurfacesIterator((int)Math.rint(subSurfaceLength/strikeSpacing+1),
				(int)Math.rint(subSurfaceWidth/dipSpacing+1),
				(int)Math.rint(subSurfaceOffset/strikeSpacing),
				(int)Math.rint(subSurfaceOffset/dipSpacing));

	}

	/**
	 *
	 * @param subSurfaceLength subSurface length in km
	 * @param subSurfaceWidth  subSurface Width in km
	 * @param subSurfaceOffset subSurface offset in km
	 * @return total number of subSurface along the fault
	 */
	public int getNumSubsetSurfaces(double subSurfaceLength,double subSurfaceWidth,double subSurfaceOffset){

		int lengthCols =  (int)Math.rint(subSurfaceLength/strikeSpacing+1);
		int widthCols =    (int)Math.rint(subSurfaceWidth/dipSpacing+1);
		int offsetColsAlong =   (int)Math.rint(subSurfaceOffset/strikeSpacing);
		int offsetColsDown =   (int)Math.rint(subSurfaceOffset/dipSpacing);

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
		int lengthCols =  (int)Math.rint(subSurfaceLength/strikeSpacing+1);
		int offsetCols =   (int)Math.rint(subSurfaceOffset/strikeSpacing);

		// number of subSurfaces along the length of fault
		int nSubSurfaceAlong = (int)Math.floor((getNumCols()-lengthCols)/offsetCols +1);

		// there is only one subSurface
		if(nSubSurfaceAlong <=1) {
			nSubSurfaceAlong=1;
		}

		return nSubSurfaceAlong;
	}
	
	

}
