'use strict';

import Tools from './Tools.js';
import Constraints from './Constraints.js';

/**
 * @fileOverview Dynamically create a new control panel for a
 *    nshmp web application. 
 * 
 * For the control panel to render correctly the following files
 *    must be present in the HTML file:
 *    - CSS: Bootstrap v3.3.6 
 *    - CSS: webapp/apps/css/template.css 
 *    - JavaScript: jQuery v3.2.1 
 *    - JavaScript: Bootstrap 3.3.7 
 *    - JavaScript: D3 v4 
 * 
 * Use the {@link FormGroupBuilder} to create form groups that can 
 *    consist of a:
 *    - Input form
 *    - Slider
 *    - Button group
 *    - Select menu
 * 
 * @example
 *  // Create an empty control panel
 *  let controlPanel = new ControlPanel();
 * 
 *  // Input form options
 *  let inputOptions = {
 *    id: 'zTop',
 *    label: 'z<sub>Top</sub>',
 *    labelColSize: 'col-xs-2',
 *    max: 700,
 *    min: 0,
 *    name: 'zTop',
 *    step: 0.5,
 *    value: 0.5,
 *  };
 * 
 *  // Button group options
 *  let btnOptions = {
 *    addLabel: false,
 *    id: 'zTop-btn-group',
 *    name: 'zTop',
 *  };
 * 
 *  // Slider options
 *  let sliderOptions = { id: 'zTop-slider' };
 * 
 *  // zTop buttons for button group
 *  let zTopBtns = [
 *    { text: '5.0 km', value: 5.0 },
 *    { text: '10.0 km', value: 10.0 },
 *    { text: '15.0 km', value: 15.0 },
 *  ];
 * 
 *  // Create a form group with a input, slider, and button group in
 *  // that order.
 *  let els = controlPanel.formGroupBuilder()
 *      .addInput(inputOptions)
 *      .addInputSlider(sliderOptions)
 *      .addBtnGroup(zTopBtns, btnOptions)
 *      .syncValues()
 *      .addInputTooltip()
 *      .addInputAddon('km')
 *      .build();
 * 
 *  // Get input el
 *  let zTopEl = els.inputEl;
 *  
 *  // Get input group el
 *  let zTopInputGroupEl = els.inputGroupEl;
 *  
 *  // Get btn group el
 *  let zTopBtnGroupEl = els.btnGroupEl;
 * 
 *  // Get slider el
 *  let zTopSliderEl = els.sliderEl;
 *    
 * @class ControlPanel
 * @author Brandon Clayton
 */
export default class ControlPanel {

