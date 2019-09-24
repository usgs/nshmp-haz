
import { D3BaseSubViewOptions } from './D3BaseSubViewOptions.js';
import { D3BaseSubViewOptionsBuilder } from './D3BaseSubViewOptions.js';
import { D3LineLegendOptions } from './D3LineLegendOptions.js';

import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Create options for D3LineSubView.
 * 
 * Use D3LineSubViewOptions.lowerBuilder() or
 *    D3LineSubViewOptions.upperBuilder() to customize options 
 *    for lower and upper sub view or use 
 *    D3LineSubViewOptions.upperWithDefaults() or 
 *    D3LineSubViewOptions.lowerWithDefaults() for default options.
 * 
 * Note: The only difference between upperWithDefaults() and 
 *    lowerWithDefault() is the plot height. The lower view defaults with
 *    224 px for plot height while the upper is 504 px.
 * 
 * @class D3LineSubViewOptions
 * @extends D3BaseSubViewOptions
 * @author Brandon Clayton
 */
export class D3LineSubViewOptions extends D3BaseSubViewOptions {

  /** 
   * @private
   * Must use D3LineSubViewOptions.builder()
   * 
   * @param {D3LineSubViewOptionsBuilder} builder The builder 
   */
  constructor(builder) {
    super(builder);

    /**
     * The font weight for the X and Y axis labels.
     * Default: 500
     * @type {Number}
     */
    this.axisLabelFontWeight = builder._axisLabelFontWeight;

    /**
     * The default X limit when the D3LineView is shown with no data. 
     * Default: [ 0.01, 10 ] 
     * @type {Array<Number>} 
     */
    this.defaultXLimit = builder._defaultXLimit;
    
    /**
     * The default X limit when the D3LineView is shown with no data. 
     * Default: [ 0.01, 1 ] 
     * @type {Array<Number>} 
     */
    this.defaultYLimit = builder._defaultYLimit;
   
    /**
     * Snap lines that are being dragged to the nearest specified value.
     * Default: 0.01
     * @type {Number}
     */
    this.dragLineSnapTo = builder._dragLineSnapTo;

    /** 
     * Color of axes grid lines.
     * Default: '#E0E0E0' 
     * @type {String}
     */
    this.gridLineColor = builder._gridLineColor; 

    /**
     * Width of axes grid lines.
     * Default: 0.75 
     * @type {Number}
     */
    this.gridLineWidth = builder._gridLineWidth;
    
    /** 
     * Axes label font size in px. 
     * Default: 16
     * @type {Number}
     */
    this.labelFontSize = builder._labelFontSize;
    
    /**
     * The legend options.
     * Default: D3LineLegendOptions.withDefaults()
     * @type {D3LineLegendOptions}
     */
    this.legendOptions = builder._legendOptions;

    /**
     * A label to represent what the data represents.
     * Default: 'Line'
     * @type {String}
     */
    this.lineLabel = builder._lineLabel;

    /**
     * Color of a reference line.
     * Default: 'gray'
     * @type {String}
     */
    this.referenceLineColor = builder._referenceLineColor;

    /**
     * Line width of the reference line.
     * Default: 1.5
     * @type {Number}
     */
    this.referenceLineWidth = builder._referenceLineWidth;

    /**
     * Whether to show the legend regardless of using the legend toggle.
     * Default: true
     * @type {Boolean}
     */
    this.showLegend = builder._showLegend;

    /**
     * The font size of the exponents on the tick mark values in px. 
     * Only when in log space.
     * Default: 8
     * @type {Number}
     */
    this.tickExponentFontSize = builder._tickExponentFontSize;

    /**
     * The tick mark values font size in px.
     * Default: 12
     * @type {Number}
     */
    this.tickFontSize = builder._tickFontSize;

    /**
     * The duration for any plot animations in milliseconds.
     * e.g. switching between log and linear scales.
     * Default: 500
     * @type {Number}
     */
    this.translationDuration = builder._translationDuration;

    /**
     * The X axis location: 'top' || 'bottom'
     * Default: 'bottom'
     * @type {String}
     */
    this.xAxisLocation = builder._xAxisLocation;

    /**
     * Extend the domain to start and end on nice round numbers.
     * Default: true
     * @type {Boolean}
     */
    this.xAxisNice = builder._xAxisNice;

    /**
     * The X axis scale: 'log' || 'linear'
     * Default: 'linear'
     * @type {String}
     */
    this.xAxisScale = builder._xAxisScale;

    /**
     * The number of digits after decimal place when 
     *    xValueToExponent is set to true.
     * Default: 4
     * @type {Number} digits The number of digits after decimal point
     */
    this.xExponentFractionDigits = builder._xExponentFractionDigits;

    /**
     * The X axis label; can be an HTML string.
     * Default: 'X'
     * @type {String}
     */
    this.xLabel = builder._xLabel;

    /**
     * Padding around the X label in px.
     * Default: 8
     * @type {Number}
     */
    this.xLabelPadding = builder._xLabelPadding;

    /**
     * The number of tick marks for the X axis.
     * The specified count is only a hint; the scale may return more or 
     *    fewer values depending on the domain.
     * Default: 8
     * @type {Number}
     */
    this.xTickMarks = builder._xTickMarks;

    /**
     * Whether to format the X value in exponential form when X value
     *    is shown on tooltip, in data view, and when saving data.
     * Default: false
     * @type{Boolean} 
     */
    this.xValueToExponent = builder._xValueToExponent;
    
    /**
     * The Y axis location: 'left' || 'right'
     * Default: 'left'
     * @type {String}
     */
    this.yAxisLocation = builder._yAxisLocation;

    /**
     * Extend the domain to start and end on nice round numbers.
     * Default: true
     * @type {Boolean}
     */
    this.yAxisNice = builder._yAxisNice;

    /**
     * Whether to reverse the Y axis direction.
     * Default: false
     * @type {Boolean}
     */
    this.yAxisReverse = builder._yAxisReverse;

    /**
     * The Y axis scale: 'log' || 'linear'
     * Default: 'linear'
     * @type {String}
     */
    this.yAxisScale = builder._yAxisScale;

    /**
     * The number of digits after decimal place when 
     *    yValueToExponent is set to true.
     * Default: 4
     * @type {Number} digits The number of digits after decimal point
     */
    this.yExponentFractionDigits = builder._xExponentFractionDigits;

    /**
     * The Y axis label; can be an HTML string.
     * Default: 'Y'
     * @type {String}
     */
    this.yLabel = builder._yLabel;
    
    /**
     * Padding around the Y label in px.
     * Default: 10
     * @type {Number}
     */
    this.yLabelPadding = builder._yLabelPadding;

    /**
     * The number of tick marks for the Y axis.
     * The specified count is only a hint; the scale may return more or 
     *    fewer values depending on the domain.
     * Default: 6
     * @type {Number}
     */
    this.yTickMarks = builder._yTickMarks;

    /**
     * Whether to format the Y value in exponential form when Y value
     *    is shown on tooltip, in data view, and when saving data.
     * Default: false
     * @type{Boolean} 
     */
    this.yValueToExponent = builder._yValueToExponent;

    /* Make immutable */
    if (new.target == D3LineSubViewOptions) Object.freeze(this);
  }

