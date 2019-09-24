
import { D3LineData } from '../data/D3LineData.js';
import { D3LineLegendOptions } from '../options/D3LineLegendOptions.js';
import { D3LinePlot } from '../D3LinePlot.js';
import { D3LineSeriesData } from '../data/D3LineSeriesData.js';
import { D3LineSubView } from '../view/D3LineSubView.js';
import { D3Utils } from '../D3Utils.js';
import { D3XYPair } from '../data/D3XYPair.js';

import NshmpError from '../../error/NshmpError.js';
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Create a legend for a D3LinePlot.
 * 
 * @class D3LineLegend
 * @author Brandon Clayton
 */
export class D3LineLegend {

  /**
   * New instance of D3LineLegend
   *  
   * @param {D3LinePlot} linePlot 
   */
  constructor(linePlot) {
    Preconditions.checkArgumentInstanceOf(linePlot, D3LinePlot);
    this.linePlot = linePlot;
  }

  /**
   * Create a legend on a sub view.
   * 
   * @param {D3LineData} lineData The line data to show in the legend
   */
  create(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    if (!lineData.subView.options.showLegend) return;

    this.remove(lineData.subView);
    this.show(lineData.subView);
    this._createLegendTable(lineData);
    this._legendSelectionListener(lineData);
  }

  /**
   * Hide the legend for specific sub view.
   * 
   * @param {D3LineSubView} subView The sub view 
   */
  hide(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    subView.svg.legendEl.classList.add('hidden');
  }

  /**
   * Hide legend on all sub views.
   */
  hideAll() {
    this.hide(this.linePlot.view.upperSubView);

    if (this.linePlot.view.addLowerSubView) {
      this.hide(this.linePlot.view.lowerSubView);
    }
  }

  /**
   * Remove the legend from the sub view.
   * 
   * @param {D3LineSubView} subView
   */
  remove(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    this.hide(subView);

    d3.select(subView.svg.legendForeignObjectEl)
        .attr('height', 0)
        .attr('width', 0);

    d3.select(subView.svg.legendTableEl)
        .selectAll('*')
        .remove();
  }

  /**
   * Highlight a legend entry given an id of the line,
   *    D3LineSeries.lineOptions.id, by increasing the line width, 
   *    marker size, and marker edge size based on 
   *    D3LineSeries.lineOptions.selectionMultiplier.
   * 
   * @param {String} id The id of the line series
   * @param {...D3LineData} lineDatas The line datas
   */
  selectLegendEntry(id, ...lineDatas) {
    Preconditions.checkArgumentString(id);
    Preconditions.checkArgumentArrayInstanceOf(lineDatas, D3LineData);

    for (let lineData of lineDatas) {
      this._resetLegendSelection(lineData);

      d3.select(lineData.subView.svg.legendEl)
          .selectAll(`#${id}`)
          .each((
              /** @type {D3LineSeriesData} */ series,
              /** @type {Number} */ i,
              /** @type {NodeList} */ els) => {
            Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
            Preconditions.checkStateInstanceOf(els, NodeList);
            if (!series.lineOptions.showInLegend) return;
            this._legendSelection(lineData, series, els[i]);
          });
    }
  }

