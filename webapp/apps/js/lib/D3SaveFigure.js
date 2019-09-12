
import NshmpError from '../error/NshmpError.js';
import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview This class will save a SVG element as a figure. 
 *   Figure types:
 *       - pdf (This brings up the print dialog)
 *       - png
 *       - svg
 *       -jpeg
 * 
 *  Use builder for class:
 *    D3SaveFigure.builder()
 *        .centerSvgOnEl(SVGElement to center plot on page)
 *        .centerTitleOnEl(SVGElement to center title on page)
 *        .currentSvgHeight(SVG height in pixels from veiw box)
 *        .currentSvgWidth(SVG width in pixels from view box)
 *        .filename(a download name)
 *        .options(D3SaveFigureOptions)
 *        .metadata(Map<String, Array<String>>) 
 *        .plotFormat(The plot format)
 *        .plotTitle(The plot title)
 *        .svgEl(Main SVGElement to save)
 *        .build();
 * 
 * @author Brandon Clayton
 */
export default class D3SaveFigure { 

  /**
   * @param {D3SaveFigureBuilder} builder The builder for this class.
   * 
   * @typedef {Object} D3SaveFigureOptions - Options for D3SaveFigure 
   * @property {Number} footerFontSize Font size of footer in px.
   * @property {Number} footerLineBreak Line break in px.
   * @property {Number} footerPadding Padding around footer in px.
   * @property {Number} marginLeft Left margin for image in inches, only used
   *    printCenter is false
   * @property {Number} marginTop Top margin for image in inches.
   * @property {Number} metadataFontSize Font size of metadata table
   * @property {Number} metadataMarginTop Margin in inches for table
   * @property {Number} metadataColumns Number of key-value columns
   * @property {Number} metadataMaxColumnValues Max number of values 
   *    that can reside in a row of a table
   * @property {Number} pageHeight Total page height in inches.
   * @property {Number} pageWidth Total page width in inches.
   * @property {Number} printDpi Resolution of image in DPI.
   * @property {Boolean} printCenter Whether to print image in center.
   * @property {Boolean} printFooter Whether to print footer.
   * @property {Boolean} printMetadata Whether to print the metadata table
   * @property {Boolean} printTitle Whether to print title.
   * @property {Number} svgHeight Plot height in inches.
   * @property {Number} svgWidth Plot width in inches.
   * @property {Number} titleFontSize Title font size in px. 
   */ 
  constructor(builder) {
    Preconditions.checkArgument(
      builder.constructor.name == 'Builder',
      'Must use D3SaveFigure.Builder');

    /** @type {SVGElement} */
    this.centerTitleOnEl = builder._centerTitleOnEl;
    /** @type {SVGElement} */
    this.centerSvgOnEl = builder._centerSvgOnEl;
    /** @type {String} */
    this.filename = builder._filename;
    /** @type {String} */
    this.plotTitle = builder._plotTitle;
    /** @type {String} */
    this.plotFormat = builder._plotFormat;
    /** @type {Map<String, Array<String>} */ 
    this.metadata = builder._metadata;
    /** @type {Number} */
    this.originalPlotHeight = builder._plotHeight;
    /** @type {Number} */
    this.originalPlotWidth = builder._plotWidth;
    /** @type {Boolean} */
    this.previewFigure = builder._previewFigure;
    /** @type {SVGElement} */
    this.svgEl = builder._svgEl;
    /** @type {Number} */ 
    this.baseDpi = 96; 

    /** @type {D3SaveFigureOptions} */
    this.options = {
      footerFontSize: 8,
      footerLineBreak: 8,
      footerPadding: 20,
      marginLeft: 0,
      marginTop: 0.75,
      metadataFontSize: 10,
      metadataMarginTop: 0,
      metadataColumns: 3,
      metadataMaxColumnValues: 5,
      pageHeight: 8.5,
      pageWidth: 11,
      printDpi: 300,
      printFooter: true,
      printMetadata: true,
      printTitle: true,
      titleFontSize: 20,
    };
    $.extend(this.options, builder._options);

     /* Update DPI */
    let dpi = this.plotFormat == 'svg' ?
        this.baseDpi : this.options.printDpi; 
    this.options.printDpi = dpi;
    
    /** @type {Number} */ 
    this.pageHeightPxPrintDpi = this.options.pageHeight * this.options.printDpi; 
    /** @type {Number} */ 
    this.pageWidthPxPrintDpi = this.options.pageWidth * this.options.printDpi;
    /** @type {Number} */ 
    this.pageHeightPxBaseDpi = this.options.pageHeight * this.baseDpi; 
    /** @type {Number} */ 
    this.pageWidthPxBaseDpi = this.options.pageWidth * this.baseDpi;
    /** @type {Number} */ 
    this.footerHeightInInch = 0.5;
  
    if (this.previewFigure) {
      this._previewFigure();
    } else {
      this._saveFigure();
    }
  }

