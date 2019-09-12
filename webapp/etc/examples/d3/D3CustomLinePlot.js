
import { D3LineView } from '../../../apps/js/d3/view/D3LineView.js';
import { D3LinePlot } from '../../../apps/js/d3/D3LinePlot.js';
import { D3LineData } from '../../../apps/js/d3/data/D3LineData.js';
import { D3LineSubView } from '../../../apps/js/d3/view/D3LineSubView.js';

import { D3LineSubViewOptions } from '../../../apps/js/d3/options/D3LineSubViewOptions.js';
import { D3LineOptions } from '../../../apps/js/d3/options/D3LineOptions.js';

import ControlPanel from '../../../apps/js/lib/ControlPanel.js';
import { Dashboard } from '../Dashboard.js';
import Header from '../../../apps/js/lib/Header.js';
import Footer from '../../../apps/js/lib/Footer.js';

/**
 * @fileoverview This is an example of plotting a custom
 *    line graph using the d3 package.
 * 
 * @class D3CustomLinePlot
 * @author Brandon Clayton
 */
export class D3CustomLinePlot {

  constructor() {
    /* Create the footer */
    let footer = new Footer();
    footer.removeButtons();
    footer.removeInfoIcon();

    /* Create the header */
    let header = new Header();
    header.setTitle('D3 Custom Line Plot');
    header.setCustomMenu(Dashboard.headerMenuItems());

    /* Create a control panel */
    let controlPanel = new ControlPanel();

    /* Container for plot(s) */
    let containerEl = document.querySelector('#content');

    /* Create the line view */
    let lineView = this.createLineView(containerEl);

    /* Set the plot title */
    lineView.setTitle('Custom Line Plot');

    /* Create the line plot */
    let linePlot = new D3LinePlot(lineView);

    /* Plot the data in the upper sub view */
    let upperSubViewData = this.plotUpperSubView(linePlot, lineView);

    /* Plot the data in the lower sub view */
    let lowerSubViewData = this.plotLowerSubView(linePlot, lineView);

    /* Set the data that will be saved */
    lineView.setSaveData(upperSubViewData, lowerSubViewData);

    /* Create a data table in the 'Data' view */
    lineView.createDataTable(upperSubViewData, lowerSubViewData);
   
    /* Create metadata to be shown in the 'Metadata' view */
    let metadata = new Map();
    metadata.set('This is some metadata', ['Some value', 'Another value']);

    /* Set the metadata */
    lineView.setMetadata(metadata);

    /* Create the metadata table */
    lineView.createMetadataTable();

  }

  /**
   * Create the view.
   * 
   * @param {HTMLElement} containerEl The container element
   */
  createLineView(containerEl) {
    /* Create the lower sub view options */
    let lowerSubViewOptions = D3LineSubViewOptions.lowerBuilder()
        .xLabel('X')
        .yLabel('Y')
        .filename('lower-line-plot')
        .build();

    /* Create the upper sub view options */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .xLabel('X')
        .yLabel('Y')
        .filename('upper-line-plot')
        .build();

    /* Create the line view */
    let lineView = D3LineView.builder()
        .addLowerSubView(true)
        .containerEl(containerEl)
        .lowerSubViewOptions(lowerSubViewOptions)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    return lineView;
  }

  /**
   * Create the data with custom line options for the lower sub view plot.
   *  
   * @param {D3LineSubView} lowerSubView The lower sub view
   */
  createLowerSubViewData(lowerSubView) {
    /* Create data for the lower sub view */
    let data = D3LineData.builder()
        .subView(lowerSubView)
        .data([ 0, 2, 4, 6 ], [ -5, 2, -1, 8 ])
        .data([ 0, 2, 4, 6 ], [ 5, -4, 8, -2 ])
        .build();

    return data;
  }

  /**
   * Create the data with custom line options for the upper sub view plot.
   *  
   * @param {D3LineSubView} upperSubView The upper sub view
   */
  createUpperSubViewData(upperSubView) {
    /**
     * Create custom line options with:
     *    - Star markers
     *    - Black marker edges
     *    - Marker size of 12
     */
    let lineOptions1 = D3LineOptions.builder()
        .label('Custom Line 1')
        .markerStyle('*')
        .markerEdgeColor('black')
        .markerSize(12)
        .build();

    /**
     * Create custom line options with:
     *    - Dashed line style
     *    - Square markers
     */
    let lineOptions2 = D3LineOptions.builder()
        .label('Custom Line 2')
        .lineStyle('--')
        .markerStyle('s')
        .build();

    /**
     * Create custom line options with:
     *    - Dotted line style
     *    - Cross markers
     */
    let lineOptions3 = D3LineOptions.builder()
        .label('Custom Line 3')
        .lineStyle(':')
        .markerStyle('x')
        .build();

    /* Create data for the upper sub view */
    let data = D3LineData.builder()
        .subView(upperSubView)
        .data([ 2, 4, 6, 8 ], [ 4, 9, 25, 15 ], lineOptions1)
        .data([ 2, 4, 6, 8 ], [ 5, 10, 8, 3 ], lineOptions2)
        .data([ 2, 4, 6, 8 ], [ 2, 12, 15, 5 ], lineOptions3)
        .build();

    return data;
  }

  /**
   * Plot some data.
   *
   * @param {D3LinePlot} linePlot 
   * @param {D3LineView} lineView The view
   */
  plotLowerSubView(linePlot, lineView) {
    /* Get the lower sub view */ 
    let subView = lineView.lowerSubView;

    /* Create the data to plot */
    let data = this.createLowerSubViewData(subView);

    /* Plot the data */
    linePlot.plot(data);

    /* Plot a reference line at y=0 */
    linePlot.plotZeroRefLine(subView);

    return data;
  }

  /**
   * Plot some data.
   *
   * @param {D3LinePlot} linePlot 
   * @param {D3LineView} lineView The view
   */
  plotUpperSubView(linePlot, lineView) {
    /* Get the upper sub view */ 
    let subView = lineView.upperSubView;

    /* Create the data to plot */
    let data = this.createUpperSubViewData(subView);

    /* Plot the data */
    linePlot.plot(data);

    /* Add a reference line at y=5 */
    let yRef = 5;
    let refLineEl = linePlot.plotHorizontalRefLine(subView, yRef);

    /* Add text above reference line */
    let textEl = linePlot.addText(
        subView,
        4,
        yRef + 0.5,
        `This line is draggable: y=${yRef}`);

    /* Y limits for a dragging the reference line */
    let yLimits = [ 4, 20 ];

    /**
     * Make the reference line draggable.
     * This method takes an optional callback function that is
     *    called when the line is dragged with arguments:
     *        - Number: the current Y value
     *        - D3LineSeriesData: the line series data
     *        - SVGElement: the element being dragged
     */
    linePlot.makeDraggableInY(subView, refLineEl, yLimits, (y) => {
      /* Move the text as the line drags */
      linePlot.moveText(subView, 4, y + 0.5, textEl);

      /* Update the text as the line drags */
      linePlot.updateText(textEl, `This line is draggable: y=${y}`);
    });

    return data;
  }

}
