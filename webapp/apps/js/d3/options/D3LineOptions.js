
import NshmpError from '../../error/NshmpError.js';
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Options for customizing a line in a line plot.
 * 
 * Use D3LineOptions.builder() to get new instance of D3LineOptionsBuilder.
 * See D3LineOptions.builder()
 * See D3LineOptionsBuilder
 * 
 * @class D3LineOptions
 * @author Brandon Clayton
 */
export class D3LineOptions {

  /**
   * @private
   * Must use D3LineOptions.builder()
   *  
   * @param {D3LineOptionsBuilder} builder The builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3LineOptionsBuilder);

    /**
     * The line color.
     * The default color is set based on the current color scheme
     *    in D3LineData.colorScheme
     * @type {String}
     */
    this.color = builder._color;

    /**
     * The id of the line, should have no spaces.
     * @type {String}
     */
    this.id = builder._id;

    /**
     * The label of the line to show in the tooltip and legend
     * @type {String}
     */
    this.label = builder._label;

    /**
     * The line style: 
     *    - '-' || 'solid': Solid line
     *    - '--' || 'dashed': Dashed line
     *    - ':' || 'dotted': Dotted line
     *    - '-:' || 'dash-dot': Dahsed-dotted
     *    - 'none': No line
     * Default: 'solid'
     * @type {String}
     */
    this.lineStyle = builder._lineStyle;

    /**
     * The line width.
     * Default: 2.5
     * @type {Number}
     */
    this.lineWidth = builder._lineWidth;

    /**
     * The marker color.
     * The default color is set based on the current color scheme
     *    in D3LineData.colorScheme
     * @type {String}
     */
    this.markerColor = builder._markerColor;

    /**
     * The marker edge color.
     * The default color is set based on the current color scheme
     *    in D3LineData.colorScheme
     * @type {String}
     */
    this.markerEdgeColor = builder._markerEdgeColor;

    /**
     * The marker edge width.
     * Default: 1.0
     * @type {Number} 
     */
    this.markerEdgeWidth = builder._markerEdgeWidth;

    /**
     * The marker size.
     * Default: 6
     * @type {Number}
     */
    this.markerSize = builder._markerSize;

    /**
     * The marker style:
     *    - 's' || 'square': Square markers
     *    - 'o' || 'circle': Circle markers
     *    - '+' || 'plus-sign': Plus sign markers
     *    - 'x' || 'cross': Cross sign markers
     *    - '^' || 'up-triangle': Up-pointing triangle
     *    - 'v' || 'down-triangle': Down-pointing triangle
     *    - '<' || 'left-triangle': Left-pointing triangle
     *    - '>' || 'right-triangle': Right-pointing triangle
     *    - 'd' || 'diamond': Diamond markers
     *    - '*' || 'star': Star markers
     * Default: 'circle'
     * @type {String}
     */
    this.markerStyle = builder._markerStyle;

    /**
     * Whether the data is selectable in the plot.
     * Default: true
     * @type {Boolean}
     */
    this.selectable = builder._selectable;

    /**
     * The plot selection multiplier to be applied to the 
     *    line width, marker size, and marker edge size, when a line
     *    or marker is selected.
     * Default: 2.0
     * @type{Number}
     */
    this.selectionMultiplier = builder._selectionMultiplier;

    /**
     * Whether to show the data in the legend.
     * Default: true
     * @type {Boolean}
     */
    this.showInLegend = builder._showInLegend;

    /**
     * The SVG dash array based on the lineStyle
     * @type {String}
     */
    this.svgDashArray = this._getDashArray();

    /**
     * The D3 symbol associated with the marker style.
     * @type {Object}
     */
    this.d3Symbol = this._getD3Symbol();

    /**
     * The D3 symbol sizes are are, square pixels.
     * @type {Number}
     */
    this.d3SymbolSize = Math.pow(this.markerSize, 2);

    /**
     * The D3 symbol rotate.
     * @type {Number}
     */
    this.d3SymbolRotate = this._getD3SymbolRotate();

