
import { D3BaseSubView }  from './view/D3BaseSubView.js';

import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview Create a tooltip on a D3BaseSubView.
 * 
 * The tooltip is placed automatically to fit in the plot window
 *    so it will not go past the edge of the plot.
 * 
 * @class D3Tooltip
 * @author Brandon Clayton
 */
export class D3Tooltip {

  constructor() {}

  /**
   * Create a tooltip on a sub view at a desired X and Y coordinate.
   * 
   * @param {D3BaseSubView} subView The sub view to place the tooltip 
   * @param {Array<String>} tooltipText The array of text to display
   * @param {Number} tooltipX The X coordinate in plot units to
   *    place the tooltip
   * @param {Number} tooltipY The Y coordinate in plot units to 
   *    place the tooltip
   */
  create(subView, tooltipText, tooltipX, tooltipY) {
    Preconditions.checkArgumentInstanceOf(subView, D3BaseSubView);
    Preconditions.checkArgumentArrayOf(tooltipText, 'string');
    Preconditions.checkArgumentNumber(tooltipX);
    Preconditions.checkArgumentNumber(tooltipY);

    this._createTooltipTable(subView, tooltipText);
    this._setTooltipLocation(subView, tooltipX, tooltipY);
  }

  /**
   * Remove any tooltip from a sub view
   *  
   * @param {D3BaseSubView} subView The sub view to remove the tooltip
   */
  remove(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3BaseSubView);

    d3.select(subView.svg.tooltipTableEl)
        .selectAll('*')
        .remove();

    d3.select(subView.svg.tooltipForeignObjectEl)
        .attr('height', 0)
        .attr('width', 0);
  }

  /**
   * @private
   * Create the tooltip table with tooltip text.
   * 
   * @param {D3BaseSubView} subView The sub view to add a tooltip
   * @param {Array<String>} tooltipText The tooltip text
   */
  _createTooltipTable(subView, tooltipText) {
    Preconditions.checkArgumentInstanceOf(subView, D3BaseSubView);
    Preconditions.checkArgumentArrayOf(tooltipText, 'string');

    let options = subView.options.tooltipOptions;
    let padding = `${options.paddingTop}px ${options.paddingRight}px ` +
        `${options.paddingBottom}px ${options.paddingLeft}px`;
    let borderStyle = `${options.borderLineWidth}px ${options.borderStyle} ` +
        `${options.borderColor}`; 

    d3.select(subView.svg.tooltipForeignObjectEl) 
        .attr('height', '100%')
        .attr('width', '100%')

    let tableD3 = d3.select(subView.svg.tooltipTableEl)
        .style('font-size', `${options.fontSize}px`)
        .style('border-collapse', 'separate')
        .style('border', borderStyle)
        .style('border-radius', `${options.borderRadius}px`)
        .style('box-shadow', '0 1px 1px rgba(0, 0, 0, 0.05)')
        .style('padding', padding)
        .style('background', options.backgroundColor);
        
    tableD3.selectAll('tr')
        .data(tooltipText)
        .enter()
        .append('tr')
        .append('td')
        .attr('nowrap', true)
        .text((/** @type {String} */ text) => { return text; });
    
    d3.select(subView.svg.tooltipEl).raise();
  }

  /**
   * @private
   * Set the tooltip location, making sure it does not go over the 
   *    edge of the plot.
   * 
   * @param {D3BaseSubView} subView The sub view 
   * @param {Number} tooltipX The X location of tooltip
   * @param {Number} tooltipY The Y location of tooltip
   */
  _setTooltipLocation(subView, tooltipX, tooltipY) {
    Preconditions.checkArgumentInstanceOf(subView, D3BaseSubView);
    Preconditions.checkArgumentNumber(tooltipX);
    Preconditions.checkArgumentNumber(tooltipY);

    let foreignObjectEl = subView.svg.tooltipForeignObjectEl; 
    let tableEl = subView.svg.tooltipTableEl;

    let tooltipHeight = parseFloat(d3.select(tableEl).style('height'));
    let tooltipWidth = parseFloat(d3.select(tableEl).style('width'));

    let plotHeight = subView.plotHeight;
    let plotWidth = subView.plotWidth;

    let offsetX = subView.options.tooltipOptions.offsetX;
    let offsetY = subView.options.tooltipOptions.offsetY;

    let availableWidth = plotWidth - tooltipX;
    let xTranslate = ( tooltipWidth + offsetX ) > availableWidth ? 
        tooltipX - tooltipWidth - offsetX + availableWidth :
        tooltipX + offsetX;

    let availableHeight = plotHeight - tooltipY;
    let yTranslate = ( tooltipHeight + offsetY ) > availableHeight ?
        tooltipY - tooltipHeight - offsetY : 
        tooltipY + offsetY; 

    d3.select(foreignObjectEl)
        .attr('height', tooltipHeight)
        .attr('width', tooltipWidth)
        .attr('transform', `translate(${xTranslate}, ${yTranslate})`); 
  }

}
