
import { D3LineData } from '../data/D3LineData.js';
import { D3LineSubView } from '../view/D3LineSubView.js';
import { D3LineView } from '../view/D3LineView.js';
import { D3XYPair } from '../data/D3XYPair.js';

import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Add X and Y axes, axes labels, and gridlines to
 *    a D3LinePlot.
 * 
 * @class D3LineAxes
 * @author Brandon Clayton
 */
export class D3LineAxes {

  /**
   * New instance of D3LineAxes
   *  
   * @param {D3LineView} view The line view
   */
  constructor(view) {
    Preconditions.checkArgumentInstanceOf(view, D3LineView);

    /** @type {D3LineView} */
    this.view = view;
  }

  /**
   * Add a log or linear X axis to a D3LineSubView with 
   *    a X label and grid lines.
   * 
   * @param {D3LineData} lineData The line data 
   * @param {String} scale The scale: 'log' || 'linear'
   */
  createXAxis(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    
    let subView = lineData.subView;
    let translate = subView.options.xAxisLocation == 'top' ? 0 : 
        subView.plotHeight;

    d3.select(subView.svg.xAxisEl)
        .attr('transform', `translate(-0.5, ${translate - 0.5})`)
        .style(subView.options.tickFontSize);
  
    d3.select(subView.svg.xTickMarksEl)
        .call(this._getXAxis(lineData, scale))
        .each(() => {
          this._setExponentTickMarks(subView, subView.svg.xTickMarksEl, scale);
        });
    
    this.createXGridLines(lineData, scale);
    this._addXLabel(subView);
  }

  /**
   * Add log or linear X grid lines to a D3LineSubView.
   * 
   * @param {D3LineData} lineData The line data
   * @param {String} scale The scale: 'log' || 'linear' 
   */
  createXGridLines(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
   
    if (!this.view.viewHeader.gridLinesCheckEl.getAttribute('checked')) return; 

    let subView = lineData.subView;
    this.removeXGridLines(subView);

    let xGridLines = this._getXAxis(lineData, scale);
    xGridLines.tickFormat('')
        .tickSize(-subView.plotHeight);

    let xGridD3 = d3.select(subView.svg.xGridLinesEl)
        .attr('transform', d3.select(subView.svg.xAxisEl).attr('transform'))
        .call(xGridLines);

    xGridD3.selectAll('*')
        .attr('stroke', subView.options.gridLineColor)
        .attr('stroke-width', subView.options.gridLineWidth);

    xGridD3.selectAll('text')
        .remove();
  }

  /**
   * Add a log or linear Y axis to a D3LineSubView with 
   *    a Y label and grid lines.
   * 
   * @param {D3LineData} lineData The line data 
   * @param {String} scale The scale: 'log' || 'linear'
   */
  createYAxis(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
   
    let subView = lineData.subView;
    let translate = subView.options.yAxisLocation == 'right' ?
        subView.plotWidth : 0;

    d3.select(subView.svg.yAxisEl)
        .attr('transform', `translate(${translate - 0.5}, -0.5)`)
        .style(subView.options.tickFontSize);
  
    d3.select(subView.svg.yTickMarksEl)
        .call(this._getYAxis(lineData, scale))
        .each(() => {
          this._setExponentTickMarks(subView, subView.svg.yTickMarksEl, scale);
        });
    
    this.createYGridLines(lineData, scale);
    this._addYLabel(subView);
  }

  /**
   * Add log or linear Y grid lines to a D3LineSubView.
   * 
   * @param {D3LineData} lineData The line data
   * @param {String} scale The scale: 'log' || 'linear' 
   */
  createYGridLines(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    
    if (!this.view.viewHeader.gridLinesCheckEl.getAttribute('checked')) return; 

    let subView = lineData.subView;
    this.removeYGridLines(subView);

    let yGridLines = this._getYAxis(lineData, scale);
    yGridLines.tickFormat('')
        .tickSize(-subView.plotWidth);

    let yGridD3 = d3.select(subView.svg.yGridLinesEl)
        .attr('transform', d3.select(subView.svg.yAxisEl).attr('transform'))
        .call(yGridLines);

    yGridD3.selectAll('*')
        .attr('stroke', subView.options.gridLineColor)
        .attr('stroke-width', subView.options.gridLineWidth);

    yGridD3.selectAll('text')
        .remove();
  }