  /**
   * Return a new instance of the Builder
   * @return new Builder
   */
  static builder() {
    return new D3SaveFigure.Builder();
  }

  /**
   * Builder for D3SaveFigure
   * 
   * @typedef {Object} D3SaveFigureBuilder - Builder class
   * @property {SVGElement} _centerSvgOnEl A SVG element to use to center
   *    the figure on the page
   * @property {SVGElement} _centerTitleOnEl A SVG element to use to 
   *    center the plot title on the page
   * @property {String} _filename Filename for download with no extension.
   * @property {String=} _plotTitle Title to add to plot.
   * @property {String} _plotFormat Format for download. 
   *     Formats: 'png' || 'svg' || 'pdf' || 'jpeg'
   * @property {Map<String, Array<String>>} _metadata The metadata for 
   *    the metadata table and footer 
   * @property {Number} _plotHeight Original SVG plot height in px.
   * @property {Number} _plotWidth Original SVG plot width in px. 
   * @property {Boolean} _previewFigure Whether to save or preview the figure.
   * @property {SVGElement} _svgEl SVG dom element to convert to image.
   */
  static get Builder() {
    return class Builder {

      constructor() {
        /** @type {SVGElement} */
        this._centerSvgOnEl = null;
        /** @type {SVGElement} */
        this._centerTitleOnEl = null;
        /** @type {String} */
        this._filename = 'download';
        /** @type {Object} */
        this._metadata = null;
        /** @type {D3SaveFigureOptions} */
        this._options = {};
        this._options.printMetadata = true;
        /** @type {String} */
        this._plotFormat = 'png';
        /** @type {Number} */
        this._plotHeight;
        /** @type {String} */
        this._plotTitle = '';
        /** @type {Number} */
        this._plotWidth;
        /** @type {Boolean} */
        this._previewFigure = false;
        /** @type {SVGElement} */
        this._svgEl;
      }
     
      /**
       * Return a new instance of D3SaveFigure
       * @return Save the SVG element as a figure.
       */
      build() {
        Preconditions.checkState(this._plotHeight, 'Current SVG height not set');
        Preconditions.checkState(this._plotWidth, 'Current SVG width not set');
        Preconditions.checkState(this._svgEl, 'SVG element not set');
        Preconditions.checkState(
            this._options.printMetadata && this._metadata != null,
            'Metadata not set');

       return new D3SaveFigure(this);
      }

      /**
       * A SVG element that is used to center the image. 
       * @param {SVGElement} centerEl The SVG element 
       */
      centerSvgOnEl(centerEl) {
        Preconditions.checkArgumentInstanceOfSVGElement(centerEl);
        this._centerSvgOnEl = centerEl.cloneNode(true);
        return this;
      }

      /**
       * A SVG element that is used to center the plot title. 
       * @param {SVGElement} centerEl The SVG element 
       */
      centerTitleOnEl(centerEl) {
        Preconditions.checkArgumentInstanceOfSVGElement(centerEl);
        this._centerTitleOnEl = centerEl.cloneNode(true);
        return this;
      }

      /**
       * The current SVG width attribute or viewbox width in pixels
       * @param {Number} width The width in pixels 
       */
      currentSvgWidth(width) {
        Preconditions.checkArgumentNumber(width);
        this._plotWidth = width;
        return this;
      }
      
      /**
       * The current SVG height attribute or viewbox height in pixels
       * @param {Number} height The height in pixels 
       */
      currentSvgHeight(height) {
        Preconditions.checkArgumentNumber(height);
        this._plotHeight = height;
        return this;
      }

      /**
       * The download filename 
       * @param {String} filename The download filename 
       */
      filename(filename) {
        Preconditions.checkArgumentString(filename);
        this._filename = filename;
        return this;
      }

      /**
       * The figure options 
       * @param {D3SaveFigureOptions} options The options 
       */
      options(options) {
        this._options = options;
        return this;
      }
      
      /**
       * The metadata for the metadata table
       * @param {Map<String, Array<String>} metadata The metadata 
       */
      metadata(metadata) {
        Preconditions.checkArgumentInstanceOfMap(metadata);
        
        for (let [key, value] of metadata) {
          Preconditions.checkArgumentString(key);
          Preconditions.checkArgumentArray(value);          
        }
        
        this._metadata = metadata;
        return this;
      }

      /**
       * The download format. 
       * Either: 'png' || 'jpeg' || 'pdf || 'svg'
       * @param {String} format The download format 
       */
      plotFormat(format) {
        Preconditions.checkArgumentString(format);

        Preconditions.checkArgument(
          format == 'jpeg' || format == 'pdf' || 
              format == 'png' || format == 'svg',
          `[${format}] not supported`);

        this._plotFormat = format.toLowerCase();
        return this;
      }

      /**
       * The plot title
       * @param {String} title The plot title 
       */
      plotTitle(title) {
        Preconditions.checkArgumentString(title);
        this._plotTitle = title;
        return this;
      }

      /**
       * Whether to preview the figure in a new window 
       *    instead of saving the figure
       */
      previewFigure() {
        this._previewFigure = true;
        return this;
      }

      /**
       * The SVG element to save as a figure
       * @param {SVGElement} svgEl The SVG element to turn into a figure 
       */
      svgEl(svgEl) {
        Preconditions.checkArgumentInstanceOfSVGElement(svgEl);
        this._svgEl = svgEl.cloneNode(true);
        return this;
      }

    }
  }
  