    /* Make immutable */
    Object.freeze(this);
  }
  
  /**
   * Create a new D3LineOptions with default options.
   * @returns {D3LineOptions} New D3LineOptions instance
   */
  static withDefaults() {
    return D3LineOptions.builder().build();
  }

  /**
   * Create line options for reference lines.
   */
  static withRefLineDefaults() {
    return D3LineOptions.builder().color('black').build();
  }

  /**
   * Returns a new D3LineOptionsBuilder
   * @returns {D3LineOptionsBuilder} New builder
   */
  static builder() {
    return new D3LineOptionsBuilder(); 
  }

  /**
   * @private 
   */
  _getDashArray() {
    let dashArray;

    switch(this.lineStyle) {
      case '-' || 'solid':
        dashArray = '';
        break;
      case '--' || 'dashed':
        dashArray = '8, 8';
        break;
      case ':' || 'dotted':
        dashArray = '2, 5';
        break;
      case '-.' || 'dash-dot':
        dashArray = '8, 5, 2, 5';
        break;
      case 'none':
        dashArray = '0, 1';
        break;
      default:
        NshmpError.throwError(`Line style [${this.lineStyle}] not supported`);
    }

    return dashArray;
  }

  /**
   * @private 
   */
  _getD3Symbol() {
    let symbol;

    switch(this.markerStyle) {
      case '+' || 'plus-sign':
      case 'x' || 'cross':
        symbol = d3.symbolCross;
        break;
      case 'd' || 'diamond':
        symbol = d3.symbolDiamond;
        break;
      case '*' || 'star':
        symbol = d3.symbolStar;
        break;
      case '^' || 'up-triangle': 
      case 'v' || 'down-triangle':
      case '<' || 'left-triangle': 
      case '>' || 'right-triangle':
        symbol = d3.symbolTriangle;
        break;
      case 'o' || 'circle':
        symbol = d3.symbolCircle;
        break;
      case 's' || 'square':
        symbol = d3.symbolSquare;
        break;
      case 'none':
        symbol = null;
        break;
      default:
        NshmpError.throwError(`Marker [${this.markerStyle}] not supported`);
    }

    Preconditions.checkNotUndefined(symbol, 'D3 symbol not found');

    return symbol;
  }

  /**
   * @private
   */
  _getD3SymbolRotate() {
    let rotate;

    switch(this.markerStyle) {
      case 'x' || 'cross':
        rotate = 45; 
        break;
      case 'v' || 'down-triangle':
        rotate = 180;
        break;
      case '<' || 'left-triangle':
        rotate = -90;
        break;
      case '>' || 'right-triangle':
        rotate = 90;
        break;
      default:
        rotate = 0;
    }

    return rotate;
  }

}

/**
 * @fileoverview Builder for D3LineOptions
 * 
 * Use D3LineOptions.builder() for new instance of D3LineOptionsBuilder
 * 
 * @class D3LineOptionsBuilder
 * @author Brandon Clayton
 */
export class D3LineOptionsBuilder {
  
  /** @private */
  constructor() {
    /** @type {String} */
    this._color = undefined;

    /** @type {String} */
    this._id = undefined;
    
    /** @type {String} */
    this._label = undefined;
    
    /** @type {String} */
    this._lineStyle = '-';
    
    /** @type {Number} */
    this._lineWidth = 2.5;
    
    /** @type {String} */
    this._markerStyle = 'o';
    
    /** @type {String} */
    this._markerColor = undefined;
    
    /** @type {String} */
    this._markerEdgeColor = undefined;
    
    /** @type {Number} */
    this._markerEdgeWidth = 1.0;
    
    /** @type {Number} */
    this._markerSize = 6.0;

    /** @type {Boolean} */
    this._selectable = true;
    
    /** @type {Number} */
    this._selectionMultiplier = 2;
    
    /** @type {Boolean} */
    this._showInLegend = true;
  }

  /**
   * Returns new D3LineOptions
   * 
   * @returns {D3LineOptions} new D3LineOptions
   */
  build() {
    return new D3LineOptions(this);
  }

  /**
   * Copy D3LineOptions into the builder.
   * 
   * @param {D3LineOptions} options The options to copy
   */
  fromCopy(options) {
    Preconditions.checkArgumentInstanceOf(options, D3LineOptions);
    
    this._color = options.color;
    this._id = options.id;
    this._label = options.label;
    this._lineStyle = options.lineStyle;
    this._lineWidth = options.lineWidth;
    this._markerColor = options.markerColor;
    this._markerEdgeColor = options.markerEdgeColor;
    this._markerEdgeWidth = options.markerEdgeWidth;
    this._markerStyle = options.markerStyle;
    this._markerSize = options.markerSize;
    this._selectable = options.selectable;
    this._selectionMultiplier = options.selectionMultiplier;
    this._showInLegend = options.showInLegend;

    return this;
  }

