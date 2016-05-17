package org.opensha2.geo;

import java.awt.Color;
import java.awt.geom.Area;
import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha2.eq.fault.surface.GriddedSurface;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Region export utilities.
 * 
 * @author Peter Powers
 */
public class RegionUtils {

  // TODO clean and cull

  private static final String NL = System.getProperty("line.separator");

  enum Style {
    BORDER,
    BORDER_VERTEX,
    GRID_NODE;
  }

  // write region
  public static void regionToKML(Region region, String filename, Color c)
      throws ParserConfigurationException,
      TransformerConfigurationException, TransformerException {
    String kmlFileName = filename + ".kml";

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.newDocument();
    Element root = doc.createElementNS("http://www.opengis.net/kml/2.2", "kml");
    doc.appendChild(root);

    Element e_doc = addElement("Document", root);
    Element e_doc_name = addElement("name", e_doc);
    e_doc_name.setTextContent(kmlFileName);

    addBorderStyle(e_doc, c);
    addBorderVertexStyle(e_doc);
    addGridNodeStyle(e_doc, c);

    Element e_folder = addElement("Folder", e_doc);
    Element e_folder_name = addElement("name", e_folder);
    e_folder_name.setTextContent("region");
    Element e_open = addElement("open", e_folder);
    e_open.setTextContent("1");

    addBorder(e_folder, region);
    addPoints(e_folder, "Border Nodes", region.border(), Style.BORDER_VERTEX);
    if (region.interiors() != null) {
      for (LocationList interior : region.interiors()) {
        addPoints(e_folder, "Interior Nodes", interior,
          Style.BORDER_VERTEX);
      }
    }

    if (region instanceof GriddedRegion) {
      addPoints(e_folder, "Grid Nodes", ((GriddedRegion) region).nodes(), Style.GRID_NODE);
    }

    // TODO absolutely need to create seom platform specific output
    // directory
    // that is not in project space (e.g. desktop, Decs and Settings);

    String outDirName = "tmp/sha_kml/";
    File outDir = new File(outDirName);
    outDir.mkdirs();
    String tmpFile = outDirName + kmlFileName;

    // write the content into xml file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer trans = transformerFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(tmpFile);
    trans.transform(source, result);
  }

  // write region
  public static void locListToKML(
      LocationList locs, String filename, Color c)
      throws ParserConfigurationException,
      TransformerConfigurationException, TransformerException {
    String kmlFileName = filename + ".kml";

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.newDocument();
    Element root = doc.createElementNS("http://www.opengis.net/kml/2.2", "kml");
    doc.appendChild(root);

    Element e_doc = addElement("Document", root);
    Element e_doc_name = addElement("name", e_doc);
    e_doc_name.setTextContent(kmlFileName);

    addBorderStyle(e_doc, c);
    addBorderVertexStyle(e_doc);
    addGridNodeStyle(e_doc, c);

    Element e_folder = addElement("Folder", e_doc);
    Element e_folder_name = addElement("name", e_folder);
    e_folder_name.setTextContent("region");
    Element e_open = addElement("open", e_folder);
    e_open.setTextContent("1");

    // addLocationPoly(e_folder, locs);
    addLocationLine(e_folder, locs);
    addPoints(e_folder, "Border Nodes", locs, Style.BORDER_VERTEX);

    // TODO absolutely need to create seom platform specific output
    // directory
    // that is not in project space (e.g. desktop, Decs and Settings);

    String outDirName = "tmp/sha_kml/";
    File outDir = new File(outDirName);
    outDir.mkdirs();
    String tmpFile = outDirName + kmlFileName;

    // write the content into xml file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer trans = transformerFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(tmpFile);
    trans.transform(source, result);
  }

  // border polygon
  private static Element addBorder(Element e, Region region) {
    Element e_placemark = addElement("Placemark", e);
    Element e_name = addElement("name", e_placemark);
    e_name.setTextContent("Border");
    Element e_style = addElement("styleUrl", e_placemark);
    e_style.setTextContent("#" + Style.BORDER.toString());
    Element e_poly = addElement("Polygon", e_placemark);
    Element e_tessellate = addElement("tessellate", e_poly);
    e_tessellate.setTextContent("1");

    addPoly(e_poly, "outerBoundaryIs", region.border());
    if (region.interiors() != null) {
      for (LocationList interior : region.interiors()) {
        addPoly(e_poly, "innerBoundaryIs", interior);
      }
    }

    return e;
  }