  /**
   * Add the metadata URL and date to the bottom of the page.
   * @param {SVGElement} svgEl - SVG element to append the footer information.
   */ 
  _addFooter(svgEl) {
    if (!this.options.printFooter) return;
   
    Preconditions.checkState(
        this.metadata.has('url'), 
        'URL not found in metadata');

    Preconditions.checkState(
      this.metadata.has('date'), 
      'Date not found in metadata');
    
    let footerText = [
      this.metadata.get('url'),
      this.metadata.get('date'),
    ];
    
    let nlines = footerText.length;

    let footerD3 = d3.select(svgEl)
        .append('g')
        .attr('class', 'print-footer')
        .style('font-size', this.options.footerFontSize)

    footerD3.selectAll('text')
        .data(footerText)
        .enter()
        .append('text')
        .text((d,i) => {return footerText[nlines - i - 1]})
        .attr('y', (d,i) => {
            return -this.options.footerLineBreak * i;
        });

    this._fitFooter(footerD3.node());
  }

  /**
   * Add a table of the metadata to the plot
   * @param {SVGElement} svgEl SVG element to append table to 
   * @param {PlotMargin} plotMargins The plot margins
   */
  _addMetadataTable(svgEl, plotMargins) {
    if (!this.options.printMetadata) return;

    let tableInfo = this._updateMetadataForTable();
    let maxColumnValues = this.options.metadataMaxColumnValues;

    let foreignD3 = d3.select(svgEl)
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
    let tableDim = tableEl.getBoundingClientRect();
    let tableWidthInPx = tableDim.width;
    let tableHeightInPx = tableDim.height;

    this._fitTable(
      foreignD3.node(), 
      tableHeightInPx, 
      tableWidthInPx, 
      plotMargins);
  }

  /**
   * Add the plot title 
   * @param {SVGElement} svgEl The SVG element to add the title 
   * @param {PlotMargin} plotMargins The plot margins
   */
  _addPlotTitle(svgEl, plotMargins) {
    if (!this.options.printTitle) return;

    let mainGroupD3 = d3.select(svgEl).select('g');

    let titleY = - plotMargins.marginTopPx / 2;
    let titleX = this.centerTitleOnEl != null ?
        this.centerTitleOnEl.getBoundingClientRect().width / 2 : 0;

    mainGroupD3.append('text')
        .attr('class', 'plot-title')
        .attr('x', titleX) 
        .attr('y', titleY)
        .attr('text-anchor', 'middle')
        .attr('alignment-baseline', 'middle')
        .style('font-size', this.options.titleFontSize)
        .text(this.plotTitle);
  }

