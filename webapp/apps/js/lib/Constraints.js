'use strict';

/**
* @fileoverview Class of static methods to listen and check if a input
*     value is within certain bounds.
* If the value is outside the specified bounds a red focus ring is 
*     added to the parent element.
* 
* @class Constraints      
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export default class Constraints {

  /**
  * @method check
  *
  * Check to see if the value of an element is within specified values
  *     and add a red focus ring to the parent of the element if the 
  *     value is outside of the bounds.
  * @param {!HTMLElement} el - DOM element of the input field
  * @param {!number} minVal - Minimum value of bound (inclusive)
  * @param {!number} maxVal - Maximum value of bound (inclusive)
  * @param {boolean=} canHaveNaN - Whether the value can be empty
  * @return {boolean} Whether the value is inside the bounds (true) or not
  */
  static check(el, minVal, maxVal, canHaveNaN = false) {
    let isInBounds;
    let val = parseFloat(el.value);

    if (val < minVal || val > maxVal || (isNaN(val) && !canHaveNaN)) {
      isInBounds = false;
    } else {
      isInBounds = true;
    }
    
    d3.select(el.parentNode)
        .classed('has-error', !isInBounds);
    
    return isInBounds;
  }

  /**
  * @method onInput
  *
  * Add listener, oninput, to a DOM element and check if the inputted
  *     value is within bounds.
  * @param {!HTMLElement} el - DOM element of the input field
  * @param {!number} minVal - Minimum value of bound (inclusive)
  * @param {!number} maxVal - Maximum value of bound (inclusive)
  * @param {boolean=} canHaveNaN - Whether the value can be empty
  */
  static onInput(el, minVal, maxVal, canHaveNaN = false){
    $(el).on('input', (event) => { 
      Constraints.check(event.target, minVal, maxVal, canHaveNaN); 
    }); 
  }

  /**
  * @method addTooltip
  *
  * Add a Bootstrap tooltip to a DOM element showing the specified bounds.
  *     Example: [0, 5]
  */
  static addTooltip(el, minVal, maxVal) {
    d3.select(el)
        .attr('data-toggle', 'tooltip');
    
    let title = '[' + minVal + ', ' + maxVal +']'; 
    let options = {
      container: 'body',
    };
    
    $(el).attr('title', title)
        .attr('data-original-title', title)
        .tooltip(options);
  }

}