  constructor() {
    let controlPanelD3 = d3.select('body')
        .insert('div', '#content')
        .attr('class', 'control-panel')
        .attr('id', 'control');

    let inputsD3 = controlPanelD3.append('form')
        .attr('id', 'inputs');
        
    let formHorizontalD3 = inputsD3.append('div')
        .attr('class', 'form-horizontal');

    /** The control panel element - @type {HTMLElement} */
    this.controlPanelEl = controlPanelD3.node();
    /** The inputs element - @type {HTMLElement} */
    this.inputsEl = inputsD3.node();
    /** The horizontal form element - @type {HTMLElement} */
    this.formHorizontalEl = formHorizontalD3.node();

    /**
     * Options for creating button groups.
     * @see createBtnGroup
     * 
     * 
     * @typedef {Object} BtnGroupOptions
     * @property {Boolean} addLabel - To add a label before the button group.
     * @property {HTMLElement} appendTo - Element to append the button group to.
     * @property {String} btnGroupColSize - Bootstrap column size.
     * @property {String} btnSize - The Bootstrap button size.
     * @property {String} id - Button group id attibute.
     * @property {String} label - The label text.
     * @property {String} name - Button group name attribute.
     * @property {String} paddingTop - The top padding for the button group.
     * @property {String} title - Button group tittle attribute.
     * @property {String} type - The type of button in the button group. 
     *    'radio' equals single selection, 'checkbox' equals multiple selection.
     */
    this.btnGroupOptions = {
      addLabel: false,
      appendTo: this.formHorizontalEl,
      btnGroupColSize: 'col-xs-10 col-xs-offset-2',
      btnSize: 'btn-xs',
      id: null,
      label: null,
      labelControl: true,
      name: null,
      paddingTop: '0.5em', 
      title: null,
      type: 'radio',
    };

    /**
     * Options for creating a Bootstrap form group div.
     * @see createFormGroup
     * 
     * @typedef {Object} FormGroupOptions
     * @property {HTMLElement} appendTo - Where to append the form group div. 
     * @property {String} formClass - Any additional classes to add to the 
     *    form group.
     * @property {String} formSize - The Bootstrap form group size.
     */
    this.formGroupOptions = {
      appendTo: this.formHorizontalEl,
      formClass: '',
      formSize: 'form-group-sm',
    };

    /**
     * Options for creating the ground motion model sorter.
     * @see createGmmSorter
     * 
     * @typedef {Object} GmmOptions
     * @property {String} label - GMM sorter label.
     * @property {String} labelFor - Where the label is bound.
     * @property {Number} size - The size of the gmm select menu.
     */
    this.gmmOptions = {
      label: 'Ground Motion Models:',
      labelControl: false,
      labelFor: 'gmms',
      size: 16,
    };

    /**
     * Options for creating an input form.
     * @see createInput
     * 
     * @typedef {Object} InputOptions
     * @property {Boolean} addLabel - To add a label before the input form.
     * @property {HTMLElement} appendTo - Element to append the input form to.
     * @property {Boolean} checked - Whether to be checked, only for 
     *    ControlPanel#createCheckbox.
     * @property {Boolean} disabled - Whether input form is disabled.
     * @property {Boolean} formControl - Wheater to add the Bootstrap
     *    'form-control' class to the the input form.
     * @property {String} inputColSize -  Bootstrap column size for the 
     *    input form.
     * @property {String} inputGroupFloat - Direction to float the 
     *    input form.
     * @property {String} label - The label if 'addLabel' is true.
     * @property {String} labelColSize - The Bootstrap column size for the 
     *    label if 'addLabel' is true.
     * @property {Boolean} labelBreak - Add a line break after the label
     *    if true and 'addLabel' is true.
     * @property {String} labelFloat - Direction to float the label.
     * @property {Number} max - Sets the max attribute. 
     * @property {Number} min - Sets the min attribute. 
     * @property {String} name - Sets the name attribute. 
     * @property {Boolean} readOnly - Sets the readOnly property.
     * @property {Number} step -  Sets the step attribute.
     * @property {String} text - The text for after a checkbox, only used 
     *    for ControlPanel.createCheckbox.
     * @property {String | Number} value - Sets the value attribute.
     */
    this.inputOptions = {
      addLabel: true,
      appendTo: this.formHorizontalEl,
      checked: false,
      disabled: false,
      formControl: true,
      id: null,
      inputColSize: 'col-xs-3',
      inputGroupFloat: 'left',
      inputGroupSize: 'input-group-sm',
      label: '',
      labelBreak: false,
      labelColSize: '',
      labelControl: true,
      labelFloat: 'left',
      max: null,
      min: null,
      name: null,
      readOnly: false,
      step: 1,
      text: null,
      type: 'number',
      value: null,
    };

    /**
     * Options for creating a label.
     * @see createLabel
     * 
     * @typedef {Object} LabelOptions 
     * @property {HTMLElement} appendTo - Where to append the form group div. 
     * @property {String} label - The label text.
     * @property {String} labelColSize - The Bootstrap column size for the 
     *    label if 'addLabel' is true.
     * @property {String} labelFor - Where the label is bound.
     * @property {String} float - Where the label should float.
     */
    this.labelOptions = {
      appendTo: this.formHorizontalEl,
      label: '',
      labelColSize: '',
      labelControl: true,
      labelFor: null,
      float: 'initial',
    };

    /**
     * Options for creating a select menu.
     * @see createSelect
     * 
     * @typedef {Object} SelectOptions
     * @property {Boolean} addLabel - To add a label before the select menu.
     * @property {HTMLElement} appendTo - Element to append the select menu to.
     * @property {String} id - The id attribute of the select menu.
     * @property {String} label - The label if 'addLabel' is true.
     * @property {String} labelColSize - The Bootstrap column size for the 
     *    label if 'addLabel' is true.
     * @property {Boolean} labelBreak - Add a line break after the label
     *    if true and 'addLabel' is true.
     * @property {Boolean} multiple - Whether the select is a multi select.
     * @property {String} name - The name attribue of select menu.
     * @property {Number} size - The size of the select menu.
     * @property {String | Number} value - A selected value.
     */
    this.selectOptions = {
      addLabel: true,
      appendTo: this.formHorizontalEl,
      id: null,
      label: '',
      labelBreak: true,
      labelColSize: '',
      labelControl: true,
      multiple: false,
      name: null,
      size: 0,
      value: '',
    };

    /**
     * Options for creating a slider.
     * @see createInputSlider
     * 
     * @typedef {Object} SliderOptions 
     * @property {HTMLElement} appendTo - Where to append the form group div. 
     * @property {String} id - The id of the slider.
     * @property {HTMLElement} inputEl - The input form element that the slider
     *    should be bound to.
     * @property {String} sliderColSize - The Bootstrap column size for the
     *    slider.
     */
    this.sliderOptions = {
      appendTo: this.formHorizontalEl,
      id: '',
      inputEl: null,
      sliderColSize: 'col-xs-5 col-xs-offset-1',
    }
  }