  /**
   * Create the canvas element 
   * 
   * @typedef {Object} CanvasObject - The canvas element and context
   * @property {HTMLCanvasElement} canvasEl The canvas element
   * @property {CanvasRenderingContext2D} The canvas context 
   * @return {CanvasObject} The canvas element and context
   */  
  _createCanvas() {
    let canvasDivD3 = d3.select('body')
        .append('div')
        .attr('class', 'svg-to-canvas hidden');

    let canvasD3 = canvasDivD3.append('canvas')
        .attr('height', this.pageHeightPxPrintDpi)
        .attr('width', this.pageWidthPxPrintDpi)
        .style('height', `${this.options.pageHeight}in`)
        .style('width', `${this.options.pageWidth}in`);

    let canvasEl = canvasD3.node();
    let canvasContext = canvasEl.getContext('2d');
    canvasDivD3.remove();
    
    return {canvasEl: canvasEl, canvasContext: canvasContext};
  }


  /**
   * Create the SVG element
   * @return {SVGElement} The svg element
   */  
  _createSvgElement() {
    let iframeD3 = d3.select('body')
        .append('iframe')
        .style('visibility', 'hidden');

    let svgDivD3 = d3.select(iframeD3.node().contentWindow.document.body)
        .append('div')
        .attr('class', 'print-plot-svg')
        .html(this.svgEl.outerHTML);
    
    let svgD3 = svgDivD3.select('svg')
      .attr('viewBox',  
          `0 0 ${this.pageWidthPxPrintDpi} ${this.pageHeightPxPrintDpi}`)
        .style('font-family', '"helvetica neue",helvetica,arial,sans-serif')
        .attr('height', this.pageHeightPxPrintDpi)
        .attr('width', this.pageWidthPxPrintDpi);

    let svgEl = svgD3.node();
    this.centerSvgOnEl = this._findMatchingElement(svgEl, this.centerSvgOnEl);
    this.centerTitleOnEl = this._findMatchingElement(svgEl, this.centerTitleOnEl);
    let plotMargins = this._translateSvg(svgEl);
    this._addPlotTitle(svgEl, plotMargins);
    this._addFooter(svgEl);
    this._addMetadataTable(svgEl, plotMargins);
   
    iframeD3.remove();
   
    return svgEl;
  }

  /**
   * Create the SVG image soruce 
   * @param {SVGElement} svgEl The SVg element for the figure
   */
  _createSvgImageSource(svgEl) {
    return `data:image/svg+xml;base64, 
        ${btoa(unescape(encodeURIComponent(svgEl.outerHTML)))}`;             
  }

  /**
   * Draw the Canvas from the SVG image and scale to set the 
   *    correct DPI
   * @param {CanvasObject} canvas The canvas element and context 
   * @param {ImageData} svgImg The SVG image 
   */
  _drawCanvas(canvas, svgImg) {
    let dpiScale = this.options.printDpi / this.baseDpi;
    
    canvas.canvasContext.scale(dpiScale, dpiScale);
    canvas.canvasContext.fillStyle = 'white';
    canvas.canvasContext.fillRect(0, 0, 
        this.pageWidthPxPrintDpi, this.pageHeightPxPrintDpi);
    canvas.canvasContext.drawImage(svgImg, 0, 0, 
          this.pageWidthPxPrintDpi, this.pageHeightPxPrintDpi);
  }

  /**
   * Given a SVG element, find the matching element inside the main
   *    SVG element of the figure
   * @param {SVGElement} svgEl The main SVG element of the figure 
   * @param {SVGElement} centerEl The SVG element to find inside the main 
   *    figure SVG element
   */
  _findMatchingElement(svgEl, centerEl) {
    if (centerEl == null) return;

    let el = d3.select(svgEl)
        .selectAll('*')
        .filter((d, i, els) => {
          return centerEl.isEqualNode(els[i]);
        }).node();
   
    if (el == null) {
      throw new NshmpError(`Could not find ${centerEl}`);
    }

    return el;
  }