  /**
   * Returns a D3 line generator.
   *  
   * @param {D3LineData} lineData The D3LineData 
   * @param {String} xScale The X axis scale
   * @param {String} yScale The Y axis scale
   * @returns {Function} The line generator
   */
  line(lineData, xScale, yScale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(xScale);
    this._checkScale(yScale);

    let line = d3.line()
        .defined((/** @type {D3XYPair} */ d) => { 
          return d.y != null; 
        })
        .x((/** @type {D3XYPair} */ d) => { 
          return this.x(lineData, xScale, d); 
        })
        .y((/** @type {D3XYPair} */ d) => { 
          return this.y(lineData, yScale, d); 
        })
    
    return line;
  }

  /**
   * Remove the X axis grid lines.
   * 
   * @param {D3LineSubView} subView The sub view to remove them from
   */
  removeXGridLines(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    d3.select(subView.svg.xGridLinesEl)
        .selectAll('*')
        .remove();
  }

  /**
   * Remove the Y axis grid lines.
   * 
   * @param {D3LineSubView} subView The sub view to remove them from
   */
  removeYGridLines(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    d3.select(subView.svg.yGridLinesEl)
        .selectAll('*')
        .remove();
  }

  /**
   * Get the plotting X coordinate of a data point assosciated with a
   *    D3LineData
   * 
   * @param {D3LineData} lineData The D3LineData for the X coordinate
   * @param {String} scale The X axis scale
   * @param {D3XYPair} xyPair The data point to plot
   * @returns {Number} The plotting X coordinate of the X data point
   */
  x(lineData, scale, xyPair) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    Preconditions.checkArgumentInstanceOf(xyPair, D3XYPair);
    