  // standalone location list
  private static Element addLocationPoly(Element e, LocationList locs) {
    Element e_placemark = addElement("Placemark", e);
    Element e_name = addElement("name", e_placemark);
    e_name.setTextContent("Border");
    Element e_style = addElement("styleUrl", e_placemark);
    e_style.setTextContent("#" + Style.BORDER.toString());
    Element e_poly = addElement("Polygon", e_placemark);
    Element e_tessellate = addElement("tessellate", e_poly);
    e_tessellate.setTextContent("1");

    addPoly(e_poly, "outerBoundaryIs", locs);
    return e;
  }

  // create a closed polygon Element from a LocationList
  private static Element addPoly(
      Element e,
      String polyName,
      LocationList locs) {

    Element e_BI = addElement(polyName, e);
    Element e_LR = addElement("LinearRing", e_BI);
    Element e_coord = addElement("coordinates", e_LR);
    String locStr = locs.toString() + locs.get(0).toString() + NL;
    e_coord.setTextContent(locStr);

    return e;
  }

  // standalone location list
  private static Element addLocationLine(Element e, LocationList locs) {
    Element e_placemark = addElement("Placemark", e);
    Element e_name = addElement("name", e_placemark);
    e_name.setTextContent("Trace");
    Element e_style = addElement("styleUrl", e_placemark);
    e_style.setTextContent("#" + Style.BORDER.toString());
    Element e_line = addElement("LineString", e_placemark);
    Element e_tessellate = addElement("tessellate", e_line);
    e_tessellate.setTextContent("1");
    Element e_coord = addElement("coordinates", e_line);
    e_coord.setTextContent(locs.toString());
    return e;
  }

  // // create lat-lon data string
  // private static String parseBorderCoords(Region region) {
  // LocationList ll = region.getBorder();
  // StringBuffer sb = new StringBuffer(NL);
  // //System.out.println("parseBorderCoords: "); // TODO clean
  // for (Location loc: ll) {
  // sb.append(loc.toKML() + NL);
  // //System.out.println(loc.toKML()); // TODO clean
  // }
  // // region borders do not repeat the first
  // // vertex, but kml closed polygons do
  // sb.append(ll.getLocationAt(0).toKML() + NL);
  // //System.out.println("---"); // TODO clean
  // return sb.toString();
  // }

  // node placemarks
  private static Element addPoints(
      Element e, String folderName,
      LocationList locations, Style style) {
    Element e_folder = addElement("Folder", e);
    Element e_folder_name = addElement("name", e_folder);
    e_folder_name.setTextContent(folderName);
    Element e_open = addElement("open", e_folder);
    e_open.setTextContent("0");
    // loop nodes
    for (Location loc : locations) {
      Element e_placemark = addElement("Placemark", e_folder);
      Element e_style = addElement("styleUrl", e_placemark);
      e_style.setTextContent("#" + style.toString());
      Element e_poly = addElement("Point", e_placemark);
      Element e_coord = addElement("coordinates", e_poly);
      // System.out.println(loc.toKML()); // TODO clean
      e_coord.setTextContent(loc.toString());
    }
    return e;
  }

  // <Folder>
  // <name>region</name>
  // <open>1</open>
  // <Placemark>
  // <name>test region</name>
  // <styleUrl>#msn_ylw-pushpin</styleUrl>
  // <Polygon>
  // <tessellate>1</tessellate>
  // <outerBoundaryIs>
  // <LinearRing>
  // <coordinates>
  // -118.494013126698,34.12890715714403,0
  // -118.2726369206852,34.02666906748863,0
  // -117.9627114364491,34.07186823617815,0
  // -117.9620310910423,34.2668764027905,0
  // -118.3264939969918,34.39919060861001,0
  // -118.5320559633752,34.23801999324961,0
  // -118.494013126698,34.12890715714403,0 </coordinates>
  // </LinearRing>
  // </outerBoundaryIs>

  // <innerBoundaryIs>
  // <LinearRing>
  // <coordinates>
  // -122.366212,37.818977,30
  // -122.365424,37.819294,30
  // -122.365704,37.819731,30
  // -122.366488,37.819402,30
  // -122.366212,37.818977,30
  // </coordinates>
  // </LinearRing>
  // </innerBoundaryIs>

  // </Polygon>
  // </Placemark>
  // <Placemark>
  // <LookAt>
  // <longitude>-118.247043582626</longitude>
  // <latitude>34.21293007086929</latitude>
  // <altitude>0</altitude>
  // <range>60381.34272309824</range>
  // <tilt>0</tilt>
  // <heading>-3.115946006858405e-08</heading>
  // <altitudeMode>relativeToGround</altitudeMode>
  // </LookAt>
  // <styleUrl>#msn_placemark_circle</styleUrl>
  // <Point>
  // <coordinates>-118.3897877691312,34.24834787236836,0</coordinates>
  // </Point>
  // </Placemark>
  // </Folder>

