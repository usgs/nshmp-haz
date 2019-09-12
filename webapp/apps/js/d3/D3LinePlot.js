
import { D3LineAxes } from './axes/D3LineAxes.js';
import { D3LineData } from './data/D3LineData.js';
import { D3LineOptions } from './options/D3LineOptions.js';
import { D3LineLegend } from './legend/D3LineLegend.js';
import { D3LineSeriesData } from './data/D3LineSeriesData.js';
import { D3LineSubView } from './view/D3LineSubView.js';
import { D3LineView } from './view/D3LineView.js';
import { D3SaveFigure} from './D3SaveFigure.js';
import { D3SaveLineData } from './D3SaveLineData.js';
import { D3TextOptions } from './options/D3TextOptions.js';
import { D3Tooltip } from './D3Tooltip.js';
import { D3Utils } from './D3Utils.js';
import { D3XYPair } from './data/D3XYPair.js';

import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview Plot D3LineData
 * 
 * @class D3LinePlot
 * @author Brandon Clayton
 */
export class D3LinePlot {

  /**
   * New D3LinePlot instance.
   * 
   * @param {D3LineView} view The line view 
   */
  constructor(view) {
    Preconditions.checkArgumentInstanceOf(view, D3LineView);

    /** @type {D3LineView} */
    this.view = view;

    /** @type {D3LineAxes} */
    this.axes = new D3LineAxes(this.view);
    
    /** @type {D3LineData} */
    this.upperLineData = undefined;
    this._setLineData(this._getDefaultUpperLineData());
    
    /** @type {D3LineData} */
    this.lowerLineData = undefined;
    if (this.view.addLowerSubView) {
      this._setLineData(this._getDefaultLowerLineData());
    }
    
    /** @type {D3Tooltip} */
    this.tooltip = new D3Tooltip();
    
    /** @type {D3LineLegend} */
    this.legend = new D3LineLegend(this);

    this._addDefaultAxes();
    this._addEventListeners();
  }

  /**
   * Select lines on multiple sub views that have the same id. 
   * 
   * @param {String} id The id of the lines to select
   * @param {Array<D3LinePlot>} linePlots The line plots
   * @param {Array<D3LineData>} lineDatas The line data
   */
  static selectLineOnSubViews(id, linePlots, lineDatas) {
    Preconditions.checkArgumentString(id);
    Preconditions.checkArgumentArrayInstanceOf(linePlots, D3LinePlot);
    Preconditions.checkArgumentArrayInstanceOf(lineDatas, D3LineData);
    Preconditions.checkState(
        linePlots.length == lineDatas.length,
        'Number of line plots and line datas must be the same');

    for (let i = 0; i < linePlots.length; i++) {
      let linePlot = linePlots[i];
      let lineData = lineDatas[i]; 
      
      Preconditions.checkStateInstanceOf(linePlot, D3LinePlot);
      Preconditions.checkStateInstanceOf(lineData, D3LineData);

      linePlot.selectLine(id, lineData); 
    }
  }

  /**
   * Sync selections between multiple sub views.
   *  
   * @param  {Array<D3LinePlot>} linePlots The line plots
   * @param {Array<D3LineData>} lineDatas The line data
   */
  static syncSubViews(linePlots, lineDatas) {
    Preconditions.checkArgumentArrayInstanceOf(linePlots, D3LinePlot);
    Preconditions.checkArgumentArrayInstanceOf(lineDatas, D3LineData);
    Preconditions.checkState(
        linePlots.length == lineDatas.length,
        'Number of line plots and line datas must be the same');

    for (let lineData of lineDatas) {
      d3.select(lineData.subView.svg.dataContainerEl)
          .selectAll('.data-enter')
          .on('click', (/** @type {D3LineSeriesData} */ series) => {
            Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
            D3LinePlot.selectLineOnSubViews(
                series.lineOptions.id,
                linePlots,
                lineDatas);
          });
    }
  }

  /**
   * Add text to a sub view's plot.
   *  
   * @param {D3LineSubView} subView The sub view to add text
   * @param {Number} x The X coordinate of text
   * @param {Number} y The Y coordinate of text
   * @param {String} text The text
   * @param {D3TextOptions=} textOptions Optional text options
   * @returns {SVGElement} The text element
   */
  addText(subView, x, y, text, textOptions = D3TextOptions.withDefaults()) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(x);
    Preconditions.checkArgumentNumber(y);
    Preconditions.checkArgumentString(text);
    Preconditions.checkArgumentInstanceOf(textOptions, D3TextOptions);

    let textD3 = d3.select(subView.svg.dataContainerEl)
        .append('g')
        .attr('class', 'text-enter')
        .append('text')
        .datum(new D3XYPair(x, y));

    let textEl = textD3.node();
    Preconditions.checkStateInstanceOfSVGElement(textEl);

    this.moveText(subView, x, y, textEl);
    this.updateText(textEl, text);
    this.updateTextOptions(textEl, textOptions);