  /**
   * Return a new {@link FormGroupBuilder} to build a form 
   *    group that can consists of a:
   *    - input form with Bootstrap input add on
   *    - slider bound to an input form
   *    - checkbox
   *    - select menu
   *    - button group
   * 
   * @example
   * // Create empty control panel
   * let controlPanel = new ControlPanel();
   * 
   * // Get form group builder
   * let formBuilder = controlPanel.formGroupBuilder();
   * 
   * @param {FormGroupOptions} formGroupOptions The form group options.
   * @returns A new instance of FormGroupBuilder
   */
  formGroupBuilder(formGroupOptions) {
    return new this.FormGroupBuilder(formGroupOptions);
  }

  /**
   *  Build a Bootstrap form group with combinations of: 
   *    - input form with Bootstrap input add on
   *    - slider bound to an input form
   *    - checkbox
   *    - select menu
   *    - button group
   * 
   * NOTE: The current builder cannot add multiple of the same element.
   */
  get FormGroupBuilder() {
    let controlPanel = this;
   
    class FormGroupBuilder {

      /**
       * Create the form group needed to add element to.
       * @param {FormGroupOptions} formGroupOptions 
       */
      constructor(formGroupOptions) {
        /** The form group element - @type {HTMLElement} */
        this.formGroupEl = controlPanel._createFormGroup(formGroupOptions);

        /** Button group element - @type {HTMLElement} */ 
        this.btnGroupEl = undefined;
        /** Checkbox element - @type {HTMLElement} */
        this.checkboxEl = undefined;
        /** Input form element - @type {HTMLElement} */
        this.inputEl = undefined;
        /** Input group element - @type {HTMLElement} */
        this.inputGroupEl = undefined;
        /** Select ment element - @type {HTMLElement} */
        this.selectEl = undefined;
        /** Slider element - @type {HTMLElement} */
        this.sliderEl = undefined;

        /** 
         * Whether to sync the values between an input form, slider, and button
         *    group. 
         * 
         * Syncing the values will work as long as any two of these 
         *    elements exists.
         * 
         * This is set to true by using the {@link syncValues} method.
         * @type {Boolean}
         */
        this._toSyncValues = false;

        /** 
         * Whether to add a Bootstrap tooltip to the input form.
         *
         * This is set to true by using the {@link addInputTooltip} method.
         *  
         * NOTE: The input form must have the min and max attribute set
         *    for this to work.
         * @see Constraints#addTooltip
         * @type {Boolean}
         */
        this._toAddInputTooltip = false;

        /**
         * Whether to add a bootstrap input form add on at the end of the 
         *    input form.
         * 
         * This is set to true by using the {@link addInputAddon} method/
         * @type {Boolean}
         */
        this._toAddInputAddon = false;
      }

      /**
       * Build the form group and return an Object with 
       *    the HTMLElements based on what was choosen to be built.
       * 
       * @return {FormGroupElements} All HTMLElement
       */
      build() {
        /**
         * The {HTMLElement}s associated with the elements created.
         * @typedef {Object} FormGroupElements 
         * @property {HTMLElement} formGroupEl - The form group element.
         * @property {HTMLElement} btnGroupEl - The button group element. Only
         *    returned if addBtnGroup is called in the builder.
         * @property {HTMLElement} checkboxEl - The checkbox element. Only
         *    returned if addCheckbox is called in the builder.
         * @property {HTMLElement} inputEl - The input element. Only returned
         *    if addInput is called in the builder.
         * @property {HTMLElement} inputGroupEl - The input group element 
         *    where the input form exist. Only returned if addInput
         *    is called in the builder.
         * @property {HTMLElement} sliderEl - The slider element. Only
         *    returned if addInputSlider is called in the builder.
         * @property {HTMLElement} selectEl - The select menu element. Only
         *    retunred if the addSelect is called in the builder.
         */
        let els = {};

        els.formGroupEl = this.formGroupEl;
       
        if (this.btnGroupEl) {
          els.btnGroupEl = this.btnGroupEl;
        }

        if (this.checkboxEl) {
          els.checkboxEl = this.checkboxEl;
        }

        if (this.inputEl) {
          els.inputEl = this.inputEl;
          els.inputGroupEl = this.inputGroupEl;
          
          if (this._toAddInputAddon) {
            controlPanel._inputAddon(this.inputEl, this.addonText);
          }

          if (this._toAddInputTooltip) {
            Constraints.addTooltip(
              this.inputEl, 
              this.inputEl.min, 
              this.inputEl.max)
          }
        } 

        if (this.sliderEl) {
          els.sliderEl = this.sliderEl;
        }

        if (this.selectEl) {
          els.selectEl =  this.selectEl;
        }

        if (this._toSyncValues) {
          controlPanel._syncValues(
              this.inputEl, 
              this.btnGroupEl, 
              this.sliderEl);
        }

        return els;
      }

      /**
       * Add a button group to the form group.
       * @param {Buttons} btns The buttons for the button group.
       * @param {BtnGroupOptions} options The button group options.
       */
      addBtnGroup(btns, options) {
        options.appendTo = this.formGroupEl;
        this.btnGroupEl = controlPanel._createBtnGroup(btns, options);
        return this;
      }

      /**
       * Add a checkbox to the form group.
       * @param {InputOptions} options The input options.
       */
      addCheckbox(options) {
        options.appendTo = this.formGroupEl;
        this.checkboxEl = controlPanel._createCheckbox(options);
        return this;
      }

      /**
       * Add an input form to the form group.
       * @param {InputOptions} options The input options.
       */
      addInput(options) {
        options.appendTo = this.formGroupEl;
        this.inputEl = controlPanel._createInput(options);
        this.inputGroupEl = this.inputEl.parentNode;
        return this;
      }

      /**
       * Add the input form addon .
       * @param {String} text The text to add to the input addon
       */
      addInputAddon(text) {
        this.addonText = text;
        this._toAddInputAddon = true;
        return this;
      }

      /**
       * Add a slider to the form group.
       * @param {SliderOptions} options The slider options.
       */
      addInputSlider(options) {
        options.appendTo = this.formGroupEl;
        options.inputEl = this.inputEl;
        this.sliderEl = controlPanel._createInputSlider(options);
        return this;
      }

      /**
       * Add a Boostrap tooltip to the input form.
       */
      addInputTooltip() {
        this._toAddInputTooltip = true;
        return this;
      }

      /**
       * Add a select menu to the form group. 
       * @param {Array<HTMLElement>} optionArray The select menu values.
       * @param {SelectOptions} options The select menu options. 
       */
      addSelect(optionArray, options) {
        options.appendTo = this.formGroupEl;
        this.selectEl = controlPanel._createSelect(optionArray, options);
        return this;
      }

      /**
       * Sync the values between a input form, slider, and a button group.
       * A minimum of two of these elements must be defined.
       */
      syncValues() {
        this._toSyncValues = true;
        return this;
      }

    }
    return FormGroupBuilder;
  }
 