  // <innerBoundaryIs>
  // <LinearRing>
  // <coordinates>
  // -122.366212593918,37.81897719083808,30
  // -122.3654241733188,37.81929450992014,30
  // -122.3657048517827,37.81973175302663,30
  // -122.3664882465854,37.81940249291773,30
  // -122.366212593918,37.81897719083808,30
  // </coordinates>
  // </LinearRing>
  // </innerBoundaryIs>

  // border style elements
  private static Element addBorderStyle(Element e, Color c) {
    Element e_style = addElement("Style", e);
    e_style.setAttribute("id", Style.BORDER.toString());

    // line style
    Element e_lineStyle = addElement("LineStyle", e_style);
    Element e_color = addElement("color", e_lineStyle);
    e_color.setTextContent(colorToHex(c));
    Element e_width = addElement("width", e_lineStyle);
    e_width.setTextContent("3");

    // poly style
    Element e_polyStyle = addElement("PolyStyle", e_style);
    e_polyStyle.appendChild((Element) e_color.cloneNode(true));
    Element e_fill = addElement("fill", e_polyStyle);
    e_fill.setTextContent("0");

    return e;
  }

  // border vertex style elements
  private static Element addBorderVertexStyle(Element e) {
    Element e_style = addElement("Style", e);
    e_style.setAttribute("id", Style.BORDER_VERTEX.toString());

    // icon style
    Element e_iconStyle = addElement("IconStyle", e_style);
    Element e_color = addElement("color", e_iconStyle);
    e_color.setTextContent(colorToHex(Color.RED));
    Element e_scale = addElement("scale", e_iconStyle);
    e_scale.setTextContent("0.6");
    Element e_icon = addElement("Icon", e_iconStyle);
    Element e_href = addElement("href", e_icon);
    e_href.setTextContent("http://maps.google.com/mapfiles/kml/shapes/open-diamond.png");
    return e;
  }

  // node style elements
  private static Element addGridNodeStyle(Element e, Color c) {
    Element e_style = addElement("Style", e);
    e_style.setAttribute("id", Style.GRID_NODE.toString());

    // icon style
    Element e_iconStyle = addElement("IconStyle", e_style);
    Element e_color = addElement("color", e_iconStyle);
    e_color.setTextContent(colorToHex(c));
    Element e_scale = addElement("scale", e_iconStyle);
    e_scale.setTextContent("0.6");
    Element e_icon = addElement("Icon", e_iconStyle);
    Element e_href = addElement("href", e_icon);
    e_href.setTextContent("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png");
    return e;
  }

  private static Element addElement(String name, Element parent) {
    Element e = parent.getOwnerDocument().createElement(name);
    parent.appendChild(e);
    return e;
  }

  // converts Color to KML compatible ABGR hex value
  private static String colorToHex(Color c) {
    StringBuffer sb = new StringBuffer();
    sb.append(toHex(c.getAlpha()));
    sb.append(toHex(c.getBlue()));
    sb.append(toHex(c.getGreen()));
    sb.append(toHex(c.getRed()));
    return sb.toString();
  }

  // converts ints to hex values, padding single digits as necessary
  private static String toHex(int i) {
    return Strings.padStart(Integer.toHexString(i), 2, '0');
  }

  /**
   * The returns the fraction of points in the given collection of locations
   * that is inside the given region. This will commonly be used with the
   * {@link GriddedSurface}s to determine the fraction of a fault surface that
   * is inside of a region.
   * 
   * @param region the region for which to test
   * @param locs any instance of Iterable<Location>, for example,
   *        ArrayList<Location> or GriddedSurface.
   * @return fraction of locations inside the given region
   * @throws NullPointerException if region or locs is null
   * @throws IllegalArgumentException if locs is empty
   */
  public static double getFractionInside(Region region, Iterable<Location> locs)
      throws NullPointerException, IllegalArgumentException {
    Preconditions.checkNotNull(region, "region cannot be null");
    Preconditions.checkNotNull(locs, "locations cannot be null");
    int numInside = 0;
    int cnt = 0;
    for (Location loc : locs) {
      if (region.contains(loc))
        numInside++;
      cnt++;
    }
    Preconditions.checkArgument(cnt > 0, "locs must contain at least one location!");
    return (double) numInside / (double) cnt;
  }

