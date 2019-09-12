
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview The options for D3LineLegend
 * 
 * Use D3LineLegendOptions.builder() to customize legend options or
 *    D3LineLegendOptions.withDefaults() for default legend options.
 * 
 * @class D3LineLegendOptions
 * @author Brandon Clayton 
 */
export class D3LineLegendOptions {

  /**
   * 
   * @param {D3LineLegendOptionsBuilder} builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3LineLegendOptionsBuilder);

    /**
     * The legend background color; 'none' for no color.
     * Default: 'white'
     * @type {String}
     */
    this.backgroundColor = builder._backgroundColor;

    /**
     * The legend border color.
     * Default: 'gray'
     * @type {String}
     */
    this.borderColor = builder._borderColor;

    /**
     * The legend border width in px.
     * Default: 1
     * @type {Number} width The border width in px 
     */
    this.borderLineWidth = builder._borderLineWidth;
    
    /**
     * The legend border radius in px.
     * Default: 4
     * @type {Number} radius The border radius 
     */
    this.borderRadius = builder._borderRadius;

    /**
     * The legend CSS border style.
     * Default: 'solid'
     * @type {String}
     */
    this.borderStyle = builder._borderStyle;

    /**
     * The legend font size
     * Default: 12
     * @type {Number}
     */
    this.fontSize = builder._fontSize;
    
    /**
     * The line length of the line shown in the legend
     * Default: 40
     * @type {Number}
     */
    this.lineLength = builder._lineLength;

    /**
     * The legend location:
     *    - 'bottom-left'
     *    - 'bottom-right'
     *    - 'top-left'
     *    - 'top-right'
     * Default: 'top-right'
     * @type {String}
     */
    this.location = builder._location;

    /**
     * The bottom margin of the legend
     * Default: 10
     * @type {Number}
     */
    this.marginBottom = builder._marginBottom;
    
    /**
     * The left margin of the legend
     * Default: 10
     * @type {Number}
     */
    this.marginLeft = builder._marginLeft;
    
    /**
     * The right margin of the legend
     * Default: 10
     * @type {Number}
     */
    this.marginRight = builder._marginRight;
    
    /**
     * The top margin of the legend
     * Default: 10
     * @type {Number}
     */
    this.marginTop = builder._marginTop;
   
    /**
     * The number of maximum rows a legend can have. If a legend
     *    has more rows then maxRows a '... and X more ...' is 
     *    added to the legend.
     * Default: 20 (upper sub view) || 4 (lower sub view)
     * @type {Number}
     */
    this.maxRows = builder._maxRows;

    /**
     * The number of columns for the legend to have.
     * Default: 1
     * @type {Number}
     */
    this.numberOfColumns = builder._numberOfColumns;

    /**
     * The bottom padding in the legend 
     * Default: 10
     * @type {Number}
     */
    this.paddingBottom = builder._paddingBottom;
    
    /**
     * The left padding in the legend. 
     * Default: 10
     * @type {Number}
     */
    this.paddingLeft = builder._paddingLeft;
    
    /**
     * The right padding in the legend. 
     * Default: 10
     * @type {Number}
     */
    this.paddingRight = builder._paddingRight;
    
    /**
     * The top padding in the legend. 
     * Default: 10
     * @type {Number}
     */
    this.paddingTop = builder._paddingTop;