  createGmmSelect(spectraParameters, gmmOptions = {}) {
    let options = $.extend({}, this.gmmOptions, gmmOptions);

    let gmmSorterEls = this._createGmmSorter(options);
    let gmmSorterEl = gmmSorterEls.gmmSorterEl;
    let gmmFormGroupEl = gmmSorterEls.formGroupEl;

    /* Alphabetic GMMs */
    let gmmAlphaOptions = $();
    spectraParameters.gmm.values.forEach((gmm) => {
      gmmAlphaOptions = gmmAlphaOptions.add($('<option>')
        .attr('value', gmm.id)
        .attr('id', gmm.id)
        .text(gmm.label));
    });

    /* Grouped GMMs */
    let gmmGroupOptions = $();
    spectraParameters.group.values.forEach((group) => {
      let members = group.data;
      let optGroup = $('<optgroup>')
          .attr('label', group.label)
          .attr('id', group.id);
      gmmGroupOptions = gmmGroupOptions.add(optGroup);
      optGroup.append(gmmAlphaOptions
        .filter((index, gmmOption) => {
          return members.includes(gmmOption.getAttribute('value')); 
        })
        .clone());
    });

    let gmmsEl = this._createSelect(
        gmmGroupOptions, {  
          appendTo: gmmFormGroupEl, 
          addLabel: false,
          id: 'gmms',
          name: 'gmm',
          multiple: true,
          size: options.size});

    /* Bind option views to sort buttons */
    $('input', gmmSorterEl).change((event) => {
      let options = event.target.value === 'alpha' ? 
          gmmAlphaOptions : gmmGroupOptions;
      this.updateSelectOptions(gmmsEl, options);
      $(gmmsEl).scrollTop(0);
      Tools.resetRadioButton(event.target);
    });

    /* Add tooltips */
    $(gmmSorterEls.gmmAlphaEl).tooltip({container: 'body'});
    $(gmmSorterEls.gmmGroupEl).tooltip({container: 'body'});

    let els = {
      gmmSorterEl: gmmSorterEl,
      gmmAlphaEl: gmmSorterEls.gmmAlphaEl,
      gmmGroupEl: gmmSorterEls.gmmGroupEl,
      gmmFormGroupEl: gmmFormGroupEl,
      gmmAlphaOptions: gmmAlphaOptions,
      gmmGroupOptions: gmmGroupOptions,
      gmmsEl: gmmsEl,
    };

    return els;
  }

