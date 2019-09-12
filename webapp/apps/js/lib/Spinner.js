'use strict';

/**
* @class Spinner
*
* @fileoverview Creates a loading spinner
* 
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export default class Spinner{
 
  /**
  * @param {HTMLElement=} containerEl - Optional container element to put 
  *     spinner. Default is body.
  */ 
  constructor(containerEl = document.querySelector('body')) {
    /** @type {HTMLElement */
    this.containerEl = containerEl;
    /** @type {HTMLElement */
    this.spinnerEl = undefined;
    /** @type {HTMLElement */
    this.cancelRequestEl = undefined; 
  }

  /**
  * @method off 
  *
  * Remove the loading spinner overlay 
  */
  off() {
    $(this.spinnerEl).modal('hide');

    d3.select(this.spinnerEl)
        .remove();
  }
  
  /**
   * Show the spinner
   * 
   * @param {Function} promiseReject The Promise reject function
   * @param {String=} text The text to appear under the spinner.
   *        Default is "Loading". 
   */
  on(promiseReject, text = 'Loading') {
    let spinnerOverlayD3 = d3.select(this.containerEl)
        .append('div')
        .attr('class', 'modal Spinner')
        .attr('tabindex', '-1')
        .attr('role', 'dialog');
    
    let spinnerContentD3 = spinnerOverlayD3.append('div')
        .attr('class', 'modal-dialog modal-sm')
        .attr('role', 'document')
        .append('div')
        .attr('class', 'modal-content');
    
    let spinnerBodyD3  = spinnerContentD3.append('div')
        .attr('class', 'modal-body');

    spinnerBodyD3.append('p')
        .attr('class', 'loading-spinner');

    spinnerBodyD3.append('p')
        .attr('class', 'spinner-text')
        .text(text);
    
    spinnerContentD3.append('div')
        .attr('class', 'modal-footer')
        .append('button')
        .attr('class', 'btn btn-primary cancel-request')
        .attr('type', 'button')
        .text('Cancel Request');

    spinnerOverlayD3.lower();
    this.spinnerEl = spinnerOverlayD3.node();
    this.cancelRequestEl = this.spinnerEl.querySelector('.cancel-request');

    $(this.spinnerEl).modal({backdrop: 'static'});

    this._onCancelRequest(promiseReject);
  }

  /**
   * Listen for a press on the "Cancel Request" button and 
   *    call the reject function if button is pressed.
   * 
   * @param {Function} promiseReject The Promise reject function
   */
  _onCancelRequest(promiseReject) {
    $(this.cancelRequestEl).on('click', (event) => {
      promiseReject('cancel');
      this.off();
    });
  }

}
