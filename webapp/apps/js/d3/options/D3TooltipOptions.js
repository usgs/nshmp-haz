
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview The options for D3Tooltip
 * 
 * Use D3TooltipOptions.builder() to customize tooltip options or
 *    D3TooltipOptions.withDefaults() for default tooltip options.
 * 
 * @class D3TooltipOptions
 * @author Brandon Clayton 
 */
export class D3TooltipOptions {

  /**
   * 
   * @param {D3TooltipOptionsBuilder} builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3TooltipOptionsBuilder);

    /**
     * The tooltip background color; 'none' for no color.
     * Default: 'white'
     * @type {String}
     */
    this.backgroundColor = builder._backgroundColor;

    /**
     * The tooltip border color.
     * Default: 'gray'
     * @type {String}
     */
    this.borderColor = builder._borderColor;

    /**
     * The tooltip border width in px.
     * Default: 1
     * @type {Number} width The border width in px 
     */
    this.borderLineWidth = builder._borderLineWidth;
    
    /**
     * The tooltip border radius in px.
     * Default: 4
     * @type {Number} radius The border radius 
     */
    this.borderRadius = builder._borderRadius;

    /**
     * The tooltip CSS border style.
     * Default: 'solid'
     * @type {String}
     */
    this.borderStyle = builder._borderStyle;

    /**
     * The tooltip font size
     * Default: 12
     * @type {Number}
     */
    this.fontSize = builder._fontSize;
    
    /**
     * The X offset of the tooltip from the data point
     * Default: 2
     * @type {Number}
     */
    this.offsetX = builder._offsetX;
    
    /**
     * The Y offset of the tooltip from the data point
     * Default: 10
     * @type {Number}
     */
    this.offsetY = builder._offsetY;
    
    /**
     * The bottom padding in the tooltip
     * Default: 10
     * @type {Number}
     */
    this.paddingBottom = builder._paddingBottom;
    
    /**
     * The left padding in the tooltip
     * Default: 10
     * @type {Number}
     */
    this.paddingLeft = builder._paddingLeft;
    
    /**
     * The right padding in the tooltip
     * Default: 10
     * @type {Number}
     */
    this.paddingRight = builder._paddingRight;
    
    /**
     * The top padding in the tooltip
     * Default: 10
     * @type {Number}
     */
    this.paddingTop = builder._paddingTop;

    /* Make immutable */
    Object.freeze(this);
  }

  /**
   * Return new D3TooltipOptionsBuilder
   * @returns {D3TooltipOptionsBuilder} New options builder
   */
  static builder() {
    return new D3TooltipOptionsBuilder();
  }

  /**
   * Return new D3TooltipOptions with defaults
   * @returns {D3TooltipOptions} New options with defaults
   */
  static withDefaults() {
    return D3TooltipOptions.builder().build();
  }

}

/**
 * @fileoverview Builder for D3TooltipOptions
 * 
 * Use D3TooltipOptions.builder() to get new instance of builder.
 * 
 * @class D3TooltipOptionsBuilder
 * @author Brandon Clayton
 */
export class D3TooltipOptionsBuilder {

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
    this._offsetX = 2;
    
    /** @type {Number} */
    this._offsetY = 8;
    
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
   * Return new D3TooltipOptions
   * @returns {D3TooltipOptions} New tooltip options
   */
  build() {
    return new D3TooltipOptions(this);
  }

  /**
   * Set the tooltip background color; 'none' for no color.
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
   * Set the tooltip border color.
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
   * Set the tooltip border width in px.
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
   * Set the tooltip border radius in px.
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
   * Set the tooltip CSS border style.
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
   * Set the X offset of the tooltip from the data point.
   * Default: 2
   * 
   * @param {Number} offset The X offset 
   */
  offsetX(offset) {
    Preconditions.checkArgumentNumber(offset); 
    this._offsetX = offset;
    return this;
  }

  /**
   * Set the Y offset of the tooltip from the data point.
   * Default: 10
   * 
   * @param {Number} offset The Y offset
   */
  offsetY(offset) {
    Preconditions.checkArgumentNumber(offset); 
    this._offsetY = offset;
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