  /**
   * Create a label.
   * @param {LabelOptions} labelOptions 
   */
  createLabel(labelOptions) {
    let options = $.extend({}, this.labelOptions, labelOptions);

    let labelD3 = d3.select(options.appendTo)
        .append('label')
        .attr('class', options.labelColSize)
        .classed('control-label', options.labelControl)
        .attr('for', options.labelFor)
        .style('float', options.float)
        .html(options.label);

    return labelD3.node(); 
  }

  /**
   * Convert a single selectable button group (a radio button group)
   *    to a multi-selectable button group (a checkbox button group) and
   *    disable the input form and slider (if present).
   * 
   * The name attribute is removed and kept under the data-name attribute
   *    so the input form will not be included in a serialization of
   *    the inputs;
   * 
   * @param {FormGroupElements} els The elements of the input form,
   *    button group, and slider (optional).
   *    - els.inputEl
   *    - els.btnGroupEl
   *    - els.sliderEl
   */
  toMultiSelectable(els) {
    let inputName = els.inputEl.getAttribute('name');
    d3.select(els.inputEl)
        .property('disabled', true)
        .attr('name', '');

    if (els.sliderEl) els.sliderEl.disabled = true;

    d3.select(els.btnGroupEl)
        .selectAll('input')
        .attr('type', 'checkbox')
        .each((d, i, el) => {
          let isActive = d3.select(el[i].parentNode)
              .classed('active');

          if (isActive) el[i].checked = true;
        });
  }