  /** 
   * Return new D3LineSubViewOptionsBuilder for lower sub view 
   */
  static lowerBuilder() {
    const LOWER_PLOT_HEIGHT = 224;
    return new D3LineSubViewOptionsBuilder()
        ._type('lower')
        .plotHeight(LOWER_PLOT_HEIGHT)
        .legendOptions(D3LineLegendOptions.lowerWithDefaults());
  }

  /** 
   * Return new D3LineSubViewOptions for lower sub view 
   */
  static lowerWithDefaults() {
    return D3LineSubViewOptions.lowerBuilder().build();
  }

  /** 
   * Return new D3LineSubViewOptionsBuilder for upper sub view 
   */
  static upperBuilder() {
    return new D3LineSubViewOptionsBuilder()
        ._type('upper');
  }

  /** 
   * Return new D3LineSubViewOptions for upper sub view 
   */
  static upperWithDefaults() {
    return D3LineSubViewOptions.upperBuilder().build();
  }

}

/**
 * @fileoverview Builder for D3LineSubViewOptions.
 * 
 * Use D3LineSubViewOptions.lowerBuilder() or
 *    D3LineSubViewOptions.upperBuilder() for new instance of builder.
 * 
 * @class D3LineSubViewOptionsBuilder
 * @extends D3BaseSubViewOptionsBuilder
 * @author Brandon Clayton
 */
