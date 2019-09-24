
import NshmpError from './NshmpError.js';

/**
 * @fileoverview Static convenience methods to check wether a method or
 *    constructor was invoked correctly.
 * 
 * If a precondition is not statisfied a NshmpError is thrown.
 * 
 * @class Preconditions
 * @author Brandon Clayton
 */
export class Preconditions {

  /** @private */
  constructor() {}

  /**
   * Ensures the truth of an expression.
   * 
   * @param {Boolean} expression Expression to check 
   * @param {String} errorMessage The exception message to use if the
   *    expression fails
   */
  static checkArgument(expression, errorMessage) {
    if (!expression) {
      throw new NshmpError(`IllegalArgumentException: ${errorMessage}`);
    }
  }

  /**
   * Check whether an argument is an array.
   * 
   * @param {Array} arr The array to test 
   * @param {String=} errorMessage An optional error message to show
   */
  static checkArgumentArray(arr, errorMessage = 'Must be an array') {
    Preconditions.checkArgument(Array.isArray(arr), errorMessage);
  }

  /**
   * Check whether an argument is an array and all elements
   *    inside are of specific type.
   * 
   * @param {Array<Object>} arr Array to check
   * @param {Object} type Type inside array to check
   */
  static checkArgumentArrayInstanceOf(arr, type) {
    Preconditions.checkArgumentArray(arr);

    for (let val of arr) {
      Preconditions.checkArgumentInstanceOf(val, type);
    }
  }

  /**
   * Check whether an array is of certain length.
   * 
   * @param {Array<Object>} arr The array to test
   * @param {Number} length The length the array should be
   */
  static checkArgumentArrayLength(arr, length) {
    Preconditions.checkArgumentArray(arr);
    Preconditions.checkArgumentInteger(length);
    Preconditions.checkArgument(arr.length == length);
  }

  /**
   * Check whether an argument is an array and all elements inside the
   *    array are of a specificed type.
   * 
   * @param {Array} arr The array to test 
   * @param {String} type The type of data inside the array
   * @param {String=} errorMessage An optional error message to show
   */
  static checkArgumentArrayOf(arr, type, errorMessage = 'Must be an array') {
    Preconditions.checkArgumentArray(arr, errorMessage);

    for (let data of arr) {
      Preconditions.checkArgumentTypeOf(data, type);
    }
  }

  /**
   * Check whether an argument is a boolean.
   * 
   * @param {Boolean} val The value to test 
   */
  static checkArgumentBoolean(val) {
    Preconditions.checkArgumentTypeOf(val, 'boolean');
  }

  /**
   * Check whether an argument is a integer.
   * 
   * @param {Number} val The value to test 
   */
  static checkArgumentInteger(val) {
    Preconditions.checkArgument(Number.isInteger(val), 'Must be an integer');
  }

  /**
   * Check whether an argument is a certain instance of a type.
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkArgumentInstanceOf(val, type) {
    Preconditions.checkArgument(
        val instanceof type,
        `Must be instance of [${type.name}]`);
  }

  /**
   * Check whether an argument is a HTMLElement
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkArgumentInstanceOfHTMLElement(val) {
    Preconditions.checkArgumentInstanceOf(val, HTMLElement);
  }

  /**
   * Check whether an argument is a Map
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkArgumentInstanceOfMap(val) {
    Preconditions.checkArgumentInstanceOf(val, Map);
  }

  /**
   * Check whether an argument is a Set
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkArgumentInstanceOfSet(val) {
    Preconditions.checkArgumentInstanceOf(val, Set);
  }

  /**
   * Check whether an argument is a SVGElement
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkArgumentInstanceOfSVGElement(val) {
    Preconditions.checkArgumentInstanceOf(val, SVGElement);
  }

  /**
   * Check whether an argument is a number.
   * 
   * @param {Number} val The value to test 
   */
  static checkArgumentNumber(val) {
    Preconditions.checkArgumentTypeOf(val, 'number');
  }

  /**
   * Check whether an argument is an object.
   * 
   * @param {Object} val The value to test 
   */
  static checkArgumentObject(val) {
    Preconditions.checkArgumentTypeOf(val, 'object');
  }

  /**
   * Check whether a property exists in an object.
   * 
   * @param {Object} obj The object to check 
   * @param {String} property The property to see if exists
   */
  static checkArgumentObjectProperty(obj, property) {
    Preconditions.checkArgumentObject(obj);
    Preconditions.checkArgumentString(property);
    Preconditions.checkArgument(
        obj.hasOwnProperty(property), 
        `Must have property [${property}] in object`);
  }

  /**
   * Check whether an argument is a string.
   * 
   * @param {String} val The string to test 
   */
  static checkArgumentString(val) {
    Preconditions.checkArgumentTypeOf(val, 'string');
  }

  /**
   * Test whether an argument is of a specific type.
   * 
   * @param {Object} val The value to test 
   * @param {String} type The type the value should be 
   */
  static checkArgumentTypeOf(val, type) {
    Preconditions.checkArgument(typeof(val) == type, `Must be of type [${type}]`);
  }