  /**
   * Convert an array of objects into an array of option elements
   *    for a select menu.
   *
   * The object inside the array must have a text and value field.
   * 
   * @param {Array<Object>} values The values to convert.
   * @return {Array<HTMLElement>} The array of option elements.
   */
  toSelectOptionArray(values) {
    let optionArray = [];
    values.forEach((val) => {
      let el = d3.create('option')
          .attr('value', val.value)
          .text(val.text)
          .node();
      optionArray.push(el);
    });
    return optionArray;
  }

  /**
   * Convert a multi-selectable button group (a checkbox button group)
   *    to a single selectable button group (a radio button group) and
   *    re-enable the input form and slider (if present).
   * 
   * The name attribute is re-added to the input form so it can
   *    be included in any serialization of the inputs.
   * 
   * @param {FormGroupElements} els The elements of the input form,
   *    button group, and slider (optional).
   *    - els.inputEl
   *    - els.btnGroupEl
   *    - els.sliderEl
   */
  toSingleSelectable(els) {
    let inputName = els.inputEl.dataset.name;
    d3.select(els.inputEl)
        .property('disabled', false)
        .attr('name', inputName);

    if (els.sliderEl) els.sliderEl.disabled = false;

    d3.select(els.btnGroupEl)
        .selectAll('label')
        .classed('active', false)
        .selectAll('input')
        .attr('type', 'radio')
        .property('checked', false);

    $(els.inputEl).trigger('input');
  }

  /**
   * Update a select menu values.
   * 
   * @param {HTMLElement} selectEl The select menu element.
   * @param {Array<HTMLElement>} optionArray The select menu option array.
   */
  updateSelectOptions(selectEl, optionArray) {
    d3.select(selectEl)
        .selectAll('*')
        .remove();
    
    d3.select(selectEl)
        .selectAll('*')
        .data($(optionArray).clone())
        .enter()
        .append((d) => { return d; });
  }

  /**
   * @private
   * Create a Bootstrap radio/checkbox button group in the control
   *    panel.
   *  
   * The type of button group is defined by the 'type' option.
   *  
   * @param {Buttons} btns The buttons that make up the button group. 
   * @param {BtnGroupOptions} btnGroupOptions The button group options.
   * @returns {HTMLElement} The element representing the button group.
   */
  _createBtnGroup(btns, btnGroupOptions) {
    /** @type {BtnGroupOptions} */
    let options = $.extend({}, this.btnGroupOptions, btnGroupOptions);

    if (options.addLabel) {
      this.createLabel({
        appendTo: options.appendTo, 
        label: options.label, 
        labelControl: options.labelControl,
        labelFor: options.id});
    }

    let btnGroupD3 = d3.select(options.appendTo)
        .append('div')
        .attr('class', 'btn-group')
        .classed(options.btnGroupColSize, true)
        .attr('data-toggle', 'buttons')
        .attr('id', options.id)
        .attr('name', options.name)
        .style('padding-top', options.paddingTop);
    
    btnGroupD3.selectAll('label')
        .data(btns)
        .enter()
        .append('label')
        .attr('class', 'btn btn-default')
        .classed('active', (d) => { return d.isActive; })
        .classed(options.btnSize, true)
        .html((d) => {
          return '<input type="' + options.type + '" ' + 
              'value="' + d.value + '" ' + 
              'id="' + (d.id || '') + '" ' +
              'title="' + d.text + '"> ' + d.text;  
        });
    
    return btnGroupD3.node();
  }

  /**
   * @private  
   * Create a checkbox in the control panel.
   * 
   * @param {InputOptions} inputOptions The input options for the checkbox.
   * @returns {HTMLElement} The checkbox element.
   */
  _createCheckbox(inputOptions) {
    let options = $.extend({}, this.inputOptions, inputOptions);
    options.type = 'checkbox';
    options.formControl = false;
    options.addLabel = false;

    let tmpEl = this._createInput(options);
    let inputD3 = d3.select(tmpEl.parentNode)
        .append('label')
        .attr('class', 'control-label secondary-input')
        .attr('for', options.id)
        .html(tmpEl.outerHTML + ' ' + options.text)
        .select('input');

    d3.select(tmpEl).remove();
    
    inputD3.property('checked', options.checked);
    return inputD3.node();
  }