  /**
   * Show the legend on specific sub view.
   * 
   * @param {D3LineSubView} subView The sub view 
   */
  show(subView) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    subView.svg.legendEl.classList.remove('hidden');
  }

  /**
   * Show legends on all sub views.
   */
  showAll() {
    this.show(this.linePlot.view.upperSubView);

    if (this.linePlot.view.addLowerSubView) {
      this.show(this.linePlot.view.lowerSubView);
    }
  }

  syncSubViews() {
    for (let lineData of [this.linePlot.upperLineData, this.linePlot.lowerLineData]) {
      d3.select(lineData.subView.svg.legendEl)
          .selectAll('.legend-entry')
          .on('click', (/** @type {D3LineSeriesData} */ series) => {
            Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
            this.linePlot.selectLine(
                series.lineOptions.id,
                this.linePlot.upperLineData,
                this.linePlot.lowerLineData);
          });
    }
  }


  /**
   * @private
   * Add lines representing the data.
   *  
   * @param {SVGElement} tableSvgEl The SVG table element
   * @param {D3LineSeriesData} series The data
   * @param {D3LineLegendOptions} legendOptions The legend options
   */
  _addLegendLines(tableSvgEl, series, legendOptions) {
    Preconditions.checkArgumentInstanceOfSVGElement(tableSvgEl);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    d3.select(tableSvgEl)
        .append('line')
        .attr('class', 'legend-line')
        .attr('x2', legendOptions.lineLength)
        .attr('stroke-width', series.lineOptions.lineWidth)
        .attr('stroke-dasharray', series.lineOptions.svgDashArray) 
        .attr('stroke', series.lineOptions.color)
        .style('shape-rendering', 'geometricPrecision')
        .attr('fill', 'none');
  }

  /**
   * @private
   * Add the symbols representing the data.
   *  
   * @param {SVGElement} tableSvgEl The SVG table element 
   * @param {D3LineSeriesData} series The data
   * @param {D3LineLegendOptions} legendOptions The legend options
   */
  _addLegendSymbols(tableSvgEl, series, legendOptions) {
    Preconditions.checkArgumentInstanceOfSVGElement(tableSvgEl);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    let size = series.lineOptions.d3SymbolSize; 
    let symbol =  d3.symbol().type(series.lineOptions.d3Symbol).size(size)();
    let rotate = series.lineOptions.d3SymbolRotate;
    let transform = `translate(${legendOptions.lineLength / 2}, 0) rotate(${rotate})`;

    d3.select(tableSvgEl)
        .append('path')
        .attr('class', 'legend-symbol')
        .attr('d', symbol)
        .attr('transform', transform)
        .attr('fill', series.lineOptions.markerColor)
        .attr('stroke', series.lineOptions.markerEdgeColor)
        .attr('stroke-width', series.lineOptions.markerEdgeWidth)
        .style('shape-rendering', 'geometricPrecision')
  }

  /**
   * @private
   * Add the legend text representing the data.
   * 
   * @param {HTMLElement} tableRowEl The HTML table row element
   * @param {D3LineSeriesData} series The data
   * @param {D3LineLegendOptions} legendOptions The legend options
   */
  _addLegendText(tableRowEl, series, legendOptions) {
    Preconditions.checkArgumentInstanceOfHTMLElement(tableRowEl);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    d3.select(tableRowEl)
        .append('td')
        .attr('class', 'legend-text')
        .style('padding', '0 5px')
        .style('font-size', `${legendOptions.fontSize}px`)
        .attr('nowrap', true)
        .text(series.lineOptions.label);
  }

  /**
   * @private
   * Add each D3LineSeriesData to the legend.
   * 
   * @param {HTMLElement} tableRowEl The HTML table row element
   * @param {D3LineSeriesData} series The data
   * @param {D3LineLegendOptions} legendOptions The legend options
   */
  _addSeriesToLegend(tableRowEl, series, legendOptions) {
    Preconditions.checkArgumentInstanceOfHTMLElement(tableRowEl);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    d3.select(tableRowEl)
        .attr('id', series.lineOptions.id)
        .attr('class', 'legend-entry')
        .datum(series);

    let tableSvgEl = this._addTableSVG(tableRowEl, series, legendOptions); 
    this._addLegendLines(tableSvgEl, series, legendOptions);
    this._addLegendSymbols(tableSvgEl, series, legendOptions);
    this._addLegendText(tableRowEl, series, legendOptions);
  }

  /**
   * @private
   * Add the SVG element to the lengend table row.
   * 
   * @param {HTMLElement} tableRowEl The table row element
   * @param {D3LineSeriesData} series The data series
   * @param {D3LineLegendOptions} legendOptions The legend options
   * @returns {SVGElement} The SVG element
   */
  _addTableSVG(tableRowEl, series, legendOptions) {
    Preconditions.checkArgumentInstanceOfHTMLElement(tableRowEl);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    let lineOptions = series.lineOptions;
    let markerSize = 2 * lineOptions.markerSize;
    let lineWidth = lineOptions.lineWidth;

    let rowWidth = legendOptions.lineLength;
    let rowHeight = legendOptions.fontSize > markerSize && 
        legendOptions.fontSize > lineWidth  ? legendOptions.fontSize : 
        markerSize > lineWidth ? markerSize : lineWidth;

    let tableSvgD3 = d3.select(tableRowEl)
        .append('td')
        .attr('class', 'legend-svg')
        .style('padding', '0 5px')
        .style('height', `${rowHeight}px`)
        .style('width', `${rowWidth}px`)
        .style('line-height', 0)
        .append('svg')
        .attr('version', 1.1)
        .attr('xmlns', 'http://www.w3.org/2000/svg')
        .attr('height', rowHeight) 
        .attr('width', rowWidth)
        .append('g')
        .attr('transform', `translate(0, ${rowHeight / 2})`);

    let svgEl = tableSvgD3.node();
    Preconditions.checkArgumentInstanceOfSVGElement(svgEl);

    return svgEl;
  }

  /**
   * @private 
   * Add all legend entries as table row.
   * 
   * @param {D3LineData} lineData The line data 
   * @param {Array<Array<D3LineSeriesData>>} tableRowData The data 
   * @param {D3LineLegendOptions} legendOptions The legend options
   */
  _addTableRows(lineData, tableRowData, legendOptions) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    for (let row of tableRowData) {
      Preconditions.checkArgumentArrayInstanceOf(row, D3LineSeriesData);
    }
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);

    let showExtraEntries = tableRowData.length > legendOptions.maxRows;
    tableRowData = showExtraEntries ? 
        tableRowData.slice(0, legendOptions.maxRows) : tableRowData;

    let tableEl = lineData.subView.svg.legendTableEl;
    d3.select(tableEl)
        .selectAll('tr')
        .data(tableRowData)
        .enter()
        .append('tr')
        .style('cursor', 'pointer')
        .each((
            /** @type {Array<D3LineSeriesData>} */ data, 
            /** @type {Number}*/ i, 
            /** @type {Array<HTMLElement>}*/ els) => {
          Preconditions.checkStateArrayInstanceOf(data, D3LineSeriesData);

          for (let series of data) {
            this._addSeriesToLegend(els[i], series, legendOptions);
          }
        });

    if (showExtraEntries) {
      let nSeries = lineData.toLegendSeries().length; 
      let extraEntries = nSeries - 
          ( legendOptions.maxRows * legendOptions.numberOfColumns ); 

      d3.select(tableEl)
          .append('tr')
          .append('td')
          .attr('colspan', legendOptions.numberOfColumns * 2)
          .style('text-align', 'center')
          .text(`... and ${extraEntries} more ...`);
    }

  }

  /**
   * @private
   * Add the table styling from D3LineLegendOptions.
   * 
   * @param {D3LineData} lineData The line data
   */
  _addTableStyling(lineData) {
    Preconditions.checkStateInstanceOf(lineData, D3LineData);
    let legendOptions = lineData.subView.options.legendOptions;

    let padding = `${legendOptions.paddingTop}px ${legendOptions.paddingRight}px ` +
        `${legendOptions.paddingBottom}px ${legendOptions.paddingLeft}px`;

    let borderStyle = `${legendOptions.borderLineWidth}px ` +
        `${legendOptions.borderStyle} ${legendOptions.borderColor}`; 

    d3.select(lineData.subView.svg.legendTableEl)
        .style('font-size', `${legendOptions.fontSize}px`)
        .style('border-collapse', 'separate')
        .style('border', borderStyle) 
        .style('border-radius', `${legendOptions.borderRadius}px`)
        .style('box-shadow', '0 1px 1px rgba(0, 0, 0, 0.05)')
        .style('padding', padding)
        .style('background', legendOptions.backgroundColor)
        .style('cursor', 'move')
        .style('border-spacing', '0')
        .style('line-height', 'inherit');
  }

  /**
   * @private
   * Create the legend table for all legend entries.
   *  
   * @param {D3LineData} lineData The line data
   */
  _createLegendTable(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    d3.select(lineData.subView.svg.legendForeignObjectEl)
        .attr('height', '100%')
        .attr('width', '100%');
    
    let legendOptions = lineData.subView.options.legendOptions;
    let legendLineSeries = lineData.toLegendSeries();
    let tableRowData = this._getTableRowData(legendLineSeries, legendOptions);
    
    this._addTableStyling(lineData);
    this._addTableRows(lineData, tableRowData, legendOptions);

    let tableEl = lineData.subView.svg.legendTableEl;
    let legendHeight = parseFloat(d3.select(tableEl).style('height'));
    let legendWidth = parseFloat(d3.select(tableEl).style('width'));

    d3.select(lineData.subView.svg.legendEl) 
        .call(this._legendDrag(lineData.subView, legendHeight, legendWidth));

    let loc = this._legendLocation(lineData.subView, legendHeight, legendWidth);

    d3.select(lineData.subView.svg.legendForeignObjectEl)
        .style('height', `${ legendHeight }px`)
        .style('width', `${ legendWidth }px`)
        .style('overflow', 'visible')
        .attr('x', loc.x)
        .attr('y', loc.y);
  }

  /**
   * @private
   * Split up the D3LineSeriesData array when using multiple 
   *    columns in a legend;
   * 
   * @param {Array<D3LineSeriesData>} legendLineSeries The line data
   * @param {D3LineLegendOptions} legendOptions The legend options
   * @returns {Array<Array<D3LineSeriesData>>}
   */
  _getTableRowData(legendLineSeries, legendOptions) {
    Preconditions.checkArgumentArrayInstanceOf(legendLineSeries, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOf(legendOptions, D3LineLegendOptions);
    
    let data = [];
    let nSeries = legendLineSeries.length; 
    let nRows = Math.ceil( nSeries / legendOptions.numberOfColumns );

    for (let row = 0; row < nRows; row++) {
      let splitStart = row * legendOptions.numberOfColumns;
      let splitEnd = ( row + 1 ) * legendOptions.numberOfColumns;
      let series = legendLineSeries.slice(splitStart, splitEnd);
      data.push(series);
    }

    return data;
  }

  /**
   * @private
   * Create a d3 drag function.
   *  
   * @param {D3LineSubView} subView The sub view
   * @param {Number} legendHeight The legend height
   * @param {Number} legendWidth The legend width
   */
  _legendDrag(subView, legendHeight, legendWidth) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(legendHeight);
    Preconditions.checkArgumentNumber(legendWidth);

    let drag = d3.drag()
        .filter(() => {
          return d3.event.target == subView.svg.legendTableEl;
        })
        .on('drag', () => {
          this._onLegendDrag(subView, legendHeight, legendWidth);
        });

    return drag;
  }

  /**
   * @private
   * Calculate the X and Y location of where the legend should be placed.
   * 
   * @param {D3LineSubView} subView The sub view
   * @param {Number} legendHeight The legend height
   * @param {Number} legendWidth The legend width
   * @returns {D3XYPair} 
   */
  _legendLocation(subView, legendHeight, legendWidth) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentNumber(legendHeight);
    Preconditions.checkArgumentNumber(legendWidth);

    let x = 0;
    let y = 0;
    let plotHeight = subView.plotHeight;
    let plotWidth = subView.plotWidth;
    let legendOptions = subView.options.legendOptions;

    let xRight = plotWidth - legendWidth - legendOptions.marginRight
    let xLeft = legendOptions.marginLeft;
    let yTop = legendOptions.marginTop;
    let yBottom = plotHeight - legendHeight - legendOptions.marginBottom;

    switch(legendOptions.location) {
      case 'top-right':
        x = xRight 
        y = yTop; 
        break;
      case 'top-left':
        x = xLeft;
        y = yTop; 
        break;
      case 'bottom-right':
        x = xRight; 
        y = yBottom;
        break;
      case 'bottom-left':
        x = xLeft; 
        y = yBottom; 
        break;
      default:
        NshmpError.throwError(`Cannot set [${legendOptions.location}] legend location`);
    }

    return new D3XYPair(x, y); 
  }

  /**
   * @private
   * Add a on click event to the legend entries
   * 
   * @param {D3LineData} lineData The line data
   */
  _legendSelectionListener(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    d3.select(lineData.subView.svg.legendEl)
        .selectAll('.legend-entry')
        .on('click', (/** @type {D3LineSeriesData */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          this.linePlot.selectLine(series.lineOptions.id, lineData);
        });
  }

  /**
   * @private
   * Handle the legend entry highlighting. 
   * 
   * @param {D3LineData} lineData The line data
   * @param {D3LineSeriesData} series The data series
   * @param {SVGElement} tableRowEl The table row element
   */
  _legendSelection(lineData, series, tableRowEl) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentInstanceOf(series, D3LineSeriesData);
    Preconditions.checkArgumentInstanceOfHTMLElement(tableRowEl);

    let isActive = !tableRowEl.classList.contains('active');
    let lineEls = tableRowEl.querySelectorAll('.legend-line');
    let symbolEls = tableRowEl.querySelectorAll('.legend-symbol');
    
    d3.select(lineData.subView.svg.legendEl)
        .selectAll('.legend-entry')
        .classed('active', false);

    tableRowEl.classList.toggle('active', isActive);
    let legendOptions = lineData.subView.options.legendOptions;
    let fontWeight = isActive ? 'bold' : 'normal';
    let fontSize = legendOptions.fontSize;

    d3.select(tableRowEl)
        .select('.legend-text')
        .style('font-weight', fontWeight)
        .style('font-size', `${fontSize}px`);

    D3Utils.linePlotSelection(series, lineEls, symbolEls, isActive);
  }

  /**
   * @private
   * Handle the legend drag event.
   * 
   * @param {D3LineSubView} subView The sub view 
   */
  _onLegendDrag(subView, legendHeight, legendWidth) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    d3.event.sourceEvent.stopPropagation();

    let x = parseFloat(subView.svg.legendForeignObjectEl.getAttribute('x'));
    x += d3.event.dx;

    let y = parseFloat(subView.svg.legendForeignObjectEl.getAttribute('y'));
    y += d3.event.dy;

    let plotHeight = subView.plotHeight;
    let plotWidth = subView.plotWidth;
    let legendOptions = subView.options.legendOptions;

    let checkLeft = legendOptions.marginLeft;
    let checkRight = plotWidth - legendWidth - legendOptions.marginRight;
    let checkTop = legendOptions.marginTop;
    let checkBottom = plotHeight - legendHeight - legendOptions.marginBottom;

    x = x < checkLeft ? checkLeft : 
        x > checkRight ? checkRight : x;

    y = y < checkTop ? checkTop :
        y > checkBottom ? checkBottom : y;

    d3.select(subView.svg.legendForeignObjectEl)
        .attr('x', x)
        .attr('y', y);
  }

  /**
   * @private
   * Reset any legend entry selections.
   *  
   * @param {D3LineData} lineData The line data
   */
  _resetLegendSelection(lineData) {
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);

    d3.select(lineData.subView.svg.legendEl)
        .selectAll('.legend-line')
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.lineWidth;
        });

    d3.select(lineData.subView.svg.legendEl)
        .selectAll('.legend-symbol')
        .attr('d', (/** @type {D3LineSeriesData}*/ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.d3Symbol.size(series.lineOptions.d3SymbolSize)();
        })
        .attr('stroke-width', (/** @type {D3LineSeriesData} */ series) => {
          Preconditions.checkStateInstanceOf(series, D3LineSeriesData);
          return series.lineOptions.markerEdgeWidth;
        });

    let legendOptions = lineData.subView.options.legendOptions;

    d3.select(lineData.subView.svg.legendEl)
        .selectAll('.legend-text')
        .style('font-size', `${legendOptions.fontSize}px`)
        .style('font-weight', 'normal');
  }

}
