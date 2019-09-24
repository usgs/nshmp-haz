
import { D3LineView } from '../../../apps/js/d3/view/D3LineView.js';
import { D3LinePlot } from '../../../apps/js/d3/D3LinePlot.js';
import { D3LineData } from '../../../apps/js/d3/data/D3LineData.js';
import { D3LineSubView } from '../../../apps/js/d3/view/D3LineSubView.js';
import { D3LineSubViewOptions } from '../../../apps/js/d3/options/D3LineSubViewOptions.js';

import ControlPanel from '../../../apps/js/lib/ControlPanel.js';
import { Dashboard } from '../Dashboard.js';
import Header from '../../../apps/js/lib/Header.js';
import Footer from '../../../apps/js/lib/Footer.js';

/**
 * @fileoverview This is an example of plotting a simple
 *    line graph using the d3 package.
 * 
 * @class D3BasicLinePlot
 * @author Brandon Clayton
 */
export class D3BasicLinePlot {

  constructor() {
    /* Create the footer */
    let footer = new Footer();
    footer.removeButtons();
    footer.removeInfoIcon();

    /* Create the header */
    let header = new Header();
    header.setTitle('D3 Simple Line Plot');
    header.setCustomMenu(Dashboard.headerMenuItems());

    /* Create a control panel */
    let controlPanel = new ControlPanel();

    /* Container for plot(s) */
    let containerEl = document.querySelector('#content');

    /* Create the line view */
    let lineView = this.createLineView(containerEl);

    /* Set the plot title */
    lineView.setTitle('Simple Line Plot');

    /* Get the sub view that will have the plot on it */
    let upperSubView = lineView.upperSubView;

    /* Plot the data */
    this.plot(lineView, upperSubView);
  }

  /**
   * Create the view.
   * 
   * @param {HTMLElement} containerEl The container element
   */
  createLineView(containerEl) {
    /* Create the upper sub view options */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .xLabel('X')
        .yLabel('Y')
        .filename('upper-line-plot')
        .build();

    /* Create the line view */
    let lineView = D3LineView.builder()
        .containerEl(containerEl)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    return lineView;
  }

  /**
   * Plot some data.
   * 
   * @param {D3LineView} lineView The view
   * @param {D3LineSubView} subView The sub view 
   */
  plot(lineView, subView) {
    /* Create a new line plot */
    let linePlot = new D3LinePlot(lineView);

    /* Create the data to plot */
    let data = D3LineData.builder()
        .subView(subView)
        .data([ 2, 4, 6, 8 ], [ 4, 9, 25, 15 ])
        .data([ 2, 4, 6, 8 ], [ 5, 10, 8, 3 ])
        .data([ 2, 4, 6, 8 ], [ 2, 12, 15, 5 ])
        .build();

    /* Plot the data */
    linePlot.plot(data);

    /* Set the data that will be saved */
    lineView.setSaveData(data);

    /* Create a data table in the 'Data' view */
    lineView.createDataTable(data);
   
    /* Create metadata to be shown in the 'Metadata' view */
    let metadata = new Map();
    metadata.set('This is some metadata', ['Some value', 'Another value']);

    /* Set the metadata */
    lineView.setMetadata(metadata);

    /* Create the metadata table */
    lineView.createMetadataTable();
  }

}