  /**
   * Check whether a value is null.
   * 
   * @param {Object} val The value to test 
   * @param {String=} errorMessage Optional error message 
   */
  static checkNotNull(val, errorMessage = 'Cannot be null') {
    if (val == null) {
      throw new NshmpError(`NullPointerException: ${errorMessage}`);
    }
  }

  /**
   * Check whether a value is undefined.
   * 
   * @param {Object} val The value to test 
   * @param {String=} errorMessage Optional error message 
   */
  static checkNotUndefined(val, errorMessage = 'Cannot be undefined') {
    if (val == undefined) {
      throw new NshmpError(`NullPointerException: ${errorMessage}`);
    }
  }

  /**
   * Ensures the truth of an expression.
   * 
   * @param {Boolean} expression Expression to check 
   * @param {String} errorMessage The exception message to use if the
   *    expression fails
   */
  static checkState(expression, errorMessage) {
    if (!expression) {
      throw new NshmpError(`IllegalStateException: ${errorMessage}`);
    }
  }

  /**
   * Check whether a value is an array.
   * 
   * @param {Array} arr The array to test 
   * @param {String=} errorMessage An optional error message to show
   */
  static checkStateArray(arr, errorMessage = 'Must be an array') {
    Preconditions.checkState(Array.isArray(arr), errorMessage);
  }

  /**
   * Check whether an argument is an array and all elements
   *    inside are of specific type.
   * 
   * @param {Array<Object>} arr Array to check
   * @param {Object} type Type inside array to check
   */
  static checkStateArrayInstanceOf(arr, type) {
    Preconditions.checkArgumentArray(arr);

    for (let val of arr) {
      Preconditions.checkStateInstanceOf(val, type);
    }
  }

  /**
   * Check whether an array is of certain length.
   * 
   * @param {Array<Object>} arr The array to test
   * @param {Number} length The length the array should be
   */
  static checkStateArrayLength(arr, length) {
    Preconditions.checkArgumentArray(arr);
    Preconditions.checkArgumentInteger(length);
    Preconditions.checkState(arr.length == length);
  }

  /**
   * Check whether a value is an array and all elements inside the
   *    array are of a specificed type.
   * 
   * @param {Array} arr The array to test 
   * @param {String} type The type of data inside the array
   * @param {String=} errorMessage An optional error message to show
   */
  static checkStateArrayOf(arr, type, errorMessage = 'Must be an array') {
    Preconditions.checkArgumentArray(arr, errorMessage);

    for (let data of arr) {
      Preconditions.checkStateTypeOf(data, type);
    }
  }

  /**
   * Check whether a value is a boolean.
   * 
   * @param {Boolean} val The value to test 
   */
  static checkStateBoolean(val) {
    Preconditions.checkStateTypeOf(val, 'boolean');
  }

  /**
   * Check whether a value is a integer.
   * 
   * @param {Number} val The value to test 
   */
  static checkStateInteger(val) {
    Preconditions.checkState(Number.isInteger(val), 'Must be an integer');
  }

  /**
   * Check whether a value is a certain instance of a type.
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkStateInstanceOf(val, type) {
    Preconditions.checkState(
        val instanceof type,
        `Must be instance of [${type.name}]`);
  }

  /**
   * Check whether an argument is a HTMLElement
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkStateInstanceOfHTMLElement(val) {
    Preconditions.checkStateInstanceOf(val, HTMLElement);
  }

  /**
   * Check whether an argument is a Map
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkStateInstanceOfMap(val) {
    Preconditions.checkStateInstanceOf(val, Map);
  }

  /**
   * Check whether an argument is a Set
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkStateInstanceOfSet(val) {
    Preconditions.checkStateInstanceOf(val, Set);
  }

  /**
   * Check whether an argument is a SVGElement
   * 
   * @param {Object} val The value to check 
   * @param {Object} type The type of instance the value should be
   */
  static checkStateInstanceOfSVGElement(val) {
    Preconditions.checkStateInstanceOf(val, SVGElement);
  }

  /**
   * Check whether a value is a number.
   * 
   * @param {Number} val The value to test 
   */
  static checkStateNumber(val) {
    Preconditions.checkStateTypeOf(val, 'number');
  }

  /**
   * Check whether a value is an object.
   * 
   * @param {Object} val The value to test 
   */
  static checkStateObject(val) {
    Preconditions.checkStateTypeOf(val, 'object');
  }

  /**
   * Check whether a property exists in an object.
   * 
   * @param {Object} obj The object to check 
   * @param {String} property The property to see if exists
   */
  static checkStateObjectProperty(obj, property) {
    Preconditions.checkArgumentObject(obj);
    Preconditions.checkArgumentString(property);

    Preconditions.checkState(
        obj.hasOwnProperty(property), 
        `Must have property [${property}] in object`);
  }

  /**
   * Check whether a value is a string.
   * 
   * @param {String} val The string to test 
   */
  static checkStateString(val) {
    Preconditions.checkStateTypeOf(val, 'string');
  }

  /**
   * Test whether a value is of a specific type.
   * 
   * @param {Object} val The value to test 
   * @param {String} type The type the value should be 
   */
  static checkStateTypeOf(val, type) {
    Preconditions.checkState(typeof(val) == type, `Must be of type [${type}]`);
  }

}
