
import { Preconditions } from '../../error/Preconditions.js';

/**
 * @fileoverview Create options for D3BaseView. 
 * 
 * Use D3BaseViewOptions.builder() to customize the options or use 
 *    D3BaseViewOptions.withDefault() for default options.
 * 
 * @class D3BaseViewOptions
 * @author Brandon Clayton
 */
export class D3BaseViewOptions {

  /**
   * @private 
   * Must use D3BaseViewOptions.builder() 
   * 
   * @param {D3BaseViewOptionsBuilder} builder The builder
   */
  constructor(builder) {
    Preconditions.checkArgumentInstanceOf(builder, D3BaseViewOptionsBuilder);

    /**
     * The title font size in the view's header in px.
     * Default: 18
     * @type {Number}
     */
    this.titleFontSize = builder._titleFontSize;

    /** 
     * The D3BaseView view size to start with, either:
     *    'min' || 'minCenter' || 'max'
     * 
     * Default value: 'max'
     * @type {String}
     */
    this.viewSizeDefault = builder._viewSizeDefault;

    /**
     * The Bootstrap column size when viewSizeDefault is 'max'
     * @type {String}
     */
    this.viewSizeMax = builder._viewSizeMax;

    /**
     * The Bootstrap column size when viewSizeDefault is 'min'
     * @type {String}
     */
    this.viewSizeMin = builder._viewSizeMin;
    
    /**
     * The Bootstrap column size when viewSizeDefault is 'minCenter'
     * @type {String}
     */
    this.viewSizeMinCenter = builder._viewSizeMinCenter;

    /* Make immutable */
    if (new.target == D3BaseViewOptions) Object.freeze(this);
  }

  /** 
   * Return a new D3BaseViewOptions instance with default options 
   */
  static withDefaults() {
    return D3BaseViewOptions.builder().build();
  }

  /** 
   * Return a new D3BaseViewOptions.Builder 
   */
  static builder() {
    return new D3BaseViewOptionsBuilder(); 
  }

}

/**
 * @fileoverview Builder for D3BaseViewOptions
 * 
 * Use D3BaseViewOptions.builder() for new instance of builder.
 * 
 * @class D3BaseViewOptionsBuilder
 * @author Brandon Clayton
 */
export class D3BaseViewOptionsBuilder {
  
  /** @private */
  constructor() {
    this._titleFontSize = 18;

    this._viewSizeMin =  'col-sm-12 col-md-6';

    this._viewSizeMinCenter = 'col-sm-offset-1 col-sm-10 ' + 
        'col-xl-offset-2 col-xl-8 col-xxl-offset-3 col-xxl-6';

    this._viewSizeMax = 'col-sm-12 col-xl-offset-1 col-xl-10 ' +
        'col-xxl-offset-2 col-xxl-8';

    this._viewSizeDefault = 'max';
  }

  /** 
   * Return new D3BaseViewOptions instance 
   */
  build() {
    return new D3BaseViewOptions(this);
  }

  /**
   * Set the title font size in px.
   * Default: 18
   * 
   * @param {Number} fontSize The title font size
   */
  titleFontSize(fontSize) {
    Preconditions.checkArgumentInteger(fontSize);
    this._titleFontSize = fontSize;
    return this;
  }

  /**
   * Set the D3BaseView view size
   * 
   * @param {String} size The view size, either: 
   *    'min' || 'minCenter' || 'max' 
   */
  viewSize(size) {
    Preconditions.checkArgument(
        size == 'min' || size == 'minCenter' || size == 'max',
        `View size [${size}] not supported`);

    this._viewSizeDefault = size;
    return this;
  }

  /**
   * Set the Bootstrap column size when viewSize is'min'
   *  
   * @param {String} size The Bootstrap column size with 
   *    viewSize is 'min'
   */
  viewSizeMin(size) {
    Preconditions.checkArgumentString(size);
    this._viewSizeMin = size;
    return this;
  }

  /**
   * Set the Bootstrap column size when viewSize is'minCenter'
   *  
   * @param {String} size The Bootstrap column size with 
   *    viewSize is 'minCenter'
   */
  viewSizeMinCenter(size) {
    Preconditions.checkArgumentString(size);
    this._viewSizeMinCenter = size;
    return this;
  }

  /**
   * Set the Bootstrap column size when viewSize is'max'
   *  
   * @param {String} size The Bootstrap column size with 
   *    viewSize is 'max'
   */
  viewSizeMax(size) {
    Preconditions.checkArgumentString(size);
    this._viewSizeMax = size;
    return this;
  }

}
