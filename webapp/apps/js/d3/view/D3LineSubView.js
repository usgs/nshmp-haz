
import { D3BaseSubView, D3BaseSubViewSVGElements } from './D3BaseSubView.js';
import { D3LineSubViewOptions } from '../options/D3LineSubViewOptions.js';

import { Preconditions } from '../../error/Preconditions.js';

/**
 * @package
 * @fileoverview Create a sub view for a D3LineView. Adds the 
 *    line plot SVG structure for a line plot.
 * 
 * @class D3LineSubView
 * @extends D3BaseSubView
 * @author Brandon Clayton 
 */
export class D3LineSubView extends D3BaseSubView {

  /**
   * Create a new sub view for D3LineView
   * 
   * @param {HTMLElement} containerEl Container element to append sub view
   * @param {D3LineSubViewOptions} options The sub view options
   */
  constructor(containerEl, options) {
    super(containerEl, options);

    /* Update types */
    /** @type {D3LineSubViewSVGElements} Line plot SVG elements */
    this.svg;

    /** @type {D3LineSubViewOptions} Sub view options for line plot */
    this.options;

  }

  /**
   * @override
   * @package
   * Create the sub view SVG structure for a line plot.
   * 
   * @returns {D3LineSubViewSVGElements} The SVG elements
   */
  _createSVGStructure() {
    let svg = super._createSVGStructure();
    let svgEl = svg.svgEl;
    let outerPlotEl = svg.outerPlotEl; 
    let innerPlotEl = svg.innerPlotEl;
    let tooltipEl = svg.tooltipEl;

    /* Grid Lines */
    let gridLinesD3 = d3.select(innerPlotEl)
        .append('g')
        .attr('class', 'grid-lines');
    let xGridLinesD3 = gridLinesD3.append('g')
        .attr('class', 'x-grid-lines');
    let yGridLinesD3 = gridLinesD3.append('g')
        .attr('class', 'y-grid-lines');

    /* X Axis */
    let xAxisD3 = d3.select(innerPlotEl)
        .append('g')
        .attr('class', 'x-axis');
    let xTickMarksD3 = xAxisD3.append('g')
        .attr('class', 'x-tick-marks');
    let xLabelD3 = xAxisD3.append('text')
        .attr('class', 'x-label')
        .attr('fill', 'black');
    
    /* Y Axis */
    let yAxisD3 = d3.select(innerPlotEl)
        .append('g')
        .attr('class', 'y-axis');
    let yTickMarksD3 = yAxisD3.append('g')
        .attr('class', 'y-tick-marks');
    let yLabelD3 = yAxisD3.append('text')
        .attr('class', 'y-label')
        .attr('fill', 'black')
        .attr('transform', 'rotate(-90)');

    /* Data Container Group */
    let dataContainerD3 = d3.select(innerPlotEl)
        .append('g')
        .attr('class', 'data-container-group');

    /* Legend Group */
    let legendD3 = d3.select(innerPlotEl)
        .append('g')
        .attr('class', 'legend')
        .style('line-height', '1.5');

    let legendForeignObjectD3 = legendD3.append('foreignObject');

    let legendTableD3 = legendForeignObjectD3
        .append('xhtml:table')
        .attr('xmlns', 'http://www.w3.org/1999/xhtml');

    d3.select(tooltipEl).raise();

    let els = new D3LineSubViewSVGElements();
    els.dataContainerEl = dataContainerD3.node();
    els.gridLinesEl = gridLinesD3.node();
    els.legendEl = legendD3.node();
    els.legendForeignObjectEl = legendForeignObjectD3.node();
    els.legendTableEl = legendTableD3.node();
    els.innerFrameEl = svg.innerFrameEl;
    els.innerPlotEl = innerPlotEl;
    els.outerFrameEl = svg.outerFrameEl;
    els.outerPlotEl = outerPlotEl;
    els.svgEl = svgEl;
    els.tooltipEl = tooltipEl;
    els.tooltipForeignObjectEl = svg.tooltipForeignObjectEl;
    els.tooltipTableEl = svg.tooltipTableEl;
    els.xAxisEl = xAxisD3.node();
    els.xGridLinesEl = xGridLinesD3.node();
    els.xLabelEl = xLabelD3.node();
    els.xTickMarksEl = xTickMarksD3.node();
    els.yAxisEl = yAxisD3.node();
    els.yGridLinesEl = yGridLinesD3.node();
    els.yLabelEl = yLabelD3.node();
    els.yTickMarksEl = yTickMarksD3.node();

    return els.checkElements();
  }

}

/**
 * @fileoverview Container class for the D3LineSubView SVG elements
 * 
 * @class D3LineSubViewSVGElements
 * @extends D3BaseSubViewSVGElements
 * @author Brandon Clayton
 */
export class D3LineSubViewSVGElements extends D3BaseSubViewSVGElements {

  constructor() {
    super();

    /** @type {SVGElement} The data group element */
    this.dataContainerEl = undefined; 
    
    /** @type {SVGElement} The grid lines group element */
    this.gridLinesEl = undefined;
    
    /** @type {SVGElement} The legend group element*/
    this.legendEl = undefined;
    
    /** @type {SVGElement} The legend foreign object element */
    this.legendForeignObjectEl = undefined;
    
    /** @type {HTMLElement} The table element*/
    this.legendTableEl = undefined;
    
    /** @type {SVGElement} The X axis group element */
    this.xAxisEl = undefined;
    
    /** @type {SVGElement} The X axis grid lines group element */
    this.xGridLinesEl = undefined;
    
    /** @type {SVGElement} The X label text element */
    this.xLabelEl = undefined;
    
    /** @type {SVGElement} The X axis tick marks group element */
    this.xTickMarksEl = undefined;
    
    /** @type {SVGElement} The Y axis group element */
    this.yAxisEl = undefined;
    
    /** @type {SVGElement} The Y axis grid lines group element */
    this.yGridLinesEl = undefined;
    
    /** @type {SVGElement} The Y label text element */
    this.yLabelEl = undefined;
    
    /** @type {SVGElement} The Y axis tick marks group element */
    this.yTickMarksEl = undefined;
  }

  /**
   * @override
   * Check the elements.
   * 
   * @returns {D3LineSubViewSVGElements} The elements
   */
  checkElements() {
    super.checkElements();

    Preconditions.checkStateInstanceOfSVGElement(this.dataContainerEl);
    Preconditions.checkStateInstanceOfSVGElement(this.gridLinesEl);
    Preconditions.checkStateInstanceOfSVGElement(this.legendEl);
    Preconditions.checkStateInstanceOfSVGElement(this.legendForeignObjectEl);
    Preconditions.checkStateInstanceOfHTMLElement(this.legendTableEl);
    Preconditions.checkStateInstanceOfSVGElement(this.xAxisEl);
    Preconditions.checkStateInstanceOfSVGElement(this.xGridLinesEl);
    Preconditions.checkStateInstanceOfSVGElement(this.xLabelEl);
    Preconditions.checkStateInstanceOfSVGElement(this.xTickMarksEl);
    Preconditions.checkStateInstanceOfSVGElement(this.yAxisEl);
    Preconditions.checkStateInstanceOfSVGElement(this.yGridLinesEl);
    Preconditions.checkStateInstanceOfSVGElement(this.yLabelEl);
    Preconditions.checkStateInstanceOfSVGElement(this.yTickMarksEl);

    return this;
  }

}
