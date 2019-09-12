
import { D3SaveFigureOptions } from './D3SaveFigureOptions.js';
import { D3TooltipOptions } from './D3TooltipOptions.js';

import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Create options for D3BaseSubView.
 * 
 * Use D3BaseSubViewOptions.lowerBuilder or 
 *    D3BaseSubViewOptions.upperBuilder to customize options 
 *    for lower and upper sub view or use 
 *    D3BaseSubViewOptions.upperWithDefaults() or 
 *    D3BaseSubViewOptions.lowerWithDefaults() for default options.
 * 
 * Note: The only difference between upperWithDefaults() and 
 *    lowerWithDefault() is the plot height. The lower view defaults with
 *    224 px for plot height while the upper is 504 px.
 * 
 * @class D3BaseSubViewOptions
 * @author Brandon Clayton
 */
export class D3BaseSubViewOptions {

  /** 
   * @private
   * Must use D3BaseSubViewOptions.lowerBuilder() or
   *    D3BaseSubViewOptions.upperBuilder()
   * 
   * @param {D3BaseSubViewOptionsBuilder} builder The builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3BaseSubViewOptionsBuilder);

    /**
     * The filename for downloading
     * Default: 'file'
     * @type {String}
     */
    this.filename = builder._filename;

    /**
     * The label for the sub view
     * Default: 'upper' || 'lower'
     * @type {String}
     */
    this.label = builder._label;

    /** 
     * Margin bottom for the SVG plot in px.
     * Default: 15
     * @type {Number}
     */
    this.marginBottom = builder._marginBottom;
    
    /** 
     * Margin left for the SVG plot in px.
     * Default: 20
     * @type {Number}
     */
    this.marginLeft = builder._marginLeft;
    
    /** 
     * Margin right for the SVG plot in px.
     * Default: 10
     * @type {Number}
     */
    this.marginRight = builder._marginRight;

    /** 
     * Margin top for the SVG plot in px.
     * Default: 10
     * @type {Number}
     */
    this.marginTop = builder._marginTop;

    /** 
     * Padding bottom for the SVG plot in px.
     * Default: 35
     * @type {Number}
     */
    this.paddingBottom = builder._paddingBottom;
    
    /** 
     * Padding left for the SVG plot in px.
     * Default: 40
     * @type {Number}
     */
    this.paddingLeft = builder._paddingLeft;
    
    /** 
     * Padding right for the SVG plot in px.
     * Default: 20
     * @type {Number}
     */
    this.paddingRight = builder._paddingRight;

    /** 
     * Padding top for the SVG plot in px.
     * Default: 10
     * @type {Number}
     */
    this.paddingTop = builder._paddingTop;

    /**
     * SVG plot height for SVG view box in px.
     * Default: 504 (upper) || 224 (lower)
     * @type {Number}
     */
    this.plotHeight = builder._plotHeight;

    /**
     * SVG plot width for SVG view box in px.
     * Default: 896
     * @type {Number}
     */
    this.plotWidth = builder._plotWidth;

    /**
     * The sub view type: 'lower' || 'upper'
     * @type {String}
     */
    this.subViewType = builder._subViewType;

    /**
     * The save figure options.
     * Default: D3SaveFigureOptions.withDefaults()
     * @type {D3SaveFigureOptions}
     */
    this.saveFigureOptions = builder._saveFigureOptions;

    /**
     * The tooltip options.
     * Default: D3TooltipOptions.withDefaults()
     * @type {D3TooltipOptions}
     */
    this.tooltipOptions = builder._tooltipOptions;

    /* Make immutable */
    if (new.target == D3BaseSubViewOptions) Object.freeze(this);
  }

  /** 
   * Return new D3BaseSubViewOptions.Builder for lower sub view 
   * 
   * @returns {D3BaseSubViewOptionsBuilder} The lower base sub view 
   *    options builder
   */
  static lowerBuilder() {
    const LOWER_PLOT_HEIGHT = 224;
    return new D3BaseSubViewOptionsBuilder()
        ._type('lower')
        .plotHeight(LOWER_PLOT_HEIGHT);
  }

  /** 
   * Return new D3BaseSubViewOptions for lower sub view
   * 
   * @returns {D3BaseSubViewOptions} The lower base sub view options
   */
  static lowerWithDefaults() {
    return D3BaseSubViewOptions.lowerBuilder().build();
  }

  /** 
   * Return new D3BaseSubViewOptions.Builder for upper sub view 
   * 
   * @returns {D3BaseSubViewOptionsBuilder} The upper base sub view 
   *    options builder
   */
  static upperBuilder() {
    return new D3BaseSubViewOptionsBuilder()._type('upper');
  }

  /** 
   * Return new D3BaseSubViewOptions for upper sub view 
   * 
   * @returns {D3BaseSubViewOptions} The upper base sub view options
   */
  static upperWithDefaults() {
    return D3BaseSubViewOptions.upperBuilder().build();
  }

}

/**
 * @fileoverview Builder for D3BaseSubViewOptions
 * 
 * Use D3BaseSubViewOptions.lowerBuilder() or
 *    D3BaseSubViewOptions.upperBuilder() to get new instance of builder.
 * 
 * @class D3SubViewOptionsBuilder
 * @author Brandon Clayton
 */
export class D3BaseSubViewOptionsBuilder {