    /* Make immutable */
    Object.freeze(this);
  }

  /**
   * Return new D3LineLegendOptionsBuilder for lower sub view.
   * Only difference between lowerBuilder and upperBuilder is 
   *    maxRows is set to 4 for lowerBuilder and 20 for upperBuilder.
   * 
   * @returns {D3LineLegendOptionsBuilder} New options builder
   */
  static lowerBuilder() {
    const LOWER_PLOT_MAX_ROWS = 4;
    return new D3LineLegendOptionsBuilder().maxRows(LOWER_PLOT_MAX_ROWS); 
  }

  /**
   * Return new D3LineLegendOptionsBuilder for upper sub view.
   * Only difference between lowerBuilder and upperBuilder is 
   *    maxRows is set to 6 for lowerBuilder and 22 for upperBuilder.
   * 
   * @returns {D3LineLegendOptionsBuilder} New options builder
   */
  static upperBuilder() {
    return new D3LineLegendOptionsBuilder(); 
  }

  /**
   * Return new D3LineLegendOptions with defaults for lower sub view.
   * Only difference between lowerBuilder and upperBuilder is 
   *    maxRows is set to 6 for lowerBuilder and 22 for upperBuilder.
   * 
   * @returns {D3LineLegendOptions} New options with defaults
   */
  static lowerWithDefaults() {
    return D3LineLegendOptions.lowerBuilder().build();
  }

  /**
   * Return new D3LineLegendOptions with defaults for upper sub view.
   * Only difference between lowerBuilder and upperBuilder is 
   *    maxRows is set to 6 for lowerBuilder and 22 for upperBuilder.
   * 
   * @returns {D3LineLegendOptions} New options with defaults
   */
  static upperWithDefaults() {
    return D3LineLegendOptions.upperBuilder().build();
  }

}

/**
 * @fileoverview Builder for D3LineLegendOptions 
 * 
 * Use D3LineLegendOptions.builder() to get new instance of builder.
 * 
 * @class D3LineLegendOptionsBuilder
 * @author Brandon Clayton
 */
export class D3LineLegendOptionsBuilder { 

  /** @private */
  constructor() {
    /** @type {String} */
    this._backgroundColor = 'white';

    /** @type {String} */
    this._borderColor = 'gray';
    
    /** @type {Number} */
    this._borderLineWidth = 1;
    
    /** @type {Number} */
    this._borderRadius = 4;
    
    /** @type {String} */
    this._borderStyle = 'solid';
    
    /** @type {Number} */
    this._fontSize = 12;
    
    /** @type {Number} */
    this._lineLength = 40;
    
    /** @type {String} */
    this._location = 'top-right';
    
    /** @type {Number} */
    this._marginBottom = 10;
    
    /** @type {Number} */
    this._marginLeft = 10;
    
    /** @type {Number} */
    this._marginRight = 10;
    
    /** @type {Number} */
    this._marginTop = 10;
    
    /** @type {Number} */
    this._maxRows = 20;
    
    /** @type {Number} */
    this._numberOfColumns = 1;
    
    /** @type {Number} */
    this._paddingBottom = 10;
    
    /** @type {Number} */
    this._paddingLeft = 10;
    
    /** @type {Number} */
    this._paddingRight = 10;
    
    /** @type {Number} */
    this._paddingTop = 10;
  }

  /**
   * Return new D3LineLegendOptions 
   * @returns {D3LineLegendOptions} New legend options
   */
  build() {
    return new D3LineLegendOptions(this); 
  }

  /**
   * Set the legend background color; 'none' for no color.
   * Default: 'white'
   *  
   * @param {String} color The background color 
   */
  backgroundColor(color) {
    Preconditions.checkArgumentString(color);
    this._backgroundColor = color;
    return this;
  }

  /**
   * Set the legend border color.
   * Default: 'gray'
   * 
   * @param {String} color The border color 
   */
  borderColor(color) {
    Preconditions.checkArgumentString(color);
    this._borderColor = color;
    return this;
  }

  /**
   * Set the legend border width in px.
   * Default: 1
   * 
   * @param {Number} width The border width in px 
   */
  borderLineWidth(width) {
    Preconditions.checkArgumentInteger(width);
    this._borderLineWidth = width;
    return this;
  }

  /**
   * Set the legend border radius in px.
   * Default: 4
   * 
   * @param {Number} radius The border radius 
   */
  borderRadius(radius) {
    Preconditions.checkArgumentInteger(radius);
    this._borderRadius = radius;
    return this;
  }

  /**
   * Set the lgeend CSS border style.
   * Default: 'solid'
   * 
   * @param {String} style The border style 
   */
  borderStyle(style) {
    Preconditions.checkArgumentString(style);
    this._borderStyle = style;
    return this;
  }