  /**
   * Scale the footer if needed to fit on the page 
   * @param {SVGElement} footerEl The footer SVG element 
   */
  _fitFooter(footerEl) {
    let footerDim = footerEl.getBoundingClientRect();
    let footerWidthInInch = footerDim.width / this.baseDpi;
    let footerHeightInInch = footerDim.height / this.baseDpi;
    let footerPaddingInInch = this.options.footerPadding / this.baseDpi;

    let pageWidth = this.options.pageWidth - footerPaddingInInch * 2;
     
    let widthScale = footerWidthInInch > pageWidth ?
        pageWidth / footerWidthInInch : 1;

    let footerHeight = this.footerHeightInch - footerPaddingInInch;
    let heightScale = footerHeightInInch > footerHeight ?
        footerHeight / footerHeightInInch : 1;

    let footerScale = Math.min(heightScale, widthScale);

    let footerPadding = this.options.footerPadding;
    let footerTransform = `translate(${footerPadding}, ` +
        `${this.pageHeightPxBaseDpi - footerPadding}) scale(${footerScale})`; 

    d3.select(footerEl).attr('transform', footerTransform);
  }

  /**
   * Scale the metadata table to fit under the figure
   * @param {SVGElement} foreignObjectEl The Foriegn Object SVG element
   * @param {Number} tableHeightInPx The table height in pixels
   * @param {Number} tableWidthInPx The table width in pixels
   * @param {PlotMargin} plotMargins The plot margins
   */
  _fitTable(foreignObjectEl, tableHeightInPx, tableWidthInPx, plotMargins) {
    let svgHeightInInch = this.originalPlotHeight / this.baseDpi;
    let tableMarginTopInPx = this.options.metadataMarginTop * this.baseDpi;

    let tableHeightInInch = tableHeightInPx / this.baseDpi; 
    let tableWidthInInch = tableWidthInPx / this.baseDpi;

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

    let centerTableX = - plotMargins.marginLeftPx + ( this.pageWidthPxBaseDpi - 
        ( tableWidthInPx * tableScale )) / 2;

    d3.select(foreignObjectEl)
        .attr('transform', `scale(${tableScale})`)
        .attr('height', tableHeightInPx)
        .attr('width', tableWidthInPx)
        .attr('y', (this.originalPlotHeight + tableMarginTopInPx) / tableScale)
        .attr('x', centerTableX / tableScale);
  }

