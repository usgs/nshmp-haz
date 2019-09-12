
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview The options for D3SaveFigure
 * 
 * Use D3SaveFigureOptions.builder() to get new instance of D3SaveFigureOptionsBuilder
 * See D3SaveFigureOptions.builder()
 * See D3SaveFigureOptionsBuilder
 * 
 * @class D3SaveFigureOptions
 * @author Brandon Clayton
 */
export class D3SaveFigureOptions {

  /**
   * 
   * @param {D3SaveFigureOptionsBuilder} builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3SaveFigureOptionsBuilder);

    /**
     * Whether to add a footer containing the URL and date to the plot page.
     * Default: true
     * @type {Boolean}
     */
    this.addFooter = builder._addFooter;

    /**
     * Whether to add a table of the metadata.
     * Note: Metadata must be set using D3BaseView.setMetadata()
     * Default: true
     * @type {Boolean} 
     */
    this.addMetadata = builder._addMetadata;

    /**
     * Whether to add the plot title.
     * Default: true
     * @type {Boolean}
     */
    this.addTitle = builder._addTitle;

    /**
     * The DPI to save the figure at.
     * Default: 300
     * @type {Number}
     */
    this.dpi = builder._dpi;
    
    /**
     * The font size of the footer.
     * Default: 10
     * @type {Number}
     */
    this.footerFontSize = builder._footerFontSize;
    
    /**
     * The footer line break distance in pixels.
     * Deafult: 14
     * @type {Number} 
     */
    this.footerLineBreak = builder._footerLineBreak;
    
    /**
     * The padding around the footer.
     * Default: 10
     * @type {Number}
     */
    this.footerPadding = builder._footerPadding;
    
    /**
     * The left margin for the figure on the page. Can be either
     *    'center' or number in inches.
     * Default: 'center'
     * @type {String | Number}
     */
    this.marginLeft = builder._marginLeft;
    
    /**
     * The top margin for the figure on the page in inches.
     * Default: 0.5
     * @type {Number}
     */
    this.marginTop = builder._marginTop;
    
    /**
     * The number of columns allowed in the metadata table.
     * Default: 3
     * @type {Number}
     */
    this.metadataColumns = builder._metadataColumns;
    
    /**
     * The metadata table font size in pixels.
     * Default: 10
     * @type {Number}
     */
    this.metadataFontSize = builder._metadataFontSize;
    
    /**
     * The top margin in inches from the bottom of the figure 
     *    to the metadata table.
     * Default: 0.5
     * @type {Number}
     */
    this.metadataMarginTop = builder._metadataMarginTop;
    
    /**
     * The maximum number of values in a column in the metadata table.
     * Once the values is greater than this a '... and X more ...' is added.
     * Default: 5
     * @type {Number}
     */
    this.metadataMaxColumnValues = builder._metadataMaxColumnValues;
    
    /**
     * The page height in inches.
     * Default: 8.5
     * @type {Number} 
     */
    this.pageHeight = builder._pageHeight;
    
    /**
     * The page width in inches.
     * Default: 11.0
     * @type {Number}
     */
    this.pageWidth = builder._pageWidth;
    
    /**
     * The title font size.
     * Default: 14
     * @type {Number}
     */
    this.titleFontSize = builder._titleFontSize;
    
    /**
     * The title location, either: 'center' || 'left'
     * Default: 'center'
     * @type {String}
     */
    this.titleLocation = builder._titleLocation;

    /* Make immutable */
    Object.freeze(this);
  }

  /**
   * Returns a new D3SaveFigureOptionsBuilder
   */
  static builder() {
    return new D3SaveFigureOptionsBuilder();
  }

  /**
   * Returns new D3SaveFigureOptions with default values
   */
  static withDefaults() {
    return D3SaveFigureOptions.builder().build();
  }

}

/**
 * @fileoverview The D3SaveFigureOptions builder
 * 
 * Use D3SaveFigureOptions.builder() for new instance of builder
 * 
 * @class D3SaveFigureOptionsBuilder
 * @author Brandon Clayton
 */
export class D3SaveFigureOptionsBuilder {

  constructor() {
    /** @type {Boolean} */
    this._addFooter = true;

    /** @type {Boolean} */
    this._addMetadata = true;
    
    /** @type {Boolean} */
    this._addTitle = true;
    
    /** @type {Number} */
    this._dpi = 300;
    
    /** @type {Number} */
    this._footerFontSize = 10;
    
    /** @type {Number} */
    this._footerLineBreak = 14;
    
    /** @type {Number} */
    this._footerPadding = 10;
    
    /** @type {String} */
    this._marginLeft = 'center';
    
    /** @type {Number} */
    this._marginTop = 0.5;
    
    /** @type {Number} */
    this._metadataColumns = 3;
    
    /** @type {Number} */
    this._metadataFontSize = 10;
    
    /** @type {Number} */
    this._metadataMarginTop = 0.5;
    
    /** @type {Number} */
    this._metadataMaxColumnValues = 5;
    
    /** @type {Number} */
    this._pageHeight = 8.5;
    
    /** @type {Number} */
    this._pageWidth = 11;
    
    /** @type {Number} */
    this._titleFontSize = 14;
    
    /** @type {String} */
    this._titleLocation = 'center';
  }

  /**
   * Return new instance of D3SaveFigureOptions
   */
  build() {
    return new D3SaveFigureOptions(this);
  }