  /**
   * @private 
   * Create a form group in the control panel.
   * 
   * @param {FormGroupOptions} formGroupOptions The form group options.
   * @returns {HTMLElement} The form group element.
   */
  _createFormGroup(formGroupOptions) {
    let options = $.extend({}, this.formGroupOptions, formGroupOptions);

    let formGroupD3 = d3.select(options.appendTo)
        .append('div')
        .attr('class', 'form-group')
        .classed(options.formClass, true)
        .classed(options.formSize, true)
        .attr('id', options.id);

    return formGroupD3.node();
  }

  /**
   * @private 
   * Convience method to creating the ground motion model sorter.
   * @param {GmmOptions} options The GMM options. 
   */
  _createGmmSorter(options) {
    let formGroupEl = this._createFormGroup(); 

    this.createLabel({
        appendTo: formGroupEl, 
        label: options.label, 
        labelControl: options.labelControl,
        labelFor: options.labelFor});

    let gmmSorterD3 = d3.select(formGroupEl)
        .append('div')
        .attr('id', 'gmm-sorter')
        .style('float', 'right')
        .attr('class', 'btn-group btn-group-xs')
        .attr('data-toggle', 'buttons');

    gmmSorterD3.append('label')
        .attr('class', 'btn btn-default gmm-group active')
        .attr('data-toggle', 'tooltip')
        .attr('data-container', 'body')
        .attr('title', 'Sort by Group')
        .attr('for', 'gmm-sort-group')
        .html('<input type="radio" value="group" id="gmm-sort-group"' +
            'aria-label="Sort by Group"> ' + 
            '<span class="glyphicon glyphicon-list" aria-hidden="true"/>');
    
    gmmSorterD3.append('label')
        .attr('class', 'btn btn-default gmm-alpha')
        .attr('data-toggle', 'tooltip')
        .attr('data-container', 'body')
        .attr('title', 'Sort Alphabetically')
        .attr('for', 'gmm-sort-alpha')
        .html('<input type="radio" value="alpha" id="gmm-sort-alpha"' +
            'aria-label="Sort Alphabetically"> ' + 
            '<span class="glyphicon glyphicon-sort-by-alphabet" ' +
            'aria-hidden="true"/>');
    
    let els = {
      gmmSorterEl: gmmSorterD3.node(),
      gmmAlphaEl: gmmSorterD3.select('.gmm-alpha').node(),
      gmmGroupEl: gmmSorterD3.select('.gmm-group').node(),
      formGroupEl: formGroupEl,
    };

    return els; 
  }

 /**
  * @private 
  * Create a input form in the control panel.
  * 
  * @param {InputOptions} inputOptions The input form options.
  * @returns {HTMLElement} The input form element.
  */
  _createInput(inputOptions) {
    let options = $.extend({}, this.inputOptions, inputOptions);

    if (options.addLabel) {
      this.createLabel({
          appendTo: options.appendTo, 
          label: options.label, 
          labelControl: options.labelControl,
          labelFor: options.id,
          labelColSize: options.labelColSize,
          float: options.labelFloat});
      
      if (options.labelBreak) {
        d3.select(options.appendTo)
            .append('br');
      }
    }

    let inputD3 = d3.select(options.appendTo)
        .append('div')
        .attr('class', 'input-group')
        .classed(options.inputGroupSize, true)
        .classed(options.inputColSize, true)
        .attr('id', options.id + '-input-group')
        .style('float', options.inputGroupFloat)
        .append('input')
        .classed('form-control', options.formControl) 
        .attr('data-name', options.name)
        .attr('id', options.id)
        .attr('name', options.name)
        .attr('type', options.type)
        .attr('min', options.min)
        .attr('max', options.max)
        .attr('value', options.value)
        .attr('step', options.step)
        .property('disabled', options.disabled)
        .property('readOnly', options.readOnly);

    return inputD3.node();
  }