  /** @private */
  constructor() {
    /** @type {String} */
    this._filename = 'file';

    /** @type {String} */
    this._label = '';
    
    /** @type {Number} */
    this._marginBottom = 15;
    
    /** @type {Number} */
    this._marginLeft = 20;
    
    /** @type {Number} */
    this._marginRight = 10;
    
    /** @type {Number} */
    this._marginTop = 10;
    
    /** @type {Number} */
    this._paddingBottom = 35;
    
    /** @type {Number} */
    this._paddingLeft = 40;
    
    /** @type {Number} */
    this._paddingRight = 20;
    
    /** @type {Number} */
    this._paddingTop = 10;
    
    /** @type {Number} */
    this._plotHeight = 504;
    
    /** @type {Number} */
    this._plotWidth = 896;
    
    /** @type {D3SaveFigureOptions} */
    this._saveFigureOptions = D3SaveFigureOptions.withDefaults();
    
    /** @type {D3TooltipOptions} */
    this._tooltipOptions = D3TooltipOptions.withDefaults();

    /** @type {String} */
    this._subViewType = 'upper';
  }

  /**
   * Return new D3BaseSubViewOptions
   */
  build() {
    this._checkHeight();
    this._checkWidth();
    return new D3BaseSubViewOptions(this);
  }

  /**
   * Set the filename for downloading.
   * Default: 'file'
   * 
   * @param {String} name The filename
   */
  filename(name) {
    Preconditions.checkArgumentString(name);
    this._filename = name;
    return this;
  }

  /**
   * Set the label for the sub view.
   * Default: ''
   * 
   * @param {String} label The label
   */
  label(label) {
    Preconditions.checkArgumentString(label);
    this._label = label;
    return this;
  }

  /**
   * Set the bottom margin for the SVG plot in px.
   * Default: 15
   * 
   * @param {Number} margin The bottom margin 
   */
  marginBottom(margin) {
    Preconditions.checkArgumentInteger(margin);
    this._marginBottom = margin; 
    return this;
  }

  /**
   * Set the left margin for the SVG plot in px.
   * Default: 20
   * 
   * @param {Number} margin The left margin 
   */
  marginLeft(margin) {
    Preconditions.checkArgumentInteger(margin);
    this._marginLeft = margin;
    return this;
  }

  /**
   * Set the right margin for the SVG plot in px.
   * Default: 10
   * 
   * @param {Number} margin The right margin 
   */
  marginRight(margin) {
    Preconditions.checkArgumentInteger(margin);
    this._marginRight = margin;
    return this;
  }

  /**
   * Set the top margin for the SVG plot in px.
   * Default: 10
   * 
   * @param {Number} margin The top margin 
   */
  marginTop(margin) {
    Preconditions.checkArgumentInteger(margin);
    this._marginTop = margin;
    return this;
  }

  /**
   * Set the bottom padding for the SVG plot in px.
   * Default: 35
   * 
   * @param {Number} margin The bottom margin 
   */
  paddingBottom(padding) {
    Preconditions.checkArgumentInteger(padding);
    this._paddingBottom = padding; 
    return this;
  }

  /**
   * Set the left padding for the SVG plot in px.
   * Default: 40
   * 
   * @param {Number} margin The left margin 
   */
  paddingLeft(padding) {
    Preconditions.checkArgumentInteger(padding);
    this._paddingLeft = padding; 
    return this;
  }

  /**
   * Set the right padding for the SVG plot in px.
   * Default: 20
   * 
   * @param {Number} margin The right margin 
   */
  paddingRight(padding) {
    Preconditions.checkArgumentInteger(padding);
    this._paddingRight = padding; 
    return this;
  }

  /**
   * Set the top padding for the SVG plot in px.
   * Default: 10
   * 
   * @param {Number} margin The top margin 
   */
  paddingTop(padding) {
    Preconditions.checkArgumentInteger(padding);
    this._paddingTop = padding; 
    return this;
  }

  /**
   * Set the SVG plot height in px.
   * Default: 504 (upper) || 224 (lower)
   * 
   * @param {number} height The plot height
   */
  plotHeight(height) {
    Preconditions.checkArgumentInteger(height);
    this._plotHeight = height;
    return this;
  }

  /**
   * Set the SVG plot width in px.
   * Default: 896
   * 
   * @param {number} width The plot width
   */
  plotWidth(width) {
    Preconditions.checkArgumentInteger(width);
    this._plotWidth = width;
    return this;
  }

  /**
   * Set the save figure options.
   * Default: D3SaveFigureOptions.withDefaults()
   * 
   * @param {D3SaveFigureOptions} options The save figure options
   */
  saveFigureOptions(options) {
    Preconditions.checkArgumentInstanceOf(options, D3SaveFigureOptions);
    this._saveFigureOptions = options;
    return this;
  }

  /**
   * Set the tooltip options.
   * Default: D3TooltipOptions.withDefaults()
   *  
   * @param {D3TooltipOptions} options The tooltip options
   */
  tooltipOptions(options) {
    Preconditions.checkArgumentInstanceOf(options, D3TooltipOptions);
    this._tooltipOptions = options;
    return this;
  }

  /**
   * Check if plot height is good.
   */
  _checkHeight() {
    let heightCheck = this._plotHeight - 
        this._marginBottom - this._marginTop;

    Preconditions.checkState(
      heightCheck > 0,
      'Height must be greater than (marginTop + marginBottom)');
  }

  /**
   * Check if plot width is good
   */
  _checkWidth() {
    let widthCheck = this._plotWidth - 
        this._marginLeft - this._marginRight;

    Preconditions.checkState(
      widthCheck > 0,
      'Width must be greater than (marginLeft + marginRight)');
  }

  /**
   * @param {String} type 
   */
  _type(type) {
    type = type.toLowerCase();
    Preconditions.checkArgument(
        type == 'lower' || type == 'upper',
        `Sub view type [${type}] not supported`);

    this._subViewType = type;
    return this; 
  }

}
