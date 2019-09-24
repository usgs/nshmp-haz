
import { D3BaseSubView } from './view/D3BaseSubView.js';
import { D3BaseView } from './view/D3BaseView.js';
import { D3SaveFigureOptions } from './options/D3SaveFigureOptions.js';
import { D3XYPair } from './data/D3XYPair.js';

import NshmpError from '../error/NshmpError.js';
import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview Preview and/or save a view's figures to:
 *    - JPEG
 *    - PNG
 *    - SVG
 * 
 * Use D3SaveFigure.save, D3SaveFigure.saveImageOnly, 
 *    D3SaveFigure.preview, and D3SaveFigure.previewImageOnly
 *    to save and preview the figures. 
 * 
 * @class D3SaveFigure
 * @author Brandon Clayton
 */
export class D3SaveFigure {

  /**
   * @private
   * Use either:
   *    - D3SaveFigure.save
   *    - D3SaveFigure.preview
   *  
   * @param {D3BaseView} view The view
   * @param {D3BaseSubView} subView The sub view
   * @param {String} format The save format: 'jpeg' || 'png' || 'svg'
   * @param {Boolean} saveFigure Whether to save the figure
   * @param {Boolean} imageOnly Whether to save or preview just the image
   *    (page size is image size)
   */
  constructor(view, subView, format, saveFigure, imageOnly) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentInstanceOf(subView, D3BaseSubView);
    Preconditions.checkArgument(
        format == 'jpeg' || format == 'png' || format == 'svg',
        `Format [${format}] not supported`);
    Preconditions.checkArgumentBoolean(saveFigure);

    /** @type {D3BaseView} */
    this.view = view;

    /** @type {D3BaseSubView} */
    this.subView = subView; 
    
    /** @type {Boolean} */
    this.imageOnly = imageOnly;
    
    /** @type {String} */
    this.saveFormat = format;
    
    /** @type {D3SaveFigureOptions} */
    this.options = this.subView.options.saveFigureOptions;
    
    /** @type {String} */
    this.filename = this.subView.options.filename;

    /** @type {Number} */
    this.baseDPI = 96;
    
    /** @type {Number} */
    this.printDPI = format == 'svg' ? this.baseDPI : this.options.dpi; 

    let marginTopBasePx = this.options.marginTop * this.baseDPI;
    let dpiRatio = this.printDPI / this.baseDPI;

    /** @type {Number} */
    this.pageHeightPxPrintDPI = this.imageOnly ? 
        ( this.subView.options.plotHeight + marginTopBasePx ) * dpiRatio:
        this.options.pageHeight * this.printDPI;
    
    /** @type {Number} */
    this.pageWidthPxPrintDPI = this.imageOnly ?
        this.subView.options.plotWidth * dpiRatio :    
        this.options.pageWidth * this.printDPI;
    
    /** @type {Number} */
    this.pageHeightPxBaseDPI = this.imageOnly ? 
        this.subView.options.plotHeight + marginTopBasePx :    
        this.options.pageHeight * this.baseDPI;
    
    /** @type {Number} */
    this.pageWidthPxBaseDPI = this.imageOnly ? 
        this.subView.options.plotWidth :
        this.options.pageWidth * this.baseDPI;
   
    /** @type {SVGElement} */
    this.svgEl = this.subView.svg.svgEl.cloneNode(true);
    
    /** @type {SVGElement} */
    this.svgOuterPlotEl = this.svgEl.querySelector('.outer-plot');
    
    /** @type {SVGElement} */
    this.svgOuterPlotFrameEl = this.svgOuterPlotEl.querySelector('.outer-frame');
    
    /** @type {SVGElement} */
    this.svgInnerPlotEl = this.svgEl.querySelector('.inner-plot');
    
    /** @type {SVGElement} */
    this.svgInnerPlotFrameEl = this.svgInnerPlotEl.querySelector('.inner-frame');

    /** @type {Number} */
    this.footerHeightInch = 0.5;

    Preconditions.checkStateInstanceOfSVGElement(this.svgEl);
    Preconditions.checkStateInstanceOfSVGElement(this.svgInnerPlotEl);
    Preconditions.checkStateInstanceOfSVGElement(this.svgInnerPlotFrameEl);
    Preconditions.checkStateInstanceOfSVGElement(this.svgOuterPlotEl);
    Preconditions.checkStateInstanceOfSVGElement(this.svgOuterPlotFrameEl);