  /**
   * Set whether to add a footer containing the URL and date to the plot page.
   * Default: true
   * 
   * @param {Boolean} addFooter Whether to add footer
   */
  addFooter(addFooter) {
    Preconditions.checkArgumentBoolean(addFooter);
    this._addFooter = addFooter;
    return this;
  }

  /**
   * Set whether to add a table of the metadata.
   * Note: Metadata must be set using D3BaseView.setMetadata()
   * Default: true
   * 
   * @param {Boolean} addMetadata Whether to add metadata table
   */
  addMetadata(addMetadata) {
    Preconditions.checkArgumentBoolean(addMetadata);
    this._addMetadata = addMetadata;
    return this;
  }

  /**
   * Set whether to add the plot title.
   * Default: true
   * 
   * @param {Boolean} addTitle Whether to add title
   */
  addTitle(addTitle) {
    Preconditions.checkArgumentBoolean(addTitle);
    this._addTitle = addTitle;
    return this;
  }

  /**
   * Set the DPI to save the figure at.
   * Default: 300
   * 
   * @param {Number} dpi The plot DPI to save at
   */
  dpi(dpi) {
    Preconditions.checkArgumentInteger(dpi);
    this._dpi = dpi;
    return this;
  }

  /**
   * Set the font size of the footer.
   * Default: 10
   * 
   * @param {Number} fontSize The footer font size
   */
  footerFontSize(fontSize) {
    Preconditions.checkArgumentInteger(fontSize);
    this._footerFontSize = fontSize;
    return this;
  }

  /**
   * Set the footer line break distance in pixels.
   * Deafult: 14
   * 
   * @param {Number} lineBreak The footer line break
   */
  footerLineBreak(lineBreak) {
    Preconditions.checkArgumentNumber(lineBreak);
    this._footerLineBreak = lineBreak;
    return this;
  }

  /**
   * Set the padding around the footer.
   * Default: 10
   * 
   * @param {Number} padding The padding around the footer
   */
  footerPadding(padding) {
    Preconditions.checkArgumentInteger(padding);
    this._footerPadding = padding;
    return this;
  }

  /**
   * Set the left margin for the figure on the page. Can be either
   *    'center' or number in inches.
   * Default: 'center'
   * 
   * @param {String | Number} marginLeft The left margin of the figure
   */
  marginLeft(marginLeft) {
    Preconditions.checkArgument(
        typeof marginLeft == 'number' || typeof marginLeft == 'string',
        `Wrong type [${typeof marginLeft}]`);

    if (typeof marginLeft == 'string') {
      marginLeft.toLowerCase();
      Preconditions.checkArgument(
          marginLeft == 'center', 
          `margin type [${marginLeft}] not supported`);
    }

    return this;
  }

  /**
   * Set the top margin for the figure on the page in inches.
   * Default: 0.5
   * 
   * @param {Number} marginTop The top margin of the plot
   */
  marginTop(marginTop) {
    Preconditions.checkArgumentInteger(marginTop);
    this._marginTop = marginTop;
    return this;
  }

  /**
   * Set the number of columns allowed in the metadata table.
   * Default: 3
   * 
   * @param {Number} columns The number of metadata table columns
   */
  metadataColumns(columns) {
    Preconditions.checkArgumentInteger(columns);
    this._metadataColumns = columns;
    return this;
  }

  /**
   * Set the metadata table font size in pixels.
   * Default: 10
   * 
   * @param {Number} fontSize The metadata table font size
   */
  metadataFontSize(fontSize) {
    Preconditions.checkArgumentInteger(fontSize);
    this._metadataFontSize = fontSize;
    return this;
  }

  /**
   * Set the top margin in inches from the bottom of the figure 
   *    to the metadata table.
   * Default: 0.5
   * 
   * @param {Number} marginTop The metadata top margin
   */
  metadataMarginTop(marginTop) {
    Preconditions.checkArgumentNumber(marginTop);
    this._metadataMarginTop = marginTop;
    return this;
  }

  /**
   * Set the maximum number of values in a column in the metadata table.
   * Once the values is greater than this a '... and X more ...' is added.
   * Default: 5
   * 
   * @param {Number} max The max column values
   */
  metadataMaxColumnValues(max) {
    Preconditions.checkArgumentInteger(max);
    this._metadataMaxColumnValues = max;
    return this;
  }

 
  /**
   * Set the page height in inches.
   * Default: 8.5
   * 
   * @param {Number} height The page height in inches
   */
  pageHeight(height) {
    Preconditions.checkArgumentNumber(height);
    this._pageHeight = height;
    return this;
  }

  /**
   * Set the page width in inches.
   * Default: 11.0
   * 
   * @param {Number} width The page width in inches
   */
  pageWidth(width) {
    Preconditions.checkArgumentNumber(width);
    this._pageWidth = width;
    return this;
  }

  /**
   * Set the title font size.
   * Default: 14
   * 
   * @param {Number} fontSize The title font size
   */
  titleFontSize(fontSize) {
    Preconditions.checkArgumentInteger(fontSize);
    this._titleFontSize = fontSize;
    return this;
  }

  /**
   * Set the title location, either: 'center' || 'left'
   * Default: 'center'
   * 
   * @param {String} loc The title location
   */
  titleLocation(loc) {
    Preconditions.checkArgumentString(loc);
    loc = loc.toLowerCase();
    Preconditions.checkArgument(
        loc == 'center' || loc == 'left',
        `Title location [${loc}] not supported`);

    return this;
  }

}