  /**
   * Returns the {@code Region} spanned by a node centered at the supplied
   * location with the given width and height.
   * @param p {@code Location} at center of a grid node
   * @param w node width
   * @param h node height
   * @return the node's {@code Region}
   */
  public static Area getNodeShape(Location p, double w, double h) {
    double halfW = w / 2;
    double halfH = h / 2;
    double nodeLat = p.lat();
    double nodeLon = p.lon();
    LocationList locs = LocationList.create(
      Location.create(nodeLat + halfH, nodeLon + halfW), // top right
      Location.create(nodeLat - halfH, nodeLon + halfW), // bot right
      Location.create(nodeLat - halfH, nodeLon - halfW), // bot left
      Location.create(nodeLat + halfH, nodeLon - halfW)); // top left
    return new Area(Locations.toPath(locs));
  }

  // private String convertLocations(LocationList ll) {
  //
  // }

  public static void main(String[] args) throws Exception {

    // LocationList ll = LocationList.create(
    // Location.create(35,-125),
    // Location.create(38,-117),
    // Location.create(37,-109),
    // Location.create(41,-95));
    //
    // locListToKML(ll, "Sausage", Color.ORANGE);

    // // visual verification tests for GeographiRegionTest
    // Region gr;
    //
    // Location L1 = new Location(32,112);
    // Location L3 = new Location(34,118);
    // gr = new Region(L1,L3);
    // RegionUtils.regionToKML(gr, "RegionLocLoc", Color.ORANGE);
    //

    // GriddedRegion rect_gr =
    // new GriddedRegion(
    // new Location(40.0,-113),
    // new Location(42.0,-117),
    // 0.2,null);
    // KML.regionToKML(
    // rect_gr,
    // "RECT_REGION2",
    // Color.ORANGE);

    // GriddedRegion relm_gr = new CaliforniaRegions.RELM_TESTING_GRIDDED();
    // KML.regionToKML(
    // relm_gr,
    // "RELM_TESTanchor",
    // Color.ORANGE);

    // System.out.println(relm_gr.getMinLat());
    // System.out.println(relm_gr.getMinGridLat());
    // System.out.println(relm_gr.getMinLon());
    // System.out.println(relm_gr.getMinGridLon());
    // System.out.println(relm_gr.getMaxLat());
    // System.out.println(relm_gr.getMaxGridLat());
    // System.out.println(relm_gr.getMaxLon());
    // System.out.println(relm_gr.getMaxGridLon());

    // GriddedRegion eggr1 = new CaliforniaRegions.WG02_GRIDDED();
    // KML.regionToKML(
    // eggr1,
    // "WG02anchor",
    // Color.ORANGE);

    // GriddedRegion eggr2 = new CaliforniaRegions.WG07_GRIDDED();
    // KML.regionToKML(
    // eggr2,
    // "WG07anchor",
    // Color.ORANGE);

    // TODO test that borders for diff constructors end up the same.

    // test mercator/great-circle region
    // GriddedRegion eggr3 = new GriddedRegion(
    // new Location(35,-125),
    // new Location(45,-90),
    // 0.5);
    // KML.regionToKML(
    // eggr3,
    // "TEST1_box",
    // Color.ORANGE);

    // SAUSAGE
    LocationList ll = LocationList.create(
      Location.create(35, -125),
      Location.create(38, -117),
      Location.create(37, -109),
      Location.create(41, -95));

    Region sausage = Regions.createBuffered("Test Buffer", ll, 100.0);
    // regionToKML(sausage, "Sausage", Color.ORANGE);
    locListToKML(ll, "Sausage", Color.ORANGE);

    // GriddedRegion sausageAnchor =
    // new GriddedRegion(ll,100,0.5,new Location(0,0));
    // KML.regionToKML(
    // sausageAnchor,
    // "SausageAnchor",
    // Color.BLUE);

    // CIRCLE
    // Location loc = new Location(35, -125);
    // GriddedRegion circle =
    // new GriddedRegion(loc, 400, 0.2, null);
    // KML.regionToKML(circle, "Circle", Color.ORANGE);
    //
    // GriddedRegion circleAnchor =
    // new GriddedRegion(loc, 400, 0.2, new Location(0,0));
    // KML.regionToKML(circleAnchor, "CircleAnchor", Color.BLUE);

    //
    // GriddedRegion eggr4 = new GriddedRegion(
    // ll,BorderType.MERCATOR_LINEAR,0.5);
    // KML.regionToKML(
    // eggr4,
    // "TEST1_loclist_lin",
    // Color.ORANGE);
    //
    //
    // GriddedRegion eggr5 = new GriddedRegion(
    // ll,BorderType.GREAT_CIRCLE,0.5);
    // KML.regionToKML(
    // eggr5,
    // "TEST1_loclist_gc",
    // Color.ORANGE);

  }
}