  /**
   * Convert the metadata Map into a set of arrays to be used to create
   *    the table
   * @param {Map<String, Array<String>} metadata The metadata Map
   * @param {Number} rows The number of rows the table will have
   * @param {Number} maxColumns The number of key-value columns the table
   *    will have
   * @param {Boolean} expandColumn Whether the first item in the metadata
   *    Map will expand all rows 
   * @return {Set<Array<String, Array<String>>>} The metadata set of arrays
   */
  _metadataToSet(metadata, rows, maxColumns, expandColumn) {
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
   * Open the figure in a new window to preview
   */
  _previewFigure() {
    let svgImg = new Image();
    let svgEl = this._createSvgElement();

    d3.select(svgEl)
        .attr('height', this.pageHeightPxBaseDpi * this.options.printDpi)
        .attr('width', this.pageWidthPxBaseDpi * this.options.printDpi)
        .style('background', 'white');
    
    let svgImgSrc = this._createSvgImageSource(svgEl); 
    let canvas = this._createCanvas();
    
    svgImg.onload = () => {
      this._drawCanvas(canvas, svgImg); 

      try {
        switch (this.plotFormat) {
          /* SVG format */
          case 'svg':
            this._previewFigureNewWindow(svgImgSrc);
            break;
          /* JPEG or PNG format */
          case 'png':
          case 'jpeg':
            canvas.canvasEl.toBlob((blob) => {
              let canvasImage = URL.createObjectURL(blob);
              this._previewFigureNewWindow(canvasImage);
            }, 'image/' + this.plotFormat, 1.0);
            break;
        }
      } catch (err) {
        throw new NshmpError(err);
      }
    }  

    svgImg.setAttribute('src', svgImgSrc);
  }

  /**
   * Open the new window with specific image
   * @param {ImageData} imageSrc The image source
   */
  _previewFigureNewWindow(imageSrc) {
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

    this._previewFigureDownloadButton(win, imageSrc);
  }

  /**
   * Add a download button to the new window for the figure preview
   * @param {Window} win The new window
   */
  _previewFigureDownloadButton(win, imageSrc) {
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
        .on('click', () => { this._saveFigure(); });
  }

  /**
   * Save the figure as PDF using jsPDF
   * @param {CanvasObject} canvas Canvas element and context 
   */
  _saveAsPdf(canvas) {
    let height = this.options.pageHeight;
    let width = this.options.pageWidth;
    let pdf = new jsPDF('landscape', 'in', [width, height]);
    canvas.canvasEl.toBlob((blob) => {
      pdf.addImage(
        URL.createObjectURL(blob), 
        'PNG', 
        0, 0,
        width, 
        height);

      pdf.save(this.filename);
    }, 'image/png', 1.0);
  }

  /**
   * Save the figure as a PNG or JPEG
   * @param {CanvasObject} canvas Canvas element and context
   * @param {HTMLElement} aEl The HTML element of an anchor 
   */
  _saveAsPngJpeg(canvas, aEl) {
    canvas.canvasEl.toBlob((blob) => {
      aEl.href = URL.createObjectURL(blob);
      aEl.click();
    }, 'image/' + this.plotFormat, 1.0);
  }

  /**
   * Save the SVG figure as specified format
   */
  _saveFigure() {
    let svgImg = new Image();
    let svgEl = this._createSvgElement();
    let svgImgSrc = this._createSvgImageSource(svgEl); 
    let canvas = this._createCanvas();
    
    svgImg.onload = () => {
      this._drawCanvas(canvas, svgImg); 

      let aEl = document.createElement('a');
      aEl.download = this.filename; 

      try {
        switch (this.plotFormat) {
          /* SVG format */
          case 'svg':
            aEl.href = svgImg.src;
            aEl.click();
            break;
          /* JPEG or PNG format */
          case 'png':
          case 'jpeg':
            this._saveAsPngJpeg(canvas, aEl);
            break;
          /* PDF format */
          case 'pdf':
            this._saveAsPdf(canvas);
            break;
        }
      } catch (err) {
        throw new NshmpError(err);
      }
    }  

    svgImg.setAttribute('src', svgImgSrc);
  }

  /**
   * Translate the SVG main group
   * 
   * @typedef {Object}  PlotMargin
   * @property {Number} marginLeftPx Plot margin left in pixels
   * @property {Number} marginTopPx Plot margin top in pixels
   * 
   * @param {SVGElement} svgEl The main SVG element of the figure
   * @return {PlotMargin} The left and top plot margins in pixels
   */
  _translateSvg(svgEl) {
    let marginTopPx = this.options.marginTop * this.baseDpi; 
    let marginLeftPx;

    if (this.centerSvgOnEl != null) {
      let centerElDim = this.centerSvgOnEl.getBoundingClientRect();
      let centerElWidthPx = centerElDim.width;
      marginLeftPx = ( this.pageWidthPxBaseDpi - centerElWidthPx ) / 2;
    } else {
      marginLeftPx = this.options.marginLeft * this.baseDpi; 
    }

    let plotTransform = `translate(${marginLeftPx}, ${marginTopPx})`;
    d3.select(svgEl)
        .select('g')
        .attr('transform', plotTransform);

    return {marginLeftPx: marginLeftPx, marginTopPx: marginTopPx};
  }

  /**
   * Update the metadata Map to put the key with a values array
   *    greater than 1 in the first position and convert Map to
   *    a Set 
   * 
   * @typedef {Object} MetadataTableInfo - Parameters to make table.
   * @property {Set<Array<String, Array<String>>>} metadataSet
   *    Set of metadata
   * @property {Number} rows The number of rows in table
   * 
   * @return {MetadataTableInfo} The table parameters
   */
  _updateMetadataForTable() {
    let metadata = new Map(this.metadata); 
    metadata.delete('url')
    metadata.delete('date');

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

}