    /** @type {HTMLIFrameElement} */
    this.iFrameEl = document.createElement('iFrame');
    document.body.appendChild(this.iFrameEl);
    Preconditions.checkStateInstanceOf(this.iFrameEl, HTMLIFrameElement);
    this.iFrameEl.style.visibility = 'hidden';
    
    /** @type {HTMLIFrameElement} */
    let iFrameBodyEl = this.iFrameEl.contentWindow.document.body;
    Preconditions.checkStateInstanceOf(
        iFrameBodyEl,
        this.iFrameEl.contentWindow.HTMLElement);
    iFrameBodyEl.appendChild(this.svgEl);

    if (saveFigure) {
      this._saveFigure();
    } else {
      this._previewFigure();
    }

    this.iFrameEl.remove();
  }

  /**
   * Preview a view's figures in a new window with a metadata table and a
   *    footer with a date and the URL.
   * 
   * @param {D3BaseView} view The view with the plot to preview
   * @param {String} previewFormat The preview format: 'jpeg' || 'png' || 'svg'
   */
  static preview(view, previewFormat) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentString(previewFormat);

    D3SaveFigure._create(
        view,
        previewFormat.toLowerCase(),
        false /* Save figure */,
        false /* Image only */);
  }

  /**
   * Preview a view's figures in a new window as just the images.
   * 
   * @param {D3BaseView} view The view with the plot to preview
   * @param {String} previewFormat The preview format: 'jpeg' || 'png' || 'svg'
   */
  static previewImageOnly(view, previewFormat) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentString(previewFormat);

    D3SaveFigure._create(
        view,
        previewFormat.toLowerCase(),
        false /* Save figure */,
        true /* Image only */);
  }

  /**
   * Save a view's figures in a specific format with a metadata table
   *    and a footer with the date and the URL.
   * 
   * @param {D3BaseView} view The view with the plots
   * @param {String} saveFormat The save format: 'jpeg' || 'png' || 'svg'
   */
  static save(view, saveFormat) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentString(saveFormat);

    D3SaveFigure._create(
        view,
        saveFormat.toLowerCase(),
        true /* Save figure */,
        false /* Image only */);
  }

  /**
   * Save a view's figures in a specific format as just the images.
   * 
   * @param {D3BaseView} view The view with the plots
   * @param {String} saveFormat The save format: 'jpeg' || 'png' || 'svg'
   */
  static saveImageOnly(view, saveFormat) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentString(saveFormat);

    D3SaveFigure._create(
        view,
        saveFormat.toLowerCase(),
        true /* Save figure */,
        true /* Image only */);
  }

  /**
   * @private
   * Save or preview both the upper and lower sub view's figures.
   *  
   * @param {D3BaseView} view The view 
   * @param {String} format The save/preview format: 'jpeg' || 'png' || 'svg'
   * @param {Boolean} saveFigure Whether to save the figure
   * @param {Boolean} imageOnly Whether to save or preview the image only
   *    (page size is image size)
   */
  static _create(view, format, saveFigure, imageOnly) {
    Preconditions.checkArgumentInstanceOf(view, D3BaseView);
    Preconditions.checkArgumentString(format);
    Preconditions.checkArgumentBoolean(saveFigure);
    Preconditions.checkArgumentBoolean(imageOnly);

    format = format.toLowerCase();

    if (view.addLowerSubView) {
      new D3SaveFigure(view, view.lowerSubView, format, saveFigure, imageOnly);
    }
    
    new D3SaveFigure(view, view.upperSubView, format, saveFigure, imageOnly);
  }

  /**
   * @private
   * Add the URL and date to the bottom of the plot.
   */
  _addFooter() {
    let footerText = [
      window.location.href.toString(),
      new Date().toLocaleString(),
    ];
    
    let footerD3 = d3.select(this.svgEl)
        .append('g')
        .attr('class', 'print-footer')
        .style('font-size', `${this.options.footerFontSize}px`);

    footerD3.selectAll('text')
        .data(footerText.reverse())
        .enter()
        .append('text')
        .attr('y', (/** @type {String} */ text, /** @type {Number} */ i) => {
          Preconditions.checkStateString(text);
          Preconditions.checkStateInteger(i); 
          return -this.options.footerLineBreak * i; 
        })
        .text((/** @type {String} */ text) => { 
          Preconditions.checkStateString(text);
          return text; 
        })

    let footerEl = footerD3.node();

    this._fitFooter(footerEl);
  }

  /**
   * @private
   * Add the metadata table to the page.
   * 
   * @param {D3XYPair} plotTranslate The X and Y translate 
   */
  _addMetadataTable(plotTranslate) {
    if (!this.options.addMetadata) return;
    Preconditions.checkArgument(plotTranslate, D3XYPair);

    let tableInfo = this._updateMetadataForTable();
    let maxColumnValues = this.options.metadataMaxColumnValues;

    let foreignD3 = d3.select(this.svgEl)
        .select('g')
        .append('foreignObject')
        .style('overflow', 'visible');

    let tableD3 = foreignD3.append('xhtml:table')
        .attr('xmlns', 'http://www.w3.org/1999/xhtml')
        .style('font-family', '"Helvetica Neue",Helvetica,Arial,sans-serif')
        .style('font-size', `${this.options.metadataFontSize}px`)
        .style('border-collapse', 'collapse')
        .style('background', 'white');

    for (let data of tableInfo.metadataSet) {
      let tableRowD3 = tableD3.append('tr');

      for (let datum of data) {
        let key = datum[0];
        let values = datum[1];
        let rowSpan = values.length > 1 ? tableInfo.rows : 1;
        
        tableRowD3.append('th')
            .attr('nowrap', true)
            .attr('valign', 'top')
            .attr('rowspan', rowSpan)
            .style('padding', '4px 4px 4px 8px')
            .style('text-align', 'right')
            .html(key);

        let innerTableD3 = tableRowD3.append('td')
            .attr('rowspan', rowSpan)
            .attr('valign', 'top')
            .style('padding', '4px 4px')
            .append('table')
            .style('font-size', `${this.options.metadataFontSize}px`)
            .style('border-collapse', 'collapse')
            .style('background', 'white');

        let nValues = values.length;
        if (nValues > maxColumnValues) {
          values = values.slice(0, maxColumnValues);
          let nMore = nValues - maxColumnValues
          values.push(`... and ${nMore} others ...`);
        }
        
        for (let value of values) {
          value = Number.isNaN(value) || value == null ? '--' : value;
          innerTableD3.append('tr')
            .append('td')
            .attr('nowrap', true)
            .style('text-align', 'left')
            .text(value);
        }
      }
    }

    let tableEl = tableD3.node(); 
    Preconditions.checkStateInstanceOf(
        tableEl,
        this.iFrameEl.contentWindow.HTMLElement);

    let tableDim = tableEl.getBoundingClientRect();
    let tableWidthInPx = tableDim.width;
    let tableHeightInPx = tableDim.height;

    this._fitMetadataTable(
      foreignD3.node(), 
      tableHeightInPx, 
      tableWidthInPx, 
      plotTranslate);
  }

  /**
   * @private
   * Add the plot title.
   * 
   * @param {D3XYPair} plotTranslate The X and Y translate
   */
  _addPlotTitle(plotTranslate) {
    if (!this.options.addTitle) return;

    Preconditions.checkArgumentInstanceOf(plotTranslate, D3XYPair);
    let titleTranslate = this._titlePosition(plotTranslate);

    d3.select(this.svgInnerPlotEl)
        .append('text')
        .attr('class', 'plot-title')
        .attr('x', titleTranslate.x)
        .attr('y', titleTranslate.y)
        .attr('text-anchor', () => { 
          return this.options.titleLocation == 'left' ? 'start' : 'middle';
        })
        .attr('alignment-baseline', 'middle')
        .text(this.view.getTitle());
  }

  /**
   * @private
   * Create the canvas element.
   * 
   * @returns {HTMLCanvasElement} The canvas element
   */
  _createCanvas() {
    let canvasDivD3 = d3.select('body')
        .append('div')
        .attr('class', 'svg-to-canvas hidden');

    let canvasD3 = canvasDivD3.append('canvas')
        .attr('height', this.pageHeightPxPrintDPI)
        .attr('width', this.pageWidthPxPrintDPI)
        .style('height', `${this.options.pageHeight}in`)
        .style('width', `${this.options.pageWidth}in`);

    let canvasEl = canvasD3.node();
    Preconditions.checkStateInstanceOf(canvasEl, HTMLCanvasElement);

    canvasDivD3.remove();
    
    return canvasEl;
  }

  /**
   * @private
   * Create a new window to preview the image.
   * 
   * @param {String} imageSrc The image source
   */
  _createPreviewWindow(imageSrc) {
    Preconditions.checkArgumentString(imageSrc);

    let win = window.open('', '_blank')
    win.document.title = this.filename;
    win.focus();
    
    d3.select(win.document.body)
        .style('margin', '0 5em')
        .style('background', 'black')
        .append('img')
        .attr('src', imageSrc)
        .style('height', 'auto')
        .style('width', 'auto')
        .style('max-height', '-webkit-fill-available')
        .style('max-width', '100%')
        .style('display', 'block')
        .style('margin', 'auto')
        .style('padding', '2em 0')
        .style('top', '50%')
        .style('transform', 'translate(0, -50%)')
        .style('position', 'relative');

    this._createPreviewDownloadButton(win, imageSrc);
  }

  /**
   * @private
   * Add a download button to the preview window.
   *  
   * @param {Window} win The preview window
   * @param {String} imageSrc The image src to download
   */
  _createPreviewDownloadButton(win, imageSrc) {
    Preconditions.checkStateObjectProperty(win, 'Window');
    Preconditions.checkArgumentInstanceOf(win, win.Window);
    Preconditions.checkArgumentString(imageSrc);

    d3.select(win.document.body)
        .append('button')
        .style('background', 'white')
        .style('position', 'absolute')
        .style('bottom', '0.5em')
        .style('left', '1em')
        .style('padding', '0.5em')
        .style('font-size', '1em')
        .style('border-radius', '4px')
        .attr('href', imageSrc)
        .text('Download')
        .on('click', () => { 
          new D3SaveFigure(
              this.view,
              this.subView,
              this.saveFormat,
              true /* Save figure */,
              this.imageOnly);
        });
  }

  /**
   * @private
   * Create the image source string 
   */
  _createSVGImageSource() {
    return `data:image/svg+xml;base64,` + 
        `${btoa(unescape(encodeURIComponent(this.svgEl.outerHTML)))}`;             
  }

  /**
   * @private
   * Draw the canvas 
   *  
   * @param {HTMLCanvasElement} canvasEl The canvas element
   * @param {HTMLImageElement} svgImage The svg image to draw
   */
  _drawCanvas(canvasEl, svgImage) {
    Preconditions.checkArgumentInstanceOf(canvasEl, HTMLCanvasElement);
    Preconditions.checkArgumentInstanceOf(svgImage, HTMLImageElement);

    let dpiScale = this.printDPI / this.baseDPI;
    let canvasContext = canvasEl.getContext('2d');

    canvasContext.scale(dpiScale, dpiScale);

    canvasContext.fillStyle = 'white';
    
    canvasContext.fillRect(0, 0, 
        this.pageWidthPxPrintDPI, this.pageHeightPxPrintDPI);
    
    canvasContext.drawImage(svgImage, 0, 0, 
          this.pageWidthPxPrintDPI, this.pageHeightPxPrintDPI);
  }

  /**
   * @private
   * Scale the footer such that it will fit on the page.
   * 
   * @param {SVGElement} footerEl The footer element to scale
   */
  _fitFooter(footerEl) {
    Preconditions.checkArgumentInstanceOf(
        footerEl,
        this.iFrameEl.contentWindow.SVGElement);

    let footerDim = footerEl.getBoundingClientRect();
    let footerWidthInInch = footerDim.width / this.baseDPI;
    let footerHeightInInch = footerDim.height / this.baseDPI;
    let footerPaddingInInch = this.options.footerPadding / this.baseDPI;

    let pageWidth = this.options.pageWidth - footerPaddingInInch * 2;
     
    let widthScale = footerWidthInInch > pageWidth ?
        pageWidth / footerWidthInInch : 1;

    let footerHeight = this.footerHeightInch - footerPaddingInInch;
    let heightScale = footerHeightInInch > footerHeight ?
        footerHeight / footerHeightInInch : 1;

    let footerScale = Math.min(heightScale, widthScale);

    let footerPadding = this.options.footerPadding;
    let footerTransform = `translate(${footerPadding}, ` +
        `${this.pageHeightPxBaseDPI - footerPadding}) scale(${footerScale})`; 

    d3.select(footerEl).attr('transform', footerTransform);
  }

  /**
   * @private
   * Scale the metadata table to fit under the figure.
   * 
   * @param {SVGElement} foreignObjectEl The Foriegn Object SVG element
   * @param {Number} tableHeightInPx The table height in pixels
   * @param {Number} tableWidthInPx The table width in pixels
   * @param {D3XYPair} plotTranslate The plot margins
   */
  _fitMetadataTable(foreignObjectEl, tableHeightInPx, tableWidthInPx, plotTranslate) {
    Preconditions.checkArgumentInstanceOf(
        foreignObjectEl,
        this.iFrameEl.contentWindow.SVGElement);
    Preconditions.checkArgumentNumber(tableHeightInPx);
    Preconditions.checkArgumentNumber(tableWidthInPx);
    Preconditions.checkArgumentInstanceOf(plotTranslate, D3XYPair);

    let svgHeightInInch = this.subView.options.plotHeight / this.baseDPI;
    let tableMarginTopInPx = this.options.metadataMarginTop * this.baseDPI;

    let tableHeightInInch = tableHeightInPx / this.baseDPI; 
    let tableWidthInInch = tableWidthInPx / this.baseDPI;

    let availableHeight = this.options.pageHeight - 
        this.options.marginTop -
        svgHeightInInch -
        this.options.metadataMarginTop -
        this.footerHeightInInch;

    let heightScale = tableHeightInInch > availableHeight ? 
        availableHeight / tableHeightInInch : 1;
    
    let widthScale = tableWidthInInch > this.options.pageWidth ? 
        this.options.pageWidth / tableWidthInInch : 1;

    let tableScale = Math.min(heightScale, widthScale);

    let centerTableX = - plotTranslate.x + ( this.pageWidthPxBaseDPI - 
        ( tableWidthInPx * tableScale )) / 2;

    d3.select(foreignObjectEl)
        .attr('transform', `scale(${tableScale})`)
        .attr('height', tableHeightInPx)
        .attr('width', tableWidthInPx)
        .attr('y', (this.subView.options.plotHeight + tableMarginTopInPx) / tableScale)
        .attr('x', centerTableX / tableScale);
  }

  /**
   * @private
   * Convert the metadata Map into a set of arrays to be used to create
   *    the table.
   * 
   * @param {Map<String, Array<String>} metadata The metadata Map
   * @param {Number} rows The number of rows the table will have
   * @param {Number} maxColumns The number of key-value columns the table
   *    will have
   * @param {Boolean} expandColumn Whether the first item in the metadata
   *    Map will expand all rows 
   * @return {Set<Array<String, Array<String>>>} The metadata set of arrays
   */
  _metadataToSet(metadata, rows, maxColumns, expandColumn) {
    Preconditions.checkArgumentInstanceOfMap(metadata);
    Preconditions.checkArgumentInteger(rows);
    Preconditions.checkArgumentInteger(maxColumns);
    Preconditions.checkArgumentBoolean(expandColumn);

    let metadataSet = new Set();
    let tmpArray = Array.from(metadata);

    let iStart = 0;
    let iEnd = 0;
    for (let jl = 0; jl < rows; jl++) {
      iStart = iEnd; 
      iEnd = iStart + maxColumns;
      if (jl == 0 && expandColumn) iEnd++;
      metadataSet.add(tmpArray.slice(iStart, iEnd));
    }

    return metadataSet;
  }

  /**
   * @private
   * Preview the figure
   */
  _previewFigure() {
    let svgImage = new Image();
    this._updateSVGElement();
    
    d3.select(this.svgEl)
        .attr('height', this.pageHeightPxBaseDPI * this.printDPI)
        .attr('width', this.pageWidthPxBaseDPI * this.printDPI)
        .style('background', 'white');
    
    let svgImageSrc = this._createSVGImageSource();
    let canvasEl = this._createCanvas();

    svgImage.onload = () => {
      this._drawCanvas(canvasEl, svgImage);

      try {
        switch (this.saveFormat) {
          /* SVG format */
          case 'svg':
            this._createPreviewWindow(svgImageSrc);
            break;
          /* JPEG or PNG format */
          case 'png':
          case 'jpeg':
            canvasEl.toBlob((blob) => {
              let canvasImageSrc = URL.createObjectURL(blob);
              this._createPreviewWindow(canvasImageSrc);
            }, `image/${this.saveFormat}`, 1.0);
            break;
          default:
            throw new NshmpError(`Plot format [${this.saveFormat}] not supported`);
        }
      } catch (err) {
        d3.select(this.iFrameEl).remove();
        throw new NshmpError(err);
      }
    };

    svgImage.setAttribute('src', svgImageSrc);
  }

  /**
   * @private
   * Save the figure
   */
  _saveFigure() {
    let svgImage = new Image();
    this._updateSVGElement();
    let svgImageSrc = this._createSVGImageSource();
    let canvasEl = this._createCanvas();

    svgImage.onload = () => {
      this._drawCanvas(canvasEl, svgImage);

      let aEl = document.createElement('a');
      aEl.download = this.filename;

      try {
        switch (this.saveFormat) {
          /* SVG format */
          case 'svg':
            aEl.setAttribute('href', svgImage.getAttribute('src'));
            aEl.click();
            break;
          /* JPEG or PNG format */
          case 'png':
          case 'jpeg':
            canvasEl.toBlob((blob) => {
              aEl.setAttribute('href', URL.createObjectURL(blob));
              aEl.click();
            }, `image/${this.saveFormat}`, 1.0);
            break;
          default:
            throw new NshmpError(`Plot format [${this.saveFormat}] not supported`);
        }
      } catch (err) {
        d3.select(this.iFrameEl).remove();
        throw new NshmpError(err);
      }

    };

    svgImage.setAttribute('src', svgImageSrc);
  }

  /**
   * @private
   * Calculate the translation needed to center the plot in the page.
   * 
   * @returns {D3XYPair} The translations X and Y 
   */
  _svgTranslate() {
    let innerPlotDim = this.svgInnerPlotEl.getBoundingClientRect();
    let innerPlotWidth = innerPlotDim.width;
    let marginLeftInPx = ( this.pageWidthPxBaseDPI - innerPlotWidth ) / 2;
    let marginTopInPx = this.options.marginTop * this.baseDPI;

    return new D3XYPair(marginLeftInPx, marginTopInPx);
  }

  /**
   * @private
   * Calculate the translation needed for the title.
   * 
   * @param {D3XYPair} plotTranslate The plot translations
   * @returns {D3XYPair} The title translation X and Y
   */
  _titlePosition(plotTranslate) {
    Preconditions.checkArgumentInstanceOf(plotTranslate, D3XYPair);

    let innerPlotWidth = this.svgInnerPlotFrameEl.getBoundingClientRect().width;
    let titleX = this.options.titleLocation == 'left' ? 0 : innerPlotWidth / 2;
    let titleY = - plotTranslate.y / 2;

    return new D3XYPair(titleX, titleY);
  }

  /**
   * @private
   * Update the metadata Map to put the key with a values array
   *    greater than 1 in the first position and convert Map to
   *    a Set.
   * 
   * @typedef {Object} MetadataTableInfo - Parameters to make table.
   * @property {Set<Array<String, Array<String>>>} metadataSet
   *    Set of metadata
   * @property {Number} rows The number of rows in table
   * 
   * @return {MetadataTableInfo} The table parameters
   */
  _updateMetadataForTable() {
    let metadata = this.view.getMetadata();

    let maxKey;
    let maxValue = [];

    for (let [key, value] of metadata) {
      if (value.length > maxValue.length) {
        maxKey = key;
        maxValue = value;
      }
    }

    metadata.delete(maxKey);
    let reMapped = new Map();
    reMapped.set(maxKey, maxValue);
    for (let [key, value] of metadata) {
      reMapped.set(key, value);
    }
    
    let expandColumn = maxValue.length > 1;
    let maxColumns = expandColumn ? this.options.metadataColumns - 1 :
        this.options.metadataColumns;

    let rows = Math.ceil( reMapped.size / maxColumns );
    let metadataSet = this._metadataToSet(
      reMapped, 
      rows, 
      maxColumns,
      expandColumn);

    return {
      metadataSet: metadataSet,
      rows: rows,
    };
  }

  /**
   * @private
   * Update the SVG element
   */
  _updateSVGElement() {
    d3.select(this.svgEl)
        .attr('viewBox', `0 0 ${this.pageWidthPxPrintDPI} ${this.pageHeightPxPrintDPI}`)
        .style('font-family', '"helvetica neue",helvetica,arial,sans-serif')
        .attr('height', this.pageHeightPxPrintDPI)
        .attr('width', this.pageWidthPxPrintDPI);

    let translate = this._svgTranslate();

    this.svgOuterPlotEl.setAttribute(
        'transform',
        `translate(${translate.x}, ${translate.y})`);

    this._addPlotTitle(translate);
    if (!this.imageOnly) {
      this._addFooter();
      this._addMetadataTable(translate);
    }
  }

}
