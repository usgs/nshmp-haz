
/**
 * @fileoverview Error class that will create a Bootstrap modal
 *    with the error message.
 * 
 * @extends Error
 * @author Brandon Clayton
 */
export default class NshmpError extends Error {

  /**
   * Create a Boostrap modal with an error message.
   *  
   * @param {String} errorMessage The error message to display.
   */
  constructor(errorMessage) {
    super(errorMessage);

    if (errorMessage instanceof NshmpError) {
      console.error(errorMessage);
      return;
    }

    this.message = errorMessage;
    try {
      let els = this._createErrorModal();
      this.el =  els.get('el');
      this.headerEl = els.get('headerEl');
      this.bodyEl = els.get('bodyEl');
      this.footerEl = els.get('footerEl');

      $(this.el).modal({backdrop: 'static'});

      $(this.el).on('hidden.bs.modal', (event) => {
        d3.select(this.el).remove();
      });
    } catch (err) {
      alert(`${err} \n ${this.message}`);
    }

    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, NshmpError);
    }
  }

  /**
   * Check an array of web service responses to see if any web service
   *    response has "status" = "error".
   * 
   * If a web service has an error a NshmpError is thrown
   * 
   * If a web service response has status error and the 
   *    supplied plot has a method clearData, it will be invoked
   *    for the upper panel and lower panel.
   * 
   * @param {Array<Object>} responses The web service responses 
   * @param {D3LinePlot || D3GeoDeagg} plots The plots to clear
   */
  static checkResponses(responses, ...plots) {
    let errorMessage = '';
    let hasError = false;

    for (let response of responses) {
      let status = response.status;
      if (status == 'error') {
        hasError = true;
        errorMessage += `<p> ${response.message} </p> \n`;
      }
    }

    if (hasError) {
      for (let plot of plots) {
        if (plot.clearData) {
          plot.clearData(plot.upperPanel);
          plot.clearData(plot.lowerPanel);
        }
      }
      
      throw new NshmpError(errorMessage);
    }
  }

  /**
   * Check a web service response to see for "status" = "error".
   * 
   * If a web service has an error, a native JavaScript 
   *    Error is thrown to allow a catch method to catch it.
   * 
   * If a web service response has status error and the 
   *    supplied plot has a method clearData, it will be invoked
   *    for the upper panel and lower panel.
   * 
   * @param {Object} response 
   * @param {D3LinePLot || D3GeoDeagg} plots The plots to clear 
   */
  static checkResponse(response, ...plots) {
    return NshmpError.checkResponses([response], ...plots);
  }

  /**
   * Convience method to throw a new NshmpError.
   * If the error message equals 'cancal' an error is not thrown,
   *    useful when canceling a Promise.
   * 
   * @param {String} errorMessage The exception message to use
   */
  static throwError(errorMessage) {
    if (errorMessage instanceof NshmpError) {
      console.error(errorMessage);
      return
    }
    
    if (errorMessage == 'cancel') return;
    
    throw new NshmpError(errorMessage);
  }

  /**
   * Create the Bootstrap modal
   */
  _createErrorModal() {
    /* Modal */
    let overlayD3 = d3.select('body')
        .append('div')
        .attr('class', 'modal error-modal')
        .attr('id', 'error-modal')
        .attr('tabindex', '-1')
        .attr('role', 'dialog');

    /* Modal content */
    let contentD3 = overlayD3.append('div')
        .attr('class', 'modal-dialog vertical-center')
        .attr('role', 'document')
        .style('display', 'grid')
        .style('margin', '0 auto')
        .append('div')
        .attr('class', 'modal-content')
        .style('overflow', 'hidden');
    
    let contentEl = contentD3.node();
    let el = overlayD3.node();

    let headerEl = this._createModalHeader(contentEl);
    let bodyEl = this._createModalBody(contentEl);
    let footerEl = this._createModalFooter(contentEl);

    let els = new Map();
    els.set('el', el);
    els.set('headerEl', headerEl);
    els.set('bodyEl', bodyEl);
    els.set('footerEl', footerEl);

    return els;
  }

  /**
   * Add modal footer with collapsible panel with stack trace
   * @param {HTMLElement} modalEl 
   */
  _createModalFooter(modalEl) {
    let footerD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'panel-footer');

    let footerTextD3 = footerD3.append('div')
        .attr('role', 'button')
        .attr('data-toggle', 'collapse')
        .attr('data-parent', '#error-modal')
        .attr('href', '#stack-trace')
        .attr('aria-expanded', 'false')
        .attr('aria-controls', 'stack-trace')
        .text('Stack trace');

    let chevronD3 = footerTextD3.append('span')
        .attr('class', 'pull-right glyphicon glyphicon-chevron-down');

    let collapseD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'panel-collapse collapse')
        .attr('id', 'stack-trace')
        .attr('role', 'tabpanel');
    
    collapseD3.append('div')
        .attr('class', 'panel-body')
        .text(this.stack);

    let collapseEl = collapseD3.node();
    let chevronEl = chevronD3.node();
    this._collapseStackTraceListener(collapseEl, chevronEl);

    return footerD3.node();    
  }

  /**
   * Set event listeners for the collapsing panel
   * @param {HTMLElement} collapseEl
   * @param {HTMLElement} chevronEl 
   */
  _collapseStackTraceListener(collapseEl, chevronEl) {
    let chevronDown = 'glyphicon-chevron-down';
    let chevronUp = 'glyphicon-chevron-up';

    $(collapseEl).on('show.bs.collapse', () => {
      chevronEl.classList.remove(chevronDown);
      chevronEl.classList.add(chevronUp);
    });

    $(collapseEl).on('hide.bs.collapse', () => {
      chevronEl.classList.remove(chevronUp);
      chevronEl.classList.add(chevronDown);
    });
  }

  /**
   * Create the modal header
   * @param {HTMLElement} modalEl The modal element
   */
  _createModalHeader(modalEl) {
    let headerD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'modal-header')
        .style('background-color', '#EF9A9A');
    
    headerD3.append('button')
        .attr('type', 'button')
        .attr('class', 'btn close')
        .attr('data-dismiss', 'modal')
        .style('opacity', '0.5')
        .append('span')
        .attr('class', 'glyphicon glyphicon-remove')

    headerD3.append('h4')
        .attr('class', 'modal-title')
        .text('Error');
    
    return headerD3.node();
  }

  /**
   * Create the modal body
   * @param {HTMLElement} modalEl The model element
   */
  _createModalBody(modalEl) {
    let bodyD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'modal-body')
        .style('word-wrap', 'break-word')
        .html(this.message);

    return bodyD3.node();
  }

}
