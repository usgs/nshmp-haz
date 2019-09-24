
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Text options for plot when adding text to a plot.
 * 
 * Use D3TextOptions.builder() to customize options.
 * 
 * @class D3TextOptions
 * @author Brandon Clayton
 */
export class D3TextOptions {
 
  /**
   * @private
   * 
   * @param {D3TextOptionsBuilder} builder The builder 
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3TextOptionsBuilder);

    /**
     * The alignment baseline. See CSS property alignment-baseline
     * Default: 'baseline'
     * @type {String}
     */
    this.alignmentBaseline = builder._alignmentBaseline;

    /**
     * The text color.
     * Default: 'black'
     * @type {String}
     */
    this.color = builder._color; 
    
    /**
     * The horizontal shift in the text from the X position.
     * Default: 0
     * @type {Number}
     */
    this.dx = builder._dx;
    
    /**
     * The vertical shift in the text from the Y position.
     * Default: 0
     * @type {Number}
     */
    this.dy = builder._dy;
    
    /**
     * The text font size in pixels.
     * Default: 14
     * @type {Number}
     */
    this.fontSize = builder._fontSize;
    
    /**
     * The font weight.
     * Default: 400
     * @type {Number}
     */
    this.fontWeight = builder._fontWeight;
    
    /**
     * The text rotation.
     * Default: 0
     * @type {Number}
     */
    this.rotate = builder._rotate;
    
    /**
     * The stroke color.
     * Default: 'black'
     * @type {String}
     */
    this.stroke = builder._stroke;
    
    /**
     * The stroke width.
     * Default: 0
     * @type {Number}
     */
    this.strokeWidth = builder._strokeWidth;

    /**
     * The text anchor. See CSS property text-anchor.
     * Default: 'start'
     * @type {String}
     */
    this.textAnchor = builder._textAnchor;

    /* Make immutable */
    Object.freeze(this);
  }

  /**
   * Return new builder.
   */
  static builder() {
    return new D3TextOptionsBuilder();
  }

  /**
   * New instance of D3TextOptions with default options.
   */
  static withDefaults() {
    return D3TextOptions.builder().build();
  }

}

/**
 * @fileoverview Builder for D3TextOptions
 * 
 * @class D3TextOptionsBuilder
 * @author Brandon Clayton
 */
export class D3TextOptionsBuilder {

  /**
   * @private
   */
  constructor() {
    /** @type {String} */
    this._alignmentBaseline = 'baseline';
    
    /** @type {String} */
    this._color = 'black';
    
    /** @type {Number} */
    this._dx = 0;
    
    /** @type {Number} */
    this._dy = 0;
    
    /** @type {Number} */
    this._fontSize = 14;

    /** @type {Number} */
    this._fontWeight = 400;
    
    /** @type {Number} */
    this._rotate = 0;
    
    /** @type {String} */
    this._stroke = 'black';
    
    /** @type {Number} */
    this._strokeWidth = 0;
    
    /** @type {String} */
    this._textAnchor = 'start';
  }

  /**
   * Return new instance of D3TextOptions
   */
  build() {
    return new D3TextOptions(this);
  }

  /**
   * Set the alignment baseline.
   * Default: 'baseline'
   * 
   * @param {String} alignment The baseline
   */
  alignmentBaseline(alignment) {
    Preconditions.checkArgumentString(alignment);
    this._alignmentBaseline = alignment;
    return this;
  }

  /**
   * Set the text color.
   * Default: 'black'
   * 
   * @param {String} color The text color.
   */
  color(color) {
    Preconditions.checkArgumentString(color);
    this._color = color;
    return this;
  }

  /**
   * Set the shift in X.
   * Default: 0
   * 
   * @param {Number} dx The horizontal shift
   */
  dx(dx) {
    Preconditions.checkArgumentNumber(dx);
    this._dx = dx;
    return this;
  }

  /**
   * Set the shift in Y.
   * Default: 0
   * 
   * @param {Number} dy The vertical shift
   */
  dy(dy) {
    Preconditions.checkArgumentNumber(dy);
    this._dy = dy;
    return this;
  }

  /**
   * Set the text font size.
   * Default: 14
   * 
   * @param {Number} fontSize The font size
   */
  fontSize(fontSize) {
    Preconditions.checkArgumentNumber(fontSize);
    this._fontSize = fontSize;
    return this;
  }

  /**
   * Set the font weight.
   * Default: 400
   * 
   * @param {Number} fontWeight The weight
   */
  fontWeight(fontWeight) {
    Preconditions.checkArgumentInteger(fontWeight);
    this._fontWeight = fontWeight;
    return this;
  }

/**
 * Set the text rotation.
 * Default: 0
 * 
 * @param {Number} rotate The rotation
 */
  rotate(rotate) {
    Preconditions.checkArgumentNumber(rotate);
    this._rotate = rotate;
    return this;
  }

  /**
   * Set the stroke color.
   * Default: 'black;
   * 
   * @param {String} stroke The stroke
   */
  stroke(stroke) {
    Preconditions.checkArgumentString(stroke);
    this._stroke = stroke;
    return this;
  }

  /**
   * Set the stroke width.
   * Default: 0
   * 
   * @param {Number} strokeWidth The width
   */
  strokeWidth(strokeWidth) {
    Preconditions.checkArgumentNumber(strokeWidth);
    this._strokeWidth = strokeWidth;
    return this;
  }

  /**
   * Set the text anchor.
   * Default: 'start'
   * 
   * @param {String} textAnchor The text anchor 
   */
  textAnchor(textAnchor) {
    Preconditions.checkArgumentString(textAnchor);
    this._textAnchor = textAnchor;
    return this;
  }

}
