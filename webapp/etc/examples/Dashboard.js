
import Footer from '../../apps/js/lib/Footer.js';
import Header from '../../apps/js/lib/Header.js';

/**
 * @fileoverview The dashboard for the examples
 * 
 * @class Dashboard
 * @author Brandon Clayton
 */
export class Dashboard {
  
  constructor() {
    let footer = new Footer()
    footer.removeButtons();
    footer.removeInfoIcon();

    let header = new Header();
    header.setTitle('Examples Dashboard');
    header.setCustomMenu(Dashboard.headerMenuItems());
 
    let examples = [
      {
        label: 'D3 Basic Line Plot', 
        href: 'd3/d3-basic-line-plot.html',
      }, {
        label: 'D3 Custom Line Plot', 
        href: 'd3/d3-custom-line-plot.html',
      },
    ];
    
    this.createDashboard(examples);
  }

  /**
   * Header menu items
   */
  static headerMenuItems() {
    let menuItems = [
      { 
        label: 'Dashboard', 
        href: '/nshmp-haz-ws/etc/examples',
      }, {
        label: 'D3 Basic Line Plot', 
        href: '/nshmp-haz-ws/etc/examples/d3/d3-basic-line-plot.html',
      }, {
        label: 'D3 Custom Line Plot', 
        href: '/nshmp-haz-ws/etc/examples/d3/d3-custom-line-plot.html',
      },
    ];

    return menuItems;
  }

  /**
   * Create the dashboard
   */
  createDashboard(examples) {
    let elD3 = d3.select('body')
        .append('div')
        .attr('id', 'container')
        .append('div')
        .attr('id', 'dash');
  
    elD3.selectAll('div')
        .data(examples)
        .enter()
        .append('div')
        .attr('class', 'col-sm-offset-3 col-sm-6')
        .on('click', (d) => { window.location = d.href; })
        .append('div')
        .attr('class', 'panel panel-default')
        .append('div')
        .attr('class', 'panel-heading')
        .append('h2')
        .attr('class', 'panel-title')
        .text((d) => { return d.label; });
  }

}