export class D3LineSubViewOptionsBuilder 
    extends D3BaseSubViewOptionsBuilder {

  /** @private */
  constructor() {
    super();

    /** @type {Number} */
    this._axisLabelFontWeight = 500;
    
    /** @type {Array<Number>} */
    this._defaultXLimit = [ 0.01, 10 ];
    
    /** @type {Array<Number>} */
    this._defaultYLimit = [ 0.01, 1 ];

    /** @type {Number} */
    this._dragLineSnapTo = 0.01;
    
    /** @type {String} */
    this._gridLineColor = '#E0E0E0';
    
    /** @type {Number} */
    this._gridLineWidth = 0.75;
    
    /** @type {Number} */
    this._labelFontSize = 16;
    
    /** @type {D3LineLegendOptions} */
    this._legendOptions = D3LineLegendOptions.upperWithDefaults();

    /** @type {String} */
    this._lineLabel = 'Line';
    
    /** @type {String} */
    this._referenceLineColor = 'gray';
    
    /** @type {Number} */
    this._referenceLineWidth = 1.5;
    
    /** @type {Boolean} */
    this._showLegend = true;
    
    /** @type {Number} */
    this._tickExponentFontSize = 8;
    
    /** @type {Number} */
    this._tickFontSize = 12
    
    /** @type {Number} */
    this._translationDuration = 500;
   
    /** @type {String} */
    this._xAxisLocation = 'bottom';
    
    /** @type {Boolean} */
    this._xAxisNice = true;
    
    /** @type {String} */
    this._xAxisScale = 'linear';

    /** @type {Number} */
    this._xExponentFractionDigits = 4;
    
    /** @type {String} */
    this._xLabel = 'X';
    
    /** @type {Number} */
    this._xLabelPadding = 8;
    
    /** @type {Number} */
    this._xTickMarks = 8;
    
    /** @type {Boolean} */
    this._xValueToExponent = false;
    
    /** @type {String} */
    this._yAxisLocation = 'left';
    
    /** @type {Boolean} */
    this._yAxisNice = true;
    
    /** @type {Boolean} */
    this._yAxisReverse = false;

    /** @type {String} */
    this._yAxisScale = 'linear';
    
    /** @type {Number} */
    this._yExponentFractionDigits = 4;

    /** @type {String} */
    this._yLabel = 'Y';
    
    /** @type {Number} */
    this._yLabelPadding = 10;
    
    /** @type {Number} */
    this._yTickMarks = 6;

    /** @type {Boolean} */
    this._yValueToExponent = false;
  }

  /**
   * Return new D3LineSubViewOptions
   * @returns {D3LineSubViewOptions} Sub view options
   */
  build() {
    this._checkHeight();
    this._checkWidth();
    return new D3LineSubViewOptions(this);
  }

  /**
   * Set the font weight for the X and Y axis labels.
   * Default: 500
   * @param {Number} weight The font weight 
   */
  axisLabelFontWeight(weight) {
    Preconditions.checkArgumentInteger(weight);
    this._axisLabelFontWeight = weight;
    return this;
  }

  /**
   * Set the default X limit when the D3LineView is shown with no data. 
   * Default: [ 0.01, 10 ] 
   * @param {Array<Number>} xLimit The [ min, max] for the X axis
   */
  defaultXLimit(xLimit) {
    Preconditions.checkArgumentArrayLength(xLimit, 2);
    Preconditions.checkArgumentArrayOf(xLimit, 'number');
    this._defaultXLimit = xLimit;
    return this;
  }

  /**
   * Set the default Y limit when the D3LineView is shown with no data. 
   * Default: [ 0.01, 1 ] 
   * @param {Array<Number>} yLimit The [ min, max ] for the Y axis
   */
  defaultYLimit(yLimit) {
    Preconditions.checkArgumentArrayLength(yLimit, 2);
    Preconditions.checkArgumentArrayOf(yLimit, 'number');
    this._defaultYLimit = yLimit;
    return this;
  }

  /**
   * Snap a line to the nearest value when dragging.
   * Default: 0.01
   * 
   * @param {Number} snapTo Snap to value
   */
  dragLineSnapTo(snapTo) {
    Preconditions.checkArgumentNumber(snapTo);
    this._dragLineSnapTo = snapTo;
    return this;
  }

  /**
   * Set the grid line color in HEX, rgb, or string name.
   * Default: 'E0E0E0'
   * @param {String} color The grid line color
   */
  gridLineColor(color) {
    Preconditions.checkArgumentString(color);
    this._gridLineColor = color;
    return this;
  }

  /**
   * Set the grid line width.
   * Default: 0.75
   * @param {Number} width The grid line width
   */
  gridLineWidth(width) {
    Preconditions.checkArgumentNumber(width);
    this._gridLineWidth = width;
    return this;
  }

  /**
   * Set the legend options.
   * Default: D3LineLegendOptions.withDefaults()
   * 
   * @param {D3LineLegendOptions} options The legend options 
   */
  legendOptions(options) {
    Preconditions.checkArgumentInstanceOf(options, D3LineLegendOptions);
    this._legendOptions = options;
    return this;
  }

  /**
   * A label representing what the line data is. 
   * Default: ''
   *  
   * @param {String} label The line label 
   */
  lineLabel(label) {
    Preconditions.checkArgumentString(label);
    this._lineLabel = label;
    return this;
  }

  /**
   * Set the reference line color in HEX, RGB, or string name.
   * Default: '#9E9E9E'
   * @param {String} color The color
   */
  referenceLineColor(color) {
    Preconditions.checkArgumentString(color);
    this._referenceLineColor = color;
    return this;
  }
  
  /**
   * Set the reference line width. 
   * Default: 1.5
   * @param {Number} width The width
   */
  referenceLineWidth(width) {
    Preconditions.checkArgumentNumber(width);
    this._referenceLineWidth = width;
    return this;
  }
 
  /**
   * Whether to show the legend regardless of using the legend toggle.
   * Default: true
   * 
   * @param {Boolean} show the legend 
   */
  showLegend(show) {
    Preconditions.checkArgumentBoolean(show);
    this._showLegend = show;
    return this;
  }

  /**
   * Set the font size of the exponents on the axes tick marks.
   * Only in log scale.
   * Default: 6
   * @param {Number} size The font size
   */
  tickExponentFontSize(size) { 
    Preconditions.checkArgumentInteger(size);
    this._tickExponentFontSize = size;
    return this; 
  } 
  
  /**
   * Set the axes tick mark font size.
   * Default: 12  
   * @param {Number} size 
   */
  tickFontSize(size) {
    Preconditions.checkArgumentInteger(size);
    this._tickFontSize = size;
    return this; 
  }

  /**
   * Set the transition duration in milliseconds. Used when switching 
   *    between log and linear scale.
   * Default: 500 
   * @param {Number} time The duration
   */
  translationDuration(time) { 
    Preconditions.checkArgumentInteger(time);
    this._translationDuration = time;
    return this; 
  } 
  
  /**
   * Set the X axis location: 'top' || 'bottom'
   * Default: 'bottom' 
   * @param {String} loc The location
   */
  xAxisLocation(loc) { 
    loc = loc.toLowerCase();
    Preconditions.checkArgument(
        loc == 'bottom' || loc == 'top',
        `X axis location [${loc}] not supported`);
    
    this._xAxisLocation = loc;
    return this; 
  } 
  
  /**
   * Whether to extend the X domain to nice round numbers.
   * Default: true 
   * @param {Boolean} bool Whether to have a nice domain
   */
  xAxisNice(bool) { 
    Preconditions.checkArgumentBoolean(bool);
    this._xAxisNice = bool;
    return this; 
  } 
  
  /**
   * Set the X axis scale: 'log' || 'linear'
   * Default: 'linear' 
   * @param {String} scale The X axis scale
   */
  xAxisScale(scale) { 
    scale = scale.toLowerCase();
    Preconditions.checkArgument(
        scale == 'log' || scale == 'linear',
        `X axis scale [${scale}] not supported`);

    this._xAxisScale = scale;
    return this; 
  } 

  /**
   * Set the number of digits after decimal place when 
   *    xValueToExponent is set to true.
   * Default: 4
   * 
   * @param {Number} digits The number of digits after decimal point
   */
  xExponentFractionDigits(digits) {
    Preconditions.checkArgumentInteger(digits);
    this._xExponentFractionDigits = digits;
    return this;
  }
  
  /**
   * Set the X axis label; can be an HTML string.
   * Default: ''
   * @param {String} label The X axis label 
   */
  xLabel(label) {
    Preconditions.checkArgumentString(label);
    this._xLabel = label;
    return this;
  }
  
  /**
   * Set the X label padding in px.
   * Default: 8
   * @param {Number} pad The padding
   */
  xLabelPadding(pad) { 
    Preconditions.checkArgumentInteger(pad);
    this._xLabelPadding = pad;
    return this; 
  } 
  
  /**
   * Set the number of X axis tick marks.
   * The specified count is only a hint; the scale may return more or 
   *    fewer values depending on the domain.
   *  Default: 8
   * @param {Number} count Number of tick marks
   */
  xTickMarks(count) { 
    Preconditions.checkArgumentInteger(count);
    this._xTickMarks = count;
    return this; 
  } 
  
  /**
   * Whether to format the X value in exponential form when X value
   *    is shown on tooltip, in data view, and when saving data.
   * Default: false
   * 
   * @param {Boolean} toExponenet Whether to format in exponential form
   */
  xValueToExponent(toExponenet) {
    Preconditions.checkArgumentBoolean(toExponenet);
    this._xValueToExponent = toExponenet;
    return this;
  }
  
  /**
   * Set the Y axis location: 'left' || 'right'
   * Default: 'left' 
   * @param {String} loc The location
   */
  yAxisLocation(loc) { 
    loc = loc.toLowerCase();
    Preconditions.checkArgument(
        loc == 'left' || loc == 'right',
        `Y axis location [${loc}] not supported`);
    
    this._yAxisLocation = loc;
    return this; 
  } 
  
  /**
   * Whether to extend the Y domain to nice round numbers.
   * Default: true 
   * @param {Boolean} bool Whether to have a nice domain
   */
  yAxisNice(bool) { 
    Preconditions.checkArgumentBoolean(bool);
    this._yAxisNice = bool;
    return this; 
  } 
  
  /**
   * Whether to reverse the Y axis direction.
   * Default: false
   * 
   * @param {Boolean} bool To reverse Y axis
   */
  yAxisReverse(bool) {
    Preconditions.checkArgumentBoolean(bool);
    this._yAxisReverse = bool;
    return this;
  }

  /**
   * Set the Y axis scale: 'log' || 'linear'
   * Default: 'linear' 
   * @param {String} scale The Y axis scale
   */
  yAxisScale(scale) { 
    scale = scale.toLowerCase();
    Preconditions.checkArgument(
        scale == 'log' || scale == 'linear',
        `Y axis scale [${scale}] not supported`);

    this._yAxisScale = scale;
    return this; 
  } 
  
  /**
   * Set the number of digits after decimal place when 
   *    yValueToExponent is set to true.
   * Default: 4
   * 
   * @param {Number} digits The number of digits after decimal point
   */
  yExponentFractionDigits(digits) {
    Preconditions.checkArgumentInteger(digits);
    this._yExponentFractionDigits = digits;
    return this;
  }
  
  /**
   * Set the Y axis label; can be an HTML string.
   * Default: ''
   * @param {String} label The Y axis label 
   */
  yLabel(label) {
    Preconditions.checkArgumentString(label);
    this._yLabel = label;
    return this;
  }
  
  /**
   * Set the Y label padding in px.
   * Default: 10
   * @param {Number} pad The padding
   */
  yLabelPadding(pad) { 
    Preconditions.checkArgumentInteger(pad);
    this._yLabelPadding = pad;
    return this; 
  } 
  
  /**
   * Set the number of Y axis tick marks.
   * The specified count is only a hint; the scale may return more or 
   *    fewer values depending on the domain.
   * Default: 6
   * @param {Number} count Number of tick marks
   */
  yTickMarks(count) { 
    Preconditions.checkArgumentInteger(count);
    this._yTickMarks = count;
    return this; 
  }

  /**
   * Whether to format the Y value in exponential form when Y value
   *    is shown on tooltip, in data view, and when saving data.
   * Default: false
   * 
   * @param {Boolean} toExponenet Whether to format in exponential form
   */
  yValueToExponent(toExponenet) {
    Preconditions.checkArgumentBoolean(toExponenet);
    this._yValueToExponent = toExponenet;
    return this;
  }

}