  /**
   * Set the line color.
   * The default color is set based on the current color scheme
   *    in D3LineData.colorScheme.
   * 
   * @param {String} color The line color 
   */
  color(color) {
    Preconditions.checkArgumentString(color);
    this._color = color;
    return this;
  }

  /**
   * Set the id of the line.
   * 
   * @param {String} id The id of the line 
   */
  id(id) {
    Preconditions.checkArgumentString(id);
    this._id = id;
    return this;
  }

  /**
   * Set the label for the line. Shown in tooltip and legend.
   * 
   * @param {String} label The label for the line 
   */
  label(label) {
    Preconditions.checkArgumentString(label);
    this._label = label;
    return this;
  }

  /**
   * Set the line style: 
   *    - '-' || 'solid': Solid line
   *    - '--' || 'dashed': Dashed line
   *    - ':' || 'dotted': Dotted line
   *    - '-:' || 'dash-dot': Dahsed-dotted
   *    - 'none': No line
   * Default: 'solid'
   * 
   * @param {String} style 
   */
  lineStyle(style) {
    Preconditions.checkArgumentString(style);
    this._lineStyle = style.toLowerCase();
    return this;
  }

  /**
   * Set the line width.
   * Default: 2.5
   * 
   * @param {Number} width The line width 
   */
  lineWidth(width) {
    Preconditions.checkArgumentNumber(width);
    this._lineWidth = width;
    return this;
  }

  /**
   * Set the marker color.
   * The default color is set based on the current color scheme
   *    in D3LineData.colorScheme
   * 
   * @param {String} color 
   */
  markerColor(color) {
    Preconditions.checkArgumentString(color);
    this._markerColor = color;
    return this;
  }

  /**
   * Set the marker edge color.
   * The default color is set based on the current color scheme
   *    in D3LineData.colorScheme
   * 
   * @param {String} color The marker edge color 
   */
  markerEdgeColor(color) {
    Preconditions.checkArgumentString(color);
    this._markerEdgeColor = color;
    return this;
  }

  /**
   * Set the marker edge width.
   * Default: 1.0
   * 
   * @param {Number} width The marker edge width 
   */
  markerEdgeWidth(width) {
    Preconditions.checkArgumentNumber(width);
    this._markerEdgeWidth = width;
    return this;
  }

  /**
   * The marker size.
   * Default: 6
   * @type {Number}
   */
  markerSize(size) {
    Preconditions.checkArgumentNumber(size);
    this._markerSize = size;
    return this;
  }

  /**
   * Set the marker style:
   *    - 's' || 'square': Square markers
   *    - 'o' || 'circle': Circle markers
   *    - '+' || 'plus-sign': Plus sign markers
   *    - 'x' || 'cross': Cross sign markers
   *    - '^' || 'up-triangle': Up-pointing triangle
   *    - 'v' || 'down-triangle': Down-pointing triangle
   *    - '<' || 'left-triangle': Left-pointing triangle
   *    - '>' || 'right-triangle': Right-pointing triangle
   *    - 'd' || 'diamond': Diamond markers
   *    - '*' || 'star': Star markers
   * Default: 'circle'
   * 
   * @param {String} marker 
   */
  markerStyle(marker) {
    Preconditions.checkArgumentString(marker);
    this._markerStyle = marker.toLowerCase();
    return this;
  }

  /**
   * Set whether the data can be selected in the plot.
   * 
   * @param {Boolean} selectable Whether data is selectable
   */
  selectable(selectable) {
    Preconditions.checkArgumentBoolean(selectable);
    this._selectable = selectable;
    return this;
  }

  /**
   * Set the plot selection multiplier to be applied to the 
   *    line width, marker size, and marker edge size, when a line
   *    or marker is selected.
   * Default: 2.0
   * 
   * @param {Number} mult The multiplier
   */
  selectionMultiplier(mult) {
    Preconditions.checkArgumentNumber(mult);
    this._selectionMultiplier = mult;
    return this;
  }

  /**
   * Whether to show the data in the legend.
   * Default: true
   * @type {Boolean}
   */
  showInLegend(bool) {
    Preconditions.checkArgumentBoolean(bool);
    this._showInLegend = bool;
    return this;
  }

}
