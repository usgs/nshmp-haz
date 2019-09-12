
import { D3LineData } from './data/D3LineData.js';
import { D3LineSeriesData } from './data/D3LineSeriesData.js';

import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview D3 Utilities
 * 
 * @class D3Utils
 * @author Brandon Clayton
 */
export class D3Utils {

  /** @private */
  constructor() {}

  /**
   * Check an array to see if if each value is a number or null.
   * 
   * @param {Array<Number | Null>} values Values to check
   */
  static checkArrayIsNumberOrNull(values) {
    Preconditions.checkArgumentArray(values);

    for (let val of values) {
      Preconditions.checkState(
          typeof val == 'number' || val === null,
          `Value [${val}] must be a number or null`);
    }
  }

  /**
   * Increase/decrease the line width, marker size, and marker edge width
   *    of all lines and symbols.
   *    
   * @param {D3LineSeriesData} series The data series
   * @param {NodeList} lineEls The SVG elements of the lines
   * @param {NodeList} symbolEls The SVG elements of the symbols
   * @param {Boolean} isActive Whether the line/symbols have been selected
   *    or deselected
   */
  static linePlotSelection(series, lineEls, symbolEls, isActive) {
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(lineEls, NodeList);
    Preconditions.checkStateInstanceOf(symbolEls, NodeList);
    Preconditions.checkArgumentBoolean(isActive);

    let options = series.lineOptions;

    let lineWidth = isActive ?  
        options.lineWidth * options.selectionMultiplier :
        options.lineWidth;

    let symbolSize = isActive ?
        options.d3SymbolSize * options.selectionMultiplier :
        options.d3SymbolSize;

    let edgeWidth = isActive ? 
        options.markerEdgeWidth * options.selectionMultiplier :
        options.markerEdgeWidth;

    d3.selectAll(lineEls)
        .attr('stroke-width', lineWidth);

    d3.selectAll(symbolEls)
        .attr('d', series.d3Symbol.size(symbolSize)()) 
        .attr('stroke-width', edgeWidth);
  }

}