  /**
   * Set the tooltip font size.
   * Default: 12
   * 
   * @param {Number} size The font size 
   */
  fontSize(size) {
    Preconditions.checkArgumentInteger(size);
    this._fontSize = size;
    return this;
  }

  /**
   * Set the line length for the line shown in the legend.
   * Default: 40
   * @param {Number} length 
   */
  lineLength(length) {
    Preconditions.checkArgumentNumber(length);
    this._lineLength = length;
    return this;
  }

  /**
   * Set the legend location:
   *    - 'bottom-left'
   *    - 'bottom-right'
   *    - 'top-left'
   *    - 'top-right'
   * Default: 'top-right'
   *  
   * @param {String} loc Legend location 
   */
  location(loc) {
    Preconditions.checkArgumentString(loc);
    loc = loc.toLowerCase();
    Preconditions.checkArgument(
        loc == 'bottom-left' ||
        loc == 'bottom-right' || 
        loc == 'top-left' ||
        loc == 'top-right', 
        `Legend location [${loc}] not supported`);

    this._location = loc;
    return this;
  }

  /**
   * Set the bottom margin of the legend.
   * Default: 10
   * 
   * @param {Number} margin The bottom margin in px
   */
  marginBottom(margin) {
    Preconditions.checkArgumentInteger(margin); 
    this._marginBottom = margin;
    return this;
  }

  /**
   * Set the left margin of the legend.
   * Default: 10
   * 
   * @param {Number} margin The left margin in px
   */
  marginLeft(margin) {
    Preconditions.checkArgumentInteger(margin); 
    this._marginLeft = margin;
    return this;
  }

  /**
   * Set the right margin of the legend.
   * Default: 10
   * 
   * @param {Number} margin The right margin in px
   */
  marginRight(margin) {
    Preconditions.checkArgumentInteger(margin); 
    this._marginRight = margin;
    return this;
  }

  /**
   * Set the top margin of the legend.
   * Default: 10
   * 
   * @param {Number} margin The top margin in px
   */
  marginTop(margin) {
    Preconditions.checkArgumentInteger(margin); 
    this._marginTop = margin;
    return this;
  }

  /**
   * Set the number of maximum rows a legend can have. If a legend
   *    has more rows then maxRows a '... and X more ...' is 
   *    added to the legend.
   * Default: 10
   *  
   * @param {Number} rows The max rows 
   */
  maxRows(rows) {
    Preconditions.checkArgumentInteger(rows);
    this._maxRows = rows;
    return this;
  }

  /**
   * Set the number of columns for the legend to have.
   * Default: 1
   * 
   * @param {Number} col The number of columns in the legend
   */
  numberOfColumns(col) {
    Preconditions.checkArgumentInteger(col);
    this._numberOfColumns = col;
    return this;
  }

  /**
   * Set the bottom padding in the tooltip.
   * Default: 10
   * 
   * @param {Number} pad The bottom padding in px
   */
  paddingBottom(pad) {
    Preconditions.checkArgumentInteger(pad); 
    this._paddingBottom = pad;
    return this;
  }

  /**
   * Set the left padding in the tooltip.
   * Default: 10
   * 
   * @param {Number} pad The left padding in px
   */
  paddingLeft(pad) {
    Preconditions.checkArgumentInteger(pad); 
    this._paddingLeft = pad;
    return this;
  }

  /**
   * Set the right padding in the tooltip.
   * Default: 10
   * 
   * @param {Number} pad The right padding in px
   */
  paddingRight(pad) {
    Preconditions.checkArgumentInteger(pad); 
    this._paddingRight = pad;
    return this;
  }

  /**
   * Set the top padding in the tooltip.
   * Default: 10
   * 
   * @param {Number} pad The top padding in px
   */
  paddingTop(pad) {
    Preconditions.checkArgumentInteger(pad); 
    this._paddingTop = pad;
    return this;
  }

}
