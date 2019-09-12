
import { D3BaseSubViewOptions } from '../options/D3BaseSubViewOptions.js';

import { Preconditions } from '../../error/Preconditions.js';

/**
 * @package
 * @fileoverview Create a base sub view for D3BaseView. Adds
 *    basic SVG structure for a plot.
 * 
 * @class D3BaseSubView
 * @author Brandon Clayton
 */
export class D3BaseSubView {

  /**
   * Create new sub view.
   * 
   * @param {HTMLElement} containerEl Container element to append sub view
   * @param {D3BaseSubViewOptions} options The sub view options
   */
  constructor(containerEl, options) {
    Preconditions.checkArgumentInstanceOfHTMLElement(containerEl);
    Preconditions.checkArgumentInstanceOf(options, D3BaseSubViewOptions);

    /** @type {HTMLElement} Container element to append sub view */
    this.containerEl = containerEl;

    /** @type {D3BaseSubViewOptions} Sub view options */
    this.options = options;

    /** @type {Number} The SVG view box height in px */
    this.svgHeight = this.options.plotHeight - 
        this.options.marginTop - this.options.marginBottom;
    
    /** @type {Number} The SVG view box width in px */
    this.svgWidth = this.options.plotWidth - 
        this.options.marginLeft - this.options.marginRight;
    
    /** @type {Number} Plot height in px */
    this.plotHeight = this.svgHeight - 
        this.options.paddingBottom - this.options.paddingTop;
    
    /** @type {Number} Plot width in px */
    this.plotWidth = this.svgWidth - 
        this.options.paddingLeft - this.options.paddingRight;
      
    /** @type {HTMLElement}  The sub view element */
    this.subViewBodyEl = this._createSubView();
    
    /** @type {D3BaseSubViewSVGElements} SVG elements */
    this.svg = this._createSVGStructure();
  }

  /**
   * @package
   * Create the SVG structure for the sub view.
   * 
   * @returns {D3BaseSubViewSVGElements} The SVG elements.
   */
  _createSVGStructure() {
    let svgD3 = d3.select(this.subViewBodyEl) 
        .append('svg')
        .attr('version', 1.1)
        .attr('xmlns', 'http://www.w3.org/2000/svg')
        .attr('preserveAspectRatio', 'xMinYMin meet')
        .attr('viewBox', `0 0 ` +
            `${this.options.plotWidth} ${this.options.plotHeight}`);
    
    let outerPlotD3 = svgD3.append('g')
        .attr('class', 'outer-plot')
        .attr('transform', `translate(` + 
            `${this.options.marginLeft}, ${this.options.marginTop})`);
    
    let outerFrameD3 = outerPlotD3.append('rect')
        .attr('class', 'outer-frame')
        .attr('height', this.svgHeight)
        .attr('width', this.svgWidth)
        .attr('fill', 'none');
    
    let innerPlotD3 = outerPlotD3.append('g')
        .attr('class', 'inner-plot')
        .attr('transform', `translate(` + 
            `${this.options.paddingLeft}, ${this.options.paddingTop})`);
    
    let innerFrameD3 = innerPlotD3.append('rect')
        .attr('class', 'inner-frame')
        .attr('height', this.plotHeight)
        .attr('width', this.plotWidth)
        .attr('fill', 'none');
    
    /* Tooltip Group */
    let tooltipD3 = innerPlotD3.append('g')
        .attr('class', 'd3-tooltip');

    let tooltipForeignObjectD3 = tooltipD3.append('foreignObject');
    let tooltipTableD3 = tooltipForeignObjectD3.append('xhtml:table')
        .attr('xmlns', 'http://www.w3.org/1999/xhtml');

    let els = new D3BaseSubViewSVGElements();
    els.innerFrameEl = innerFrameD3.node();
    els.innerPlotEl = innerPlotD3.node();
    els.outerFrameEl = outerFrameD3.node();
    els.outerPlotEl = outerPlotD3.node();
    els.svgEl = svgD3.node();
    els.tooltipEl = tooltipD3.node();
    els.tooltipForeignObjectEl = tooltipForeignObjectD3.node();
    els.tooltipTableEl = tooltipTableD3.node();

    return els.checkElements();
  }

  /**
   * @package
   * Create the sub view.
   * 
   * @returns {HTMLElement} The sub view element
   */
  _createSubView() {
    let subViewD3 = d3.select(this.containerEl)
        .append('div')
        .style('line-height', '1.2')
        .attr('class', 'panel-body');
    
    return subViewD3.node();
  }

}

/**
 * @fileoverview Container class for the D3BaseSubView SVG elements
 * 
 * @class D3BaseSubViewSVGElements
 * @author Brandon Clayton
 */
export class D3BaseSubViewSVGElements {

  constructor() {
    /** @type {SVGElement} The inner plot frame element */
    this.innerFrameEl = undefined;

    /** @type {SVGElement} The inner plot group element */
    this.innerPlotEl = undefined;
    
    /** @type {SVGElement} The outer plot frame element */
    this.outerFrameEl = undefined;
    
    /** @type {SVGElement} The outer plot group element */
    this.outerPlotEl = undefined;
    
    /** @type {SVGElement} The main SVG element */
    this.svgEl = undefined;
    
    /** @type {SVGElement} The tooltip group element */
    this.tooltipEl = undefined;
    
    /** @type {SVGElement} The tooltip foreign object element */
    this.tooltipForeignObjectEl = undefined;
    
    /** @type {HTMLElement} The tooltip table element */
    this.tooltipTableEl = undefined;
  }

  /**
   * Check that all elements are set.
   * 
   * @returns {D3BaseSubViewSVGElements} The elements
   */
  checkElements() {
    for (let value of Object.values(this)) {
      Preconditions.checkNotUndefined(value);
    }
    
    Preconditions.checkStateInstanceOfSVGElement(this.innerFrameEl);
    Preconditions.checkStateInstanceOfSVGElement(this.innerPlotEl);
    Preconditions.checkStateInstanceOfSVGElement(this.outerFrameEl);
    Preconditions.checkStateInstanceOfSVGElement(this.outerPlotEl);
    Preconditions.checkStateInstanceOfSVGElement(this.svgEl);
    Preconditions.checkStateInstanceOfSVGElement(this.tooltipEl);
    Preconditions.checkStateInstanceOfSVGElement(this.tooltipForeignObjectEl);
    Preconditions.checkStateInstanceOfHTMLElement(this.tooltipTableEl);

    return this;
  }

}