  /**
   * @private 
   * Create a slider in the control panel that based from a input form.
   * 
   * @param {SliderOptions} sliderOptions The slider options.
   * @returns {HTMLElement} The slider element.
   */
  _createInputSlider(sliderOptions) {
    let options = $.extend({}, this.sliderOptions, sliderOptions);

    let sliderD3 = d3.select(options.appendTo)
        .append('div')
        .attr('class', options.sliderColSize)
        .append('input')
        .attr('class', 'control-panel-slider')
        .attr('id', options.id)
        .attr('type', 'range')
        .attr('min', options.inputEl.min)
        .attr('max', options.inputEl.max)
        .attr('value', options.inputEl.value)
        .attr('step', options.inputEl.step);

    return sliderD3.node();
  }

  /**
   * @private 
   * Create a select menu in the control panel.
   * 
   * @param {SelectOptions} selectOptions The select menu options.
   * @param {Array<HTMLElement>} optionArray The option elements.
   * @returns {HTMLElement} The select menu element.
   */
  _createSelect(optionArray, selectOptions) {
    let options = $.extend({}, this.selectOptions, selectOptions);

    if (options.addLabel) {
      this.createLabel({
        appendTo: options.appendTo, 
        label: options.label, 
        labelControl: options.labelControl,
        labelFor: options.id,
        labelColSize: options.labelColSize});
      
      if (options.labelBreak) {
        d3.select(options.appendTo)
            .append('br');
      }
    }

    let selectD3 = d3.select(options.appendTo)
        .append('select')
        .attr('class', 'form-control')
        .attr('id', options.id)
        .attr('name', options.name)
        .property('multiple', options.multiple)
        .attr('size', options.size);
    
    if (options.size > 1) {
      selectD3.style('height', 'auto');
    }

    let selectEl = selectD3.node();
    selectEl.value = options.value;
    this.updateSelectOptions(selectEl, optionArray);
    
    return selectEl;
  }

  /**
   * @private 
   * Add a input form addon to the input form.
   * 
   * @param {HTMLElement} intputEl The input form element to add the addon.
   * @param {String} text The text to add to the input addon.
   */
  _inputAddon(inputEl, text) {
    d3.select(inputEl.parentNode)
        .append('span')
        .attr('class', 'input-group-addon')
        .html(text);
  }

  /**
   * @private 
   * Sync values between an input form, slider, and button group.
   * 
   * If a button is clicked the input form and slider are updated. 
   * 
   * If the input form is updated then the slider is updated and if
   *    a button has that value it is activated.
   * 
   * If the slider is updated the input form is updated and if a
   *    button has that value it is activated.
   * 
   * A minumum of two of the elements must be defined. 
   * 
   * @param {HTMLElement} inputEl The input element.
   * @param {HTMLElement=} btnGroupEl Optional button group element.
   * @param {HTMLElement=} sliderEl Optional slider element.
   */
  _syncValues(inputEl, btnGroupEl = undefined, sliderEl = undefined) {
    $(inputEl).on('input', (event) => {
      if (sliderEl) sliderEl.value = event.target.value;
      if (btnGroupEl) this._findBtnGroupValue(btnGroupEl, event.target.value);
    });

    if (btnGroupEl) {
      $('input', btnGroupEl).on('change', (event) => {
        if (inputEl.disabled) return;
        let val = event.target.value;
        inputEl.value = val;
        $(inputEl).trigger('input');
        if (sliderEl) sliderEl.value = val; 
        Tools.resetRadioButton(event.target); 
      });
    }

    if (sliderEl) {
      $(sliderEl).on('input', (event) => {
        inputEl.value = event.target.value;
        $(inputEl).trigger('input');
        if (btnGroupEl) this._findBtnGroupValue(btnGroupEl, event.target.value);
      });
    }

    $(inputEl).trigger('input');
  }

  /**
   * @private 
   * Find a button with a specific value and activate that button.
   * 
   * @param {HTMLElement} btnGroupEl The button group element.
   * @param {String | Number} value The value to find.
   */
  _findBtnGroupValue(btnGroupEl, value) {
    d3.select(btnGroupEl)
        .selectAll('label')
        .classed('active', false);

    let el = $('input', btnGroupEl).filter((i, el) => {
      return parseFloat(el.value) == parseFloat(value); 
    });

    if (el[0] != undefined) {
      d3.select(el[0].parentNode).classed('active', true);
    }
  }

}