    return textEl;
  }

  /**
   * Clear all plots off a D3LineSubView.
   * 
   * @param {D3LineSubView} subView
   */
  clear(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    this.legend.remove(subView);

    d3.select(subView.svg.dataContainerEl).datum(null);

    d3.select(subView.svg.dataContainerEl)
        .selectAll('*')
        .remove();
  }

  /**
   * Clear all plots off the sub views
   */
  clearAll() {
    this.clear(this.view.upperSubView);

    if (this.view.addLowerSubView) this.clear(this.view.lowerSubView);
  }

  /**
   * Get the current X domain of the plot.
   * 
   * @param {D3LineSubView} subView The sub view to get domain
   * @returns {Array<Number>} The X axis domain: [ xMin, xMax ]
   */
  getXDomain(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    
    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    return this.axes._getXAxisScale(
        lineData,
        this._getCurrentXScale(lineData.subView))
        .domain();
  }

  /**
   * Get the current Y domain of the plot.
   * 
   * @param {D3LineSubView} subView The sub view to get the domain
   * @returns {Array<Number>} The Y axis domain: [ yMin, yMax ]
   */
  getYDomain(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    
    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    return this.axes._getYAxisScale(
        lineData,
        this._getCurrentYScale(lineData.subView))
        .domain();
  }

  /**
   * Make a vertical line draggable.
   *  
   * @param {D3LineSubView} subView The sub view were the line is to drag
   * @param {SVGElement} refLineEl The reference line element
   * @param {Array<Number>} xLimit The limits that the line can be dragged
   * @param {Function} callback The funciton to call when the line is dragged.
   *    The arguments passed to the callback function are 
   *    (Number, D3LineSeriesData, SVGElement) where:
   *        - Number is the current X value
   *        - D3LineSeriesData is updated line series data
   *        - SVGElement is element being dragged
   */
  makeDraggableInX(subView, refLineEl, xLimit, callback = () => {}) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOfSVGElement(refLineEl);
    Preconditions.checkArgumentArrayLength(xLimit, 2);
    Preconditions.checkState(
        xLimit[0] < xLimit[1], 
        `X limit min [${xLimit[0]}] must be less than X limit max [${xLimit[1]}]`);
    Preconditions.checkArgumentInstanceOf(callback, Function);

    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    d3.select(refLineEl).select('.plot-line').style('cursor', 'col-resize');

    d3.selectAll([ refLineEl ])
        .on('click', null)
        .call(d3.drag()    
            .on('start', (/** @type {D3LineSeriesData*/ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              this._onDragStart(series, refLineEl);
            }) 
            .on('end', (/** @type {D3LineSeriesData*/ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              this._onDragEnd(series, refLineEl);
            }) 
            .on('drag', (/** @type {D3LineSeriesData */ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              let xValues = series.xValues;
              Preconditions.checkStateArrayLength(xValues, 2);
              Preconditions.checkState(
                  xValues[0] == xValues[1],
                  'Not a vertical line');
              this._onDragInX(lineData, series, refLineEl, xLimit, callback);
            }));
  }

  /**
   * Make a horizontal line draggable.
   *  
   * @param {D3LineSubView} subView The sub view were the line is to drag
   * @param {SVGElement} refLineEl The reference line element
   * @param {Array<Number>} yLimit The limits that the line can be dragged
   * @param {Function} callback The funciton to call when the line is dragged.
   *    The arguments passed to the callback function are 
   *    (Number, D3LineSeriesData, SVGElement) where:
   *        - Number is the current Y value
   *        - D3LineSeriesData is updated line series data
   *        - SVGElement is element being dragged
   */
  makeDraggableInY(subView, refLineEl, yLimit, callback = () => {}) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOfSVGElement(refLineEl);
    Preconditions.checkArgumentArrayLength(yLimit, 2);
    Preconditions.checkState(
        yLimit[0] < yLimit[1], 
        `Y limit min [${yLimit[0]}] must be less than Y limit max [${yLimit[1]}]`);
    Preconditions.checkArgumentInstanceOf(callback, Function);

    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    d3.select(refLineEl).select('.plot-line').style('cursor', 'row-resize');

    d3.selectAll([ refLineEl ])
        .selectAll('.plot-line')
        .on('click', null)
        .call(d3.drag()
            .on('start', (/** @type {D3LineSeriesData*/ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              this._onDragStart(series, refLineEl);
            }) 
            .on('end', (/** @type {D3LineSeriesData*/ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              this._onDragEnd(series, refLineEl);
            }) 
            .on('drag', (/** @type {D3LineSeriesData */ series) => {
              Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
              let yValues = series.yValues;
              Preconditions.checkStateArrayLength(yValues, 2);
              Preconditions.checkState(
                  yValues[0] == yValues[1],
                  'Not a horizontal line');
              this._onDragInY(lineData, series, refLineEl, yLimit, callback);
            }));
  }

  /**
   * Move a text element to a new location.
   *  
   * @param {D3LineSubView} subView The sub view of the text
   * @param {Number} x The new X coordinate
   * @param {Number} y The new Y coordinate
   * @param {SVGElement} textEl The text element
   */
  moveText(subView, x, y, textEl) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(x);
    Preconditions.checkArgumentNumber(y);
    Preconditions.checkArgumentInstanceOfSVGElement(textEl);

    let xScale = this._getCurrentXScale(subView);
    let yScale = this._getCurrentYScale(subView);
    
    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    let xyPair = new D3XYPair(x, y);

    d3.select(textEl)
        .datum(xyPair)
        .attr('x', this.axes.x(lineData, xScale, xyPair))
        .attr('y', this.axes.y(lineData, yScale, xyPair));
  }

  /**
   * Fire a custom function when a line or symbol is selected.
   * Arguments passed to the callback function:
   *    - D3LineSeriesData: The series data from the plot selection
   * 
   * @param {D3LineData} lineData The line data
   * @param {Function} callback Function to call when plot is selected
   */
  onPlotSelection(lineData, callback) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(callback, Function);

    lineData.subView.svg.dataContainerEl.addEventListener('plotSelection', (e) => {
      let series = e.detail;
      Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
      callback(series);
    });
  }

  /**
   * Creates a 2-D line plot from D3LineData.
   *  
   * @param {D3LineData} lineData The line data to plot
   */
  plot(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    lineData = this._dataEnter(lineData);

    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    
    this.axes.createXAxis(lineData, xScale);
    this.axes.createYAxis(lineData, yScale);
    
    this.legend.create(lineData);

    this._plotUpdateHorizontalRefLine(lineData, xScale, yScale);
    this._plotUpdateVerticalRefLine(lineData, xScale, yScale);
    this._plotSelectionEventListener(lineData);
  }

  /**
   * Plot a reference line at zero, y=0.
   * 
   * @param {D3LineSubView} subView The sub view to add the line to
   */
  plotZeroRefLine(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    let lineOptions = D3LineOptions.builder()
        .color(subView.options.referenceLineColor)
        .id('zero-ref-line')
        .lineWidth(subView.options.referenceLineWidth)
        .markerSize(0)
        .selectable(false)
        .showInLegend(false)
        .build();

    let refLineEl = this.plotHorizontalRefLine(subView, 0, lineOptions);

    d3.select(refLineEl).lower();
  }

  /**
   * Plot a horizontal reference line, y=value.
   * 
   * @param {D3LineSubView} subView The sub view to put reference line
   * @param {Number} y The Y value of reference line
   * @param {D3LineOptions=} lineOptions The line options
   * @returns {SVGElement} The reference line element
   */
  plotHorizontalRefLine(
      subView,
      y,
      lineOptions = D3LineOptions.withRefLineDefaults()) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(y);
    Preconditions.checkArgumentInstanceOf(lineOptions, D3LineOptions);

    let series = new D3LineSeriesData(
        this.getXDomain(subView),
        [ y, y ],
        lineOptions);

    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    lineData = D3LineData.builder()
        .of(lineData)
        .data(series.xValues, series.yValues, series.lineOptions)
        .build();

    let refLineD3 = d3.select(subView.svg.dataContainerEl)
        .append('g')
        .attr('class', 'data-enter-ref-line horizontal-ref-line')
        .attr('id', lineOptions.id);

    let refLineEl = refLineD3.node();
    Preconditions.checkStateInstanceOfSVGElement(refLineEl);

    this._plotRefLine(lineData, series, refLineEl);

    return refLineEl;
  }

  /**
   * Plot a vertical reference line, x=value.
   * 
   * @param {D3LineSubView} subView The sub view to put reference line
   * @param {Number} x The X value of reference line
   * @param {D3LineOptions=} lineOptions The line options
   * @returns {SVGElement} The reference line element
   */
  plotVerticalRefLine(
      subView,
      x,
      lineOptions = D3LineOptions.withRefLineDefaults()) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(x);
    Preconditions.checkArgumentInstanceOf(lineOptions, D3LineOptions);

    let series = new D3LineSeriesData(
        [ x, x ],
        this.getYDomain(subView),
        lineOptions);

    let lineData = subView.options.subViewType == 'lower' ?
        this.lowerLineData : this.upperLineData;

    lineData = D3LineData.builder()
        .of(lineData)
        .data(series.xValues, series.yValues, series.lineOptions)
        .build();

    let refLineD3 = d3.select(subView.svg.dataContainerEl)
        .append('g')
        .attr('class', 'data-enter-ref-line vertical-ref-line')
        .attr('id', lineOptions.id);

    let refLineEl = refLineD3.node();
    Preconditions.checkStateInstanceOfSVGElement(refLineEl);

    this._plotRefLine(lineData, series, refLineEl);

    return refLineEl;
  }

  /**
   * Get an SVG element in a sub view's plot based on the data's id.
   * 
   * @param {D3LineSubView} subView The sub view the data element is in
   * @param {String} id The id of the data element
   * @returns {SVGElement} The SVG element with that id
   */
  querySelector(subView, id) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentString(id);

    let dataEl = subView.svg.dataContainerEl.querySelector(`#${id}`);
    Preconditions.checkNotNull(
        dataEl,
        `Id [${id}] not found in [${subView.options.subViewType}] sub view`);
    Preconditions.checkStateInstanceOfSVGElement(dataEl);

    return dataEl;
  }

  /**
   * Get SVG elements in a sub view's plot based on the data's id. 
   * 
   * @param {D3LineSubView} subView The sub view the data element is in
   * @param {String} id The id of the data element
   * @returns {NodeList<SVGElement>} Node list of SVG elements with that id
   */
  querySelectorAll(subView, id) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentString(id);

    let dataEls = subView.svg.dataContainerEl.querySelectorAll(`#${id}`);
    Preconditions.checkStateInstanceOf(dataEls, NodeList);
    Preconditions.checkState(
        dataEls.length > 0,
        `Id [${id}] not found in [${subView.options.subViewType}] sub view`);
    
    for (let el of dataEls) {
      Preconditions.checkStateInstanceOfSVGElement(el);
    }

    return dataEls;
  }

  /**
   * Select lines of multiple line data given an id.
   *  
   * @param {String} id of line to select
   * @param {...D3LineData} lineDatas The line data
   */
  selectLine(id, ...lineDatas) {
    Preconditions.checkArgumentString(id);

    this.legend.selectLegendEntry(id, ...lineDatas);

    for (let lineData of lineDatas) {
      Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
      this._resetPlotSelection(lineData);

      d3.select(lineData.subView.svg.dataContainerEl)
          .selectAll(`#${id}`)
          .each((
              /** @type {D3LineSeriesData} */ series,
              /** @type {Number} */ i,
              /** @type {NodeList} */ els) => {
            Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
            Preconditions.checkStateInstanceOfSVGElement(els[i]);
            this._plotSelection(lineData, series, els[i]);
          });
    }
  }

  /**
   * Sync the plot selections between the upper and lower sub views.
   */
  syncSubViews() {
    this.legend.syncSubViews();
    for (let lineData of [this.upperLineData, this.lowerLineData]) {
      d3.select(lineData.subView.svg.dataContainerEl)
          .selectAll('.data-enter')
          .on('click', (/** @type {D3LineSeriesData} */ series) => {
            Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
            this.selectLine(
                series.lineOptions.id,
                this.upperLineData,
                this.lowerLineData);
          });
    }
  }

  /**
   * Update the text on a text element.
   * 
   * @param {SVGElement} textEl The text element
   * @param {String} text The new text
   */
  updateText(textEl, text) {
    Preconditions.checkArgumentInstanceOfSVGElement(textEl);
    Preconditions.checkArgumentString(text);

    d3.select(textEl).text(text)
  }

  /**
   * Update the text on a text element.
   * 
   * @param {SVGElement} textEl The text element
   * @param {D3TextOptions=} textOptions Optional text options
   */
  updateTextOptions(textEl, textOptions) {
    Preconditions.checkArgumentInstanceOfSVGElement(textEl);
    Preconditions.checkArgumentInstanceOf(textOptions, D3TextOptions);

    let cxRotate = textEl.getAttribute('x');
    let cyRotate = textEl.getAttribute('y');

    d3.select(textEl)
        .attr('alignment-baseline', textOptions.alignmentBaseline)
        .attr('dx', textOptions.dx)
        .attr('dy', -textOptions.dy)
        .attr('fill', textOptions.color)
        .attr('stroke', textOptions.stroke)
        .attr('stroke-width', textOptions.strokeWidth)
        .attr('transform', `rotate(${textOptions.rotate}, ${cxRotate}, ${cyRotate})`)
        .style('font-size', `${textOptions.fontSize}px`)
        .style('font-weight', textOptions.fontWeight)
        .style('text-anchor', textOptions.textAnchor);
  }

  /**
   * @private
   * Add the default X and Y axes.
   * 
   * Based on D3LineSubViewOptions.defaultXLimit and 
   *    D3LineSubViewOptions.defaultYLimit.
   */
  _addDefaultAxes() {
    this.axes.createXAxis(
        this.upperLineData,
        this.view.getXAxisScale(this.view.upperSubView));
    
    this.axes.createYAxis(
        this.upperLineData,
        this.view.getYAxisScale(this.view.upperSubView));

    if (this.view.addLowerSubView) {
      this.axes.createXAxis(
          this.lowerLineData,
          this.view.getXAxisScale(this.view.lowerSubView));
      
      this.axes.createYAxis(
          this.lowerLineData,
          this.view.getYAxisScale(this.view.lowerSubView));
      }
  }

  /**
   * @private
   * Add all event listeners
   */
  _addEventListeners() {
    this.view.viewFooter.saveMenuEl.querySelectorAll('a').forEach((el) => {
      el.addEventListener('click', (e) => { 
        this._onSaveMenu(e);
      });
    });

    this.view.viewFooter.xAxisBtnEl.addEventListener('click', () => { 
      this._onXAxisClick(event); 
    });
    
    this.view.viewFooter.yAxisBtnEl.addEventListener('click', () => { 
      this._onYAxisClick(event); 
    });

    this.view.viewHeader.gridLinesCheckEl.addEventListener('click', () => { 
      this._onGridLineIconClick(); 
    });

    this.view.viewHeader.legendCheckEl.addEventListener('click', () => {
      this._onLegendIconClick();
    });
  }

  /**
   * @private
   * Enter all data from D3LineData.series and any existing data
   *    into new SVG elements.
   * 
   * @param {D3LineData} lineData The data
   */
  _dataEnter(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    /** @type {Array<D3LineData>} */
    let currentLineData = d3.select(lineData.subView.svg.dataContainerEl)
        .datum();

    let data = currentLineData || [];
    data.push(lineData);
    let updatedLineData = D3LineData.of(...data);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter')
        .remove();

    let seriesEnter = d3.select(lineData.subView.svg.dataContainerEl)
        .datum([ updatedLineData ])    
        .selectAll('.data-enter')
        .data(updatedLineData.series);

    seriesEnter.exit().remove();

    let seriesDataEnter = seriesEnter.enter()
        .append('g')
        .attr('class', 'data-enter')
        .attr('id', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.id;
        });

    /* Update upperLineData or lowerLineData */
    this._setLineData(updatedLineData);
    
    let seriesDataEnterEls = seriesDataEnter.nodes();
    this._plotLine(updatedLineData, seriesDataEnterEls);
    this._plotSymbol(updatedLineData, seriesDataEnterEls);

    return updatedLineData;
  }

  /**
   * @private
   * Returns a default D3LineData for the lower sub view to 
   *    show a empty plot on startup.
   * 
   * @returns {D3LineData} The default line data for lower view
   */
  _getDefaultLowerLineData() {
    let lowerXLimit = this.view.lowerSubView.options.defaultXLimit;
    let lowerYLimit = this.view.lowerSubView.options.defaultYLimit;

    let lowerLineData = D3LineData.builder()
        .xLimit(lowerXLimit)
        .yLimit(lowerYLimit)
        .subView(this.view.lowerSubView)
        .build();

    return lowerLineData;
  }

  /**
   * @private
   * Returns a default D3LineData for the upper sub view to 
   *    show a empty plot on startup.
   * 
   * @returns {D3LineData} The default line data for upper view
   */
  _getDefaultUpperLineData() {
    let upperXLimit = this.view.upperSubView.options.defaultXLimit;
    let upperYLimit = this.view.upperSubView.options.defaultYLimit;

    let upperLineData = D3LineData.builder()
        .xLimit(upperXLimit)
        .yLimit(upperYLimit)
        .subView(this.view.upperSubView)
        .build();

    return upperLineData;
  }

  /**
   * @private
   * Get the current X scale of a D3LineSubView, either: 'log' || 'linear'
   * 
   * @param {D3LineSubView} subView The sub view to get X scale
   * @returns {String} The X scale: 'log' || 'linear'
   */
  _getCurrentXScale(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    if (this.view.viewOptions.syncXAxisScale || 
        subView.options.subViewType == 'upper') {
      return this.view.viewFooter.xLinearBtnEl.classList.contains('active') ?
          this.view.viewFooter.xLinearBtnEl.getAttribute('value') :
          this.view.viewFooter.xLogBtnEl.getAttribute('value');
    } else {
      return subView.options.xAxisScale;
    }
  }

  /**
   * @private
   * Get the current Y scale of a D3LineSubView, either: 'log' || 'linear'
   * 
   * @param {D3LineSubView} subView The sub view to get Y scale
   * @returns {String} The Y scale: 'log' || 'linear'
   */
  _getCurrentYScale(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    if (this.view.viewOptions.syncYAxisScale || 
        subView.options.subViewType == 'upper') {
      return this.view.viewFooter.yLinearBtnEl.classList.contains('active') ?
          this.view.viewFooter.yLinearBtnEl.getAttribute('value') :
          this.view.viewFooter.yLogBtnEl.getAttribute('value');
    } else {
      return subView.options.yAxisScale;
    }
  }

  /**
   * @private
   * Handler to add the grid lines when the grid lines icon is checked.
   */
  _onAddGridLines() {
    this.view.viewHeader.gridLinesCheckEl.setAttribute('checked', 'true');

    this.axes.createXGridLines(
        this.upperLineData,
        this._getCurrentXScale(this.view.upperSubView));
    
    this.axes.createYGridLines(
        this.upperLineData,
        this._getCurrentYScale(this.view.upperSubView));

    if (this.view.addLowerSubView) {
      this.axes.createXGridLines(
          this.lowerLineData,
          this._getCurrentXScale(this.view.lowerSubView));
      
      this.axes.createYGridLines(
          this.lowerLineData,
          this._getCurrentYScale(this.view.lowerSubView));
    }
  }

  /**
   * @private
   * Event handler for mouse over plot symbols; add tooltip.
   * 
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The data series
   */
  _onDataSymbolMouseover(lineData, series) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);

    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    
    let xyPair = series.data[0];
    let tooltipX = this.axes.x(lineData, xScale, xyPair);
    let tooltipY = this.axes.y(lineData, yScale, xyPair);

    let subViewOptions = lineData.subView.options;

    let x = subViewOptions.xValueToExponent ? 
        xyPair.x.toExponential(subViewOptions.xExponentFractionDigits) :
        xyPair.x;

    let y = subViewOptions.yValueToExponent ? 
        xyPair.y.toExponential(subViewOptions.yExponentFractionDigits) :
        xyPair.y;

    let tooltipText = [
      `${lineData.subView.options.lineLabel}:  ${series.lineOptions.label}`,
      `${lineData.subView.options.xLabel}: ${xyPair.xString || x}`,
      `${lineData.subView.options.yLabel}: ${xyPair.yString || y}`,
    ];
    
    this.tooltip.create(lineData.subView, tooltipText, tooltipX, tooltipY);
  }

  /**
   * @private
   * Event handler for mouse out of plot symols; remove toolip.
   *  
   * @param {D3LineData} lineData The line data
   */
  _onDataSymbolMouseout(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    this.tooltip.remove(lineData.subView);
  }

  /**
   * @private
   * Drag line in X direction.
   *  
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The series data
   * @param {SVGElement} dataEl The element being dragged
   * @param {Array<Number>} yLimit The Y limit
   * @param {Function} callback The function to call
   */
  _onDragInX(lineData, series, dataEl, xLimit, callback) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);
    Preconditions.checkArgumentArrayLength(xLimit, 2);
    Preconditions.checkArgumentInstanceOf(callback, Function);

    d3.event.sourceEvent.stopPropagation();

    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    let xBounds = this.axes._getXAxisScale(lineData, xScale);
    let xDomain = xBounds.domain();

    let xMinLimit = xLimit[0] < xDomain[0] ? xDomain[0] : xLimit[0];
    let xMaxLimit = xLimit[1] > xDomain[1] ? xDomain[1] : xLimit[1];

    let x = xBounds.invert(d3.event.x);
    x = x < xMinLimit ? xMinLimit : x > xMaxLimit ? xMaxLimit : x;
    
    let snapTo = 1 / lineData.subView.options.dragLineSnapTo;
    x = Math.round( x *  snapTo ) / snapTo;
    let xValues = series.xValues.map(() => { return x; }); 

    let updatedSeries = new D3LineSeriesData(
        xValues,
        series.yValues,
        series.lineOptions,
        series.xStrings,
        series.yStrings);

    d3.select(dataEl).raise();
    callback(x, updatedSeries, dataEl);
    let duration = 0;
    this._plotUpdateDataEl(lineData, updatedSeries, dataEl, xScale, yScale, duration);
  }

  /**
   * @private
   * Drag line in Y direction.
   *  
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The series data
   * @param {SVGElement} dataEl The element being dragged
   * @param {Array<Number>} yLimit The Y limit
   * @param {Function} callback The function to call
   */
  _onDragInY(lineData, series, dataEl, yLimit, callback) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);
    Preconditions.checkArgumentArrayLength(yLimit, 2);
    Preconditions.checkArgumentInstanceOf(callback, Function);

    d3.event.sourceEvent.stopPropagation();

    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    let yBounds = this.axes._getYAxisScale(lineData, yScale);
    let yDomain = yBounds.domain();

    let yMinLimit = yLimit[0] < yDomain[0] ? yDomain[0] : yLimit[0];
    let yMaxLimit = yLimit[1] > yDomain[1] ? yDomain[1] : yLimit[1];

    let y = yBounds.invert(d3.event.y);
    y = y < yMinLimit ? yMinLimit : y > yMaxLimit ? yMaxLimit : y;

    let snapTo = 1 / lineData.subView.options.dragLineSnapTo;
    y = Math.round( y *  snapTo ) / snapTo;
    let yValues = series.yValues.map(() => { return y; }); 

    let updatedSeries = new D3LineSeriesData(
        series.xValues,
        yValues,
        series.lineOptions,
        series.xStrings,
        series.yStrings);

    d3.select(dataEl).raise();
    callback(y, updatedSeries, dataEl);
    let duration = 0;
    this._plotUpdateDataEl(lineData, updatedSeries, dataEl, xScale, yScale, duration);
  }

  /**
   * @private
   * Reset line and symbol size on drag end.
   *  
   * @param {D3LineSeriesData} series The series
   * @param {SVGElement} dataEl
   */
  _onDragEnd(series, dataEl) {
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);

    d3.select(dataEl)
        .selectAll('.plot-line')
        .attr('stroke-width', series.lineOptions.lineWidth); 
    
    d3.select(dataEl)
        .selectAll('.plot-symbol')
        .attr('d', series.d3Symbol.size(series.lineOptions.d3SymbolSize)())
        .attr('stroke-width', series.lineOptions.markerEdgeWidth);
  }

  /**
   * @private
   * Increase line and symbol size on drag start.
   *  
   * @param {D3LineSeriesData} series The series 
   * @param {SVGElement} dataEl
   */
  _onDragStart(series, dataEl) {
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);

    let lineOptions = series.lineOptions;
    let lineWidth = lineOptions.lineWidth * lineOptions.selectionMultiplier;
    let symbolSize = lineOptions.d3SymbolSize * lineOptions.selectionMultiplier;
    let edgeWidth = lineOptions.markerEdgeWidth * lineOptions.selectionMultiplier;

    d3.select(dataEl)
        .selectAll('.plot-line')
        .attr('stroke-width', lineWidth);

    d3.select(dataEl)
        .selectAll('.plot-symbol')
        .attr('d', series.d3Symbol.size(symbolSize)())
        .attr('stroke-width', edgeWidth);
  }

  /**
   * @private
   * Event handler to add or remove the grid lines when the grid lines
   *    icon is clicked.
   */
  _onGridLineIconClick() {
    let isChecked = this.view.viewHeader.gridLinesCheckEl.getAttribute('checked');

    d3.select(this.view.viewHeader.gridLinesCheckEl)
        .style('color', !isChecked ? 'black' : '#bfbfbf');

    if (isChecked) {
      this._onRemoveGridLines();
    } else {
      this._onAddGridLines();
    }
  }

  /**
   * @private
   * Event handler for legend icon click; add/remove legend.
   * 
   * @param {D3LineData} lineData The line data
   */
  _onLegendIconClick() {
    let isChecked = this.view.viewHeader.legendCheckEl.getAttribute('checked');

    if (isChecked) {
      this.view.viewHeader.legendCheckEl.removeAttribute('checked');
      this.legend.hideAll();
    } else {
      this.view.viewHeader.legendCheckEl.setAttribute('checked', 'true');
      this.legend.showAll();
    }
  }

  /**
   * @private
   * Handler to remove the grid lines when the grid lines icon is 
   *    not checked
   */
  _onRemoveGridLines() {
    this.view.viewHeader.gridLinesCheckEl.removeAttribute('checked');

    this.axes.removeXGridLines(this.view.upperSubView);
    this.axes.removeYGridLines(this.view.upperSubView);
    
    if (this.view.addLowerSubView) {
      this.axes.removeXGridLines(this.view.lowerSubView);
      this.axes.removeYGridLines(this.view.lowerSubView);
    }
  }

  /**
   * Save/preview figure or data
   * 
   * @param {Event} event The event
   */
  _onSaveMenu(event) {
    let saveType = event.target.getAttribute('data-type');
    let saveFormat = event.target.getAttribute('data-format');
    let imageOnly = this.view.viewFooter.imageOnlyEl.checked;

    switch(saveType) {
      case 'save-figure':
        if (imageOnly) D3SaveFigure.saveImageOnly(this.view, saveFormat);
        else D3SaveFigure.save(this.view, saveFormat);
        break;
      case 'preview-figure':
        if (imageOnly) D3SaveFigure.previewImageOnly(this.view, saveFormat);
        else D3SaveFigure.preview(this.view, saveFormat);
        break;
      case 'save-data':
        Preconditions.checkNotUndefined(
            this.view.getSaveData(),
            'Must set the save data, D3LineView.setSaveData()');
        D3SaveLineData.saveCSV(...this.view.getSaveData());
        break;
      default: 
        throw new NshmpError(`Save type [${saveType}] not supported`);
    }
  }

  /**
   * @private
   * Update the plot when the X axis buttons are clicked.
   *  
   * @param {Event} event The click event
   */
  _onXAxisClick(event) {
    if (event.target.hasAttribute('disabled')) return;

    let xScale = event.target.getAttribute('value');
    let yScaleUpper = this._getCurrentYScale(this.view.upperSubView);

    this.axes.createXAxis(this.upperLineData, xScale);
    this._plotUpdate(this.upperLineData, xScale, yScaleUpper);

    if (this.view.addLowerSubView && this.view.viewOptions.syncXAxisScale) {
      let yScaleLower = this._getCurrentYScale(this.view.lowerSubView);
      this.axes.createXAxis(this.lowerLineData, xScale);
      this._plotUpdate(this.lowerLineData, xScale, yScaleLower);
    }
  }

  /**
   * @private
   * Update the plot when the Y axus buttons are clicked.
   *  
   * @param {Event} event The click event 
   */
  _onYAxisClick(event) {
    if (event.target.hasAttribute('disabled')) return;

    let xScaleUpper = this._getCurrentXScale(this.view.upperSubView);
    let yScale = event.target.getAttribute('value');
    
    this.axes.createYAxis(this.upperLineData, yScale);
    this._plotUpdate(this.upperLineData, xScaleUpper, yScale);
    
    if (this.view.addLowerSubView && this.view.viewOptions.syncYAxisScale) {
      let xScaleLower = this._getCurrentXScale(this.view.lowerSubView);
      this.axes.createYAxis(this.lowerLineData, yScale);
      this._plotUpdate(this.lowerLineData, xScaleLower, yScale);
    }
  }

  /**
   * @private
   * Plot the lines.
   *  
   * @param {D3LineData} lineData The line data  
   * @param {Array<SVGElement>} seriesEnter
   */
  _plotLine(lineData, seriesEnterEls) {
    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    let line = this.axes.line(lineData, xScale, yScale);

    d3.selectAll(seriesEnterEls)
        .append('path')
        .each((
            /** @type {D3LineSeriesData} */ series,
            /** @type {Number} */ i,
            /** @type {Array<SVGElement>} */ els) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          Preconditions.checkStateInteger(i);
          Preconditions.checkStateArrayInstanceOf(els, SVGElement); 
          this._plotLineSeries(series, els[i], line); 
        });
  }

  /**
   * @private
   * Add a D3LineSeries line to the plot.
   * 
   * @param {D3LineSeriesData} series The series to add
   * @param {SVGElement} dataEl The plot data element
   * @param {Function} line The line function
   */
  _plotLineSeries(series, dataEl, line) {
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);
    Preconditions.checkArgumentInstanceOf(line, Function);
    
    d3.select(dataEl)
        .classed('plot-line', true)
        .attr('d', (/** @type {D3LineSeriesData} */series) => { 
          return line(series.data); 
        })
        .attr('stroke-dasharray', (/** @type {D3LineSeriesData} */ series) => { 
          return series.lineOptions.svgDashArray; 
        })
        .attr('stroke', (/** @type {D3LineSeriesData} */ series) => { 
          return series.lineOptions.color; 
        })
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => { 
          return series.lineOptions.lineWidth; 
        })
        .attr('fill', 'none')
        .style('shape-rendering', 'geometricPrecision')
        .style('cursor', 'pointer');
  }

  /**
   * @private
   * Add a reference line to the plot.
   *  
   * @param {D3LineData} lineData The upper or lower line data 
   * @param {D3LineSeriesData} series The series to add 
   * @param {SVGElement} refLineEl The reference line element
   */
  _plotRefLine(lineData, series, refLineEl) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(refLineEl);
    
    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    let line = this.axes.line(lineData, xScale, yScale);

    d3.select(refLineEl)
        .datum(series)
        .selectAll('path')
        .data([ series ])
        .enter()
        .append('path')
        .each((
            /** @type {D3LineSeriesData} */ series,
            /** @type {Number} */ i,
            /** @type {Array<SVGElement>} */ els) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          Preconditions.checkStateInteger(i);
          Preconditions.checkStateArrayInstanceOf(els, SVGElement); 
          this._plotLineSeries(series, els[i], line); 
        });
  }

  /**
   * @private
   * Select the line and symbol.
   * 
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The data series
   * @param {SVGElement} dataEl The data SVG element
   */
  _plotSelection(lineData, series, dataEl) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);

    d3.select(dataEl).raise();
    let isActive = !dataEl.classList.contains('active');
    let lineEls = dataEl.querySelectorAll('.plot-line');
    let symbolEls = dataEl.querySelectorAll('.plot-symbol');

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter')
        .classed('active', false);
    
    dataEl.classList.toggle('active', isActive);
    D3Utils.linePlotSelection(series, lineEls, symbolEls, isActive);

    let selectionEvent = new CustomEvent(
        'plotSelection', 
        { detail: series });
    lineData.subView.svg.dataContainerEl.dispatchEvent(selectionEvent);
  }

  /**
   * @private
   * Plot selection event listner.
   * 
   * @param {D3LineData} lineData The line data
   */
  _plotSelectionEventListener(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter')
        .filter((/** @type {D3LineSeriesData */ series) => {
          Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.selectable;
        })
        .on('click', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          this.selectLine(series.lineOptions.id, lineData);
        });
  }

  /**
   * @private
   * Plot the symbols.
   * 
   * @param {D3LineData} lineData The line data
   * @param {Array<SVGElement>} seriesEnter The SVG elements
   */
  _plotSymbol(lineData, seriesEnterEls) {
    d3.selectAll(seriesEnterEls)
        .selectAll('.plot-symbol')
        .data((/** @type {D3LineSeriesData} */ series) =>  { 
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return lineData.toMarkerSeries(series); 
        })
        .enter()
        .filter((/** @type {D3LineSeriesData */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.data[0].x != null && series.data[0].y != null;
        })
        .append('path')
        .attr('class', 'plot-symbol')
        .each((
            /** @type {D3LineSeriesData} */ series,
            /** @type {Number} */ i,
            /** @type {Array<SVGElement>} */ els) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          Preconditions.checkStateInteger(i);
          Preconditions.checkStateArrayInstanceOf(els, SVGElement); 
          this._plotSymbolSeries(lineData, series, els[i]); 
        });
  }

  /**
   * @private
   * Add a D3LineSeries symbols to the plot.
   * 
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The series to add
   * @param {SVGElement} dataEl The plot data element
   */
  _plotSymbolSeries(lineData, series, dataEl) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);
    
    let xScale = this._getCurrentXScale(lineData.subView);
    let yScale = this._getCurrentYScale(lineData.subView);
    
    d3.select(dataEl)
        .attr('d', (/** @type {D3LineSeriesData} */ series) => {
          return series.d3Symbol(); 
        })
        .attr('transform', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);

          let x = this.axes.x(lineData, xScale, series.data[0]);
          let y = this.axes.y(lineData, yScale, series.data[0]);
          let rotate = series.lineOptions.d3SymbolRotate;

          return `translate(${x}, ${y}) rotate(${rotate})`;
        })
        .attr('fill', (/** @type {D3LineSeriesData} */ series) => { 
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.markerColor; 
        })
        .attr('stroke', (/** @type {D3LineSeriesData} */ series) => { 
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.markerEdgeColor; 
        })
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => { 
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.markerEdgeWidth; 
        })
        .style('shape-rendering', 'geometricPrecision')
        .style('cursor', 'pointer')
        .on('mouseover', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          this._onDataSymbolMouseover(lineData, series);
        })
        .on('mouseout', () => {
          this._onDataSymbolMouseout(lineData);
        });
  }

  /**
   * @private
   * Update the plot
   *  
   * @param {D3LineData} lineData The line data
   * @param {String} xScale The current X scale
   * @param {String} yScale The current Y scale
   */
  _plotUpdate(lineData, xScale, yScale) {
    this._plotUpdateHorizontalRefLine(lineData, xScale, yScale);
    this._plotUpdateVerticalRefLine(lineData, xScale, yScale);
    this._plotUpdateText(lineData, xScale, yScale);

    /* Update lines */
    let line = this.axes.line(lineData, xScale, yScale);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter')
        .selectAll('.plot-line')
        .transition()
        .duration(lineData.subView.options.translationDuration)
        .attr('d', (/** @type {D3LineSeriesData} */ series) => { 
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return line(series.data);
        });

    /* Update symbols */
    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter')
        .selectAll('.plot-symbol')
        .transition()
        .duration(lineData.subView.options.translationDuration)
        .attr('d', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.d3Symbol(); 
        })
        .attr('transform', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);

          let x = this.axes.x(lineData, xScale, series.data[0]);
          let y = this.axes.y(lineData, yScale, series.data[0]);
          let rotate = series.lineOptions.d3SymbolRotate;

          return `translate(${x}, ${y}) rotate(${rotate})`;
        });
  }

  /**
   * @private
   * Update plot for single data element.
   * 
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The line series
   * @param {SVGElement} dataEl The plot data element
   * @param {Number} translationDuration The duration for translation
   */
  _plotUpdateDataEl(lineData, series, dataEl, xScale, yScale, translationDuration) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfSVGElement(dataEl);

    let line = this.axes.line(lineData, xScale, yScale);
  
    /* Update data */
    d3.select(dataEl).datum(series);

    /* Update line */
    d3.select(dataEl)
        .selectAll('.plot-line')
        .datum(series)
        .transition()
        .duration(translationDuration)
        .attr('d', (/** @type {D3LineSeriesData*/ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return line(series.data);
        });

    let lineOptions = series.lineOptions;
    let symbolSize = lineOptions.d3SymbolSize * lineOptions.selectionMultiplier;
    let edgeWidth = lineOptions.markerEdgeWidth * lineOptions.selectionMultiplier;
    
    /* Update symbols */
    d3.select(dataEl)
        .selectAll('.plot-symbol')
        .data(() => {
          let symbolLineData = D3LineData.builder()
              .subView(lineData.subView)
              .data(
                  series.xValues,
                  series.yValues,
                  series.lineOptions,
                  series.xStrings,
                  series.yStrings)
              .build();

          return symbolLineData.toMarkerSeries(series);
        })
        .transition()
        .duration(translationDuration)
        .attr('d', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.d3Symbol.size(symbolSize)(); 
        })
        .attr('stroke-width', edgeWidth)
        .attr('transform', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          let x = this.axes.x(lineData, xScale, series.data[0]);
          let y = this.axes.y(lineData, yScale, series.data[0]);
          let rotate = series.lineOptions.d3SymbolRotate;
          return `translate(${x}, ${y}) rotate(${rotate})`;
        });
  }

  /**
   * @private
   * Update any horizontal reference lines.
   *  
   * @param {D3LineData} lineData The upper or lower line data
   * @param {String} xScale The X scale
   * @param {String} yScale The Y scale
   */
  _plotUpdateHorizontalRefLine(lineData, xScale, yScale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData); 
    Preconditions.checkArgumentString(xScale);
    Preconditions.checkArgumentString(yScale);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter-ref-line.horizontal-ref-line')
        .selectAll('.plot-line')
        .each((
            /** @type {D3LineSeriesData */ series,
            /** @type {Number} */ i,
            /** @type {Array<SVGElement>} */ els) => {
          Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);

          let refLineEl = els[i].parentNode;
          Preconditions.checkArgumentInstanceOfSVGElement(refLineEl);

          let xDomain = this.axes._getXAxisScale(lineData, xScale).domain();

          let updatedSeries = new D3LineSeriesData(
              xDomain,
              series.yValues,
              series.lineOptions,
              series.xStrings,
              series.yStrings);
          
          let duration = lineData.subView.options.translationDuration;
          this._plotUpdateDataEl(
              lineData,
              updatedSeries,
              refLineEl,
              xScale,
              yScale,
              duration);
        });
  }

  /**
   * @private
   * Update any added text.
   *  
   * @param {D3LineData} lineData The line data
   * @param {String} xScale The X axis scale
   * @param {String} yScale The Y axis scale
   */
  _plotUpdateText(lineData, xScale, yScale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.text-enter')
        .selectAll('text')
        .transition()
        .duration(lineData.subView.options.translationDuration)
        .attr('x', (/** @type {D3XYPair} */ xyPair) => {
          Preconditions.checkStateInstanceOf(xyPair, D3XYPair);
          return this.axes.x(lineData, xScale, xyPair);
        })
        .attr('y', (/** @type {D3XYPair} */ xyPair) => {
          Preconditions.checkStateInstanceOf(xyPair, D3XYPair);
          return this.axes.y(lineData, yScale, xyPair);
        });
  }

  /**
   * @private
   * Update any vertical reference lines.
   * 
   * @param {D3LineData} lineData The upper or lower line data
   * @param {String} xScale The X scale
   * @param {String} yScale The Y scale
   */
  _plotUpdateVerticalRefLine(lineData, xScale, yScale) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData); 
    Preconditions.checkArgumentString(xScale);
    Preconditions.checkArgumentString(yScale);

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.data-enter-ref-line.vertical-ref-line')
        .selectAll('.plot-line')
        .each((
            /** @type {D3LineSeriesData */ series,
            /** @type {Number} */ i,
            /** @type {Array<SVGElement>} */ els) => {
          Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);

          let yDomain = this.axes._getYAxisScale(lineData, yScale).domain();
          let refLineEl = els[i].parentNode;
          Preconditions.checkArgumentInstanceOfSVGElement(refLineEl);

          let updatedSeries = new D3LineSeriesData(
              series.xValues,
              yDomain,
              series.lineOptions,
              series.xStrings,
              series.yStrings);

          let duration = lineData.subView.options.translationDuration;
          this._plotUpdateDataEl(
              lineData,
              updatedSeries,
              refLineEl,
              xScale,
              yScale,
              duration);
        });
  }

  /**
   * @private
   * Reset all plot selections
   * 
   * @param {D3LineData} lineData The line data 
   */
  _resetPlotSelection(lineData) {
    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.plot-line')
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.lineWidth;
        });

    d3.select(lineData.subView.svg.dataContainerEl)
        .selectAll('.plot-symbol')
        .attr('d', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.d3Symbol.size(series.lineOptions.d3SymbolSize)();
        })
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.markerEdgeWidth;
        });
  }

  /**
   * @private
   * Set the current line data.
   * 
   * @param {D3LineData} lineData The line data to set
   */
  _setLineData(lineData) {
    if (lineData.subView.options.subViewType == 'lower') {
      this.lowerLineData = lineData;
    } else {
      this.upperLineData = lineData;
    }
  }

}
