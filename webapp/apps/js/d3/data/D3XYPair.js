
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Container class to hold a X and Y pair 
 * 
 * @class D3LineSeriesData
 * @author Brandon Clayton
 */
export class D3XYPair {
  
  /**
   * @param {Number} x The X value 
   * @param {Number} y The Y value
   * @param {String} xString Optional string representation
   * @param {String} yString Optional string representation
   */
  constructor(x, y, xString = '', yString = '') {
    Preconditions.checkArgument(
        typeof x == 'number' || x === null,
        `Value [${x}] must be a number or null`);
    
    Preconditions.checkArgument(
        typeof y == 'number' || y === null,
        `Value [${y}] must be a number or null`);

    Preconditions.checkArgumentString(xString);
    Preconditions.checkArgumentString(yString);

    /** @type {Number} The X value */
    this.x = x;
    
    /** @type {Number} The Y value */
    this.y = y;

    /** @type {String} The X value string representation */
    this.xString = xString;

    /** @type {String} The Y value string representation */
    this.yString = yString;
  }

}