    let d3Scale = this._getXAxisScale(lineData, scale);
    return d3Scale(xyPair.x);
  }

  /**
   * Get the plotting Y coordinate of a data point assosciated with a
   *    D3LineData
   * 
   * @param {D3LineData} lineData The D3LineData for the Y coordinate
   * @param {String} scale The Y axis scale
   * @param {D3XYPair} xyPair The data point to plot
   * @returns {Number} The plotting Y coordinate of the Y data point
   */
  y(lineData, scale, xyPair) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    Preconditions.checkArgumentInstanceOf(xyPair, D3XYPair);
    
    let d3Scale = this._getYAxisScale(lineData, scale);
    return d3Scale(xyPair.y);
  }

  /**
   * @private
   * Add a X axis label.
   *  
   * @param {D3LineSubView} subView Sub view to add X label
   */
  _addXLabel(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    
    let y = subView.options.xAxisLocation == 'top' ? 
        -subView.options.paddingTop : subView.options.paddingBottom;
    
    d3.select(subView.svg.xLabelEl)
        .attr('x', subView.plotWidth / 2)
        .attr('y', y) 
        .style('text-anchor', 'middle')
        .style('alignment-baseline', 'middle')
        .style('font-weight', subView.options.axisLabelFontWeight)
        .text(subView.options.xLabel);
  }

  /**
   * @private
   * Add a Y axis label.
   *  
   * @param {D3LineSubView} subView Sub view to add Y label
   */
  _addYLabel(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    
    let y = subView.options.yAxisLocation == 'right' ? 
        subView.options.paddingRight : -subView.options.paddingLeft;

    d3.select(subView.svg.yLabelEl)
        .attr('x', -subView.plotHeight / 2) 
        .attr('y', y)
        .style('text-anchor', 'middle')
        .style('alignment-baseline', 'middle')
        .style('font-weight', subView.options.axisLabelFontWeight)
        .text(subView.options.yLabel);
  }

  /**
   * @private
   * Check that the scale is either 'log' || 'linear'
   * 
   * @param {String} scale The scale 
   */
  _checkScale(scale) {
    Preconditions.checkArgument(
        scale == 'log' || scale == 'linear',
        `Axis scale [${scale}] not supported`);
  }

  /**
   * @private
   * Get the D3 axis scale: d3.scaleLog() || d3.scaleLinear()
   *  
   * @param {String} scale The axis scale: 'log' || 'linear'
   */
  _getD3AxisScale(scale) {
    this._checkScale(scale);
    return scale == 'log' ? d3.scaleLog() : d3.scaleLinear(); 
  }

  /**
   * @private
   * Get the D3 X axis: d3.axisTop() || d3.axisBottom()
   *  
   * @param {D3LineData} lineData  The line data
   * @param {String} scale The axis scale: 'log' || 'linear'
   */
  _getXAxis(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    
    let d3Scale = this._getXAxisScale(lineData, scale);
    let axis = lineData.subView.options.xAxisLocation == 'top' ?
        d3.axisTop(d3Scale) : d3.axisBottom(d3Scale);
   
    axis.ticks(lineData.subView.options.xTickMarks);

    return axis;
  }

  /**
   * @private
   * Get the X axis scale and set the range and domain.
   * 
   * @param {D3LineData} lineData The line data
   * @param {String} scale The axis scale
   */
  _getXAxisScale(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
    
    let d3Scale = this._getD3AxisScale(scale);
    d3Scale.range([ 0, lineData.subView.plotWidth ])
        .domain(lineData.getXLimit());

    if (lineData.subView.options.xAxisNice) {
      d3Scale.nice(lineData.subView.options.xTickMarks);
    }

    return d3Scale;
  }

  /**
   * @private
   * Get the Y axis: d3AxisLeft() || d3.axisRight()
   * 
   * @param {D3LineData} lineData The line data
   * @param {String} scale The axis scale
   */
  _getYAxis(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);

    let d3Scale = this._getYAxisScale(lineData, scale);
    let axis = lineData.subView.options.yAxisLocation == 'right' ?
        d3.axisRight(d3Scale) : d3.axisLeft(d3Scale);
   
    axis.ticks(lineData.subView.options.yTickMarks);

    return axis;
  }

  /**
   * @private
   * Get the Y axis scale and set the range and domain.
   * 
   * @param {D3LineData} lineData The line data
   * @param {String} scale The axis scale
   */
  _getYAxisScale(lineData, scale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this._checkScale(scale);
   
    let d3Scale = this._getD3AxisScale(scale);
    d3Scale.range([ lineData.subView.plotHeight, 0 ])
        .domain(lineData.getYLimit());

    if (lineData.subView.options.yAxisReverse) {
      d3Scale.range([ 0, lineData.subView.plotHeight ]);
    }

    if (lineData.subView.options.yAxisNice) {
      d3Scale.nice(lineData.subView.options.yTickMarks);
    }

    return d3Scale;
  }

  /**
   * If the axis scale is 'log' set the tick marks to be 
   *    in exponential form.
   * 
   * @param {D3LineSubView} subView The sub view to set the tick marks
   * @param {HTMLElement} tickMarksEl The X or Y tick mark element
   * @param {String} scale The axis scale
   */
  _setExponentTickMarks(subView, tickMarksEl, scale) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOfSVGElement(tickMarksEl);
    this._checkScale(scale);

    if (scale != 'log') return;

    d3.select(tickMarksEl)
        .selectAll('.tick text')
        .text(null)
        .filter((d) => { return Number.isInteger(Math.log10(d)); })
        .text(10)
        .append('tspan')
        .text((d) => { return Math.round(Math.log10(d)); })
        .style('baseline-shift', 'super')
        .attr('font-size', subView.options.tickExponentFontSize); 
  }

}
