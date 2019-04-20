import {Inject, Injectable, PLATFORM_ID} from '@angular/core';
import * as mxEditor from 'mxgraph';
import * as mxGraph from 'mxgraph';
import * as mxDragSource from 'mxgraph';
import {GlyphInfo} from './glyphInfo';
import {MetadataService} from './metadata.service';
import {isPlatformBrowser} from '@angular/common';

declare var require: any;
const mx = require('mxgraph')({
  mxImageBasePath: 'mxgraph/images',
  mxBasePath: 'mxgraph'
});

// Constants (there is no doubt a better way to do this
const glyphWidth = 52;
const glyphHeight = 104;

const portWidth = 10;

@Injectable({
  providedIn: 'root'
})
export class GraphService {

  graph: mxGraph;
  editor: mxEditor;
  graphContainer: HTMLElement;
  glyphDragPreviewElt: HTMLElement;

  baseGlyphStyle;

  constructor(private metadataService: MetadataService) {

    this.graphContainer = document.createElement('div');
    this.graphContainer.id = 'graphContainer';
    this.graphContainer.style.margin = 'auto';
    this.graphContainer.style.background = 'url(assets/grid.png)';
    this.graphContainer.style.position = 'absolute';
    this.graphContainer.style.top = '0';
    this.graphContainer.style.bottom = '0';
    this.graphContainer.style.left = '0';
    this.graphContainer.style.right = '0';

    mx.mxGraphHandler.prototype.guidesEnabled = true;

    // mxEditor is kind of a parent to mxGraph
    // it's used mainly for 'actions', which for now means delete,
    // later will mean undoing
    this.editor = new mx.mxEditor();
    this.graph = this.editor.graph;
    this.editor.setGraphContainer(this.graphContainer);

    this.graph.setConnectable(true);
    this.graph.allowDanglingEdges = false;

    // Enables rubberband selection
    // tslint:disable-next-line:no-unused-expression
    new mx.mxRubberband(this.graph);

    // Sets the graph container and configures the editor

    // without this, an option appears to collapse glyphs, which hides their ports
    this.graph.isCellFoldable = function(cell) {
      return false;
    };

    // Add event listeners to the graph. NOTE: MUST USE THE '=>' WAY FOR THIS TO WORK.
    this.graph.addListener(mx.mxEvent.CLICK, (sender, event) => this.handleClickEvent(sender, event));

    // Ports are not used as terminals for edges, they are
    // only used to compute the graphical connection point
    this.graph.isPort = function(cell) {
      // 'this' is the mxGraph, not the GraphService
      const geo = this.getCellGeometry(cell);
      return (geo != null) ? geo.relative : false;
    };

    // A dummy element used for previewing glyphs as they are dragged onto the graph
    this.glyphDragPreviewElt = document.createElement('div');
    this.glyphDragPreviewElt.style.border = 'dashed black 1px';
    this.glyphDragPreviewElt.style.width = glyphWidth + 'px';
    this.glyphDragPreviewElt.style.height = glyphHeight + 'px';

    this.baseGlyphStyle = {};
    this.baseGlyphStyle[mx.mxConstants.STYLE_SHAPE] = mx.mxConstants.SHAPE_LABEL;
    this.baseGlyphStyle[mx.mxConstants.STYLE_FONTCOLOR] = '#FFFFFF';
    this.baseGlyphStyle[mx.mxConstants.STYLE_IMAGE_ALIGN] = mx.mxConstants.ALIGN_CENTER;
    this.baseGlyphStyle[mx.mxConstants.STYLE_IMAGE_VERTICAL_ALIGN] = mx.mxConstants.ALIGN_TOP;
    this.baseGlyphStyle[mx.mxConstants.STYLE_IMAGE_WIDTH] = String(glyphWidth);
    this.baseGlyphStyle[mx.mxConstants.STYLE_IMAGE_HEIGHT] = String(glyphHeight);
    this.baseGlyphStyle[mx.mxConstants.STYLE_RESIZABLE] = 0;

    const style = this.graph.getStylesheet().getDefaultEdgeStyle();
    style[mx.mxConstants.STYLE_ROUNDED] = true;
    style[mx.mxConstants.STYLE_EDGE] = mx.mxEdgeStyle.ElbowConnector;
  }

  /**
   * Returns the <div> that this graph displays to
   */
  getGraphDOM() {
    return this.graphContainer;
  }

  /**
   * Deletes the currently selected cell
   */
  delete() {
    this.editor.execute('delete');
  }

  /**
   * Makes the given element draggable in mxGraph
   */
  makeElementDraggable(element) {
    element.width = glyphWidth;
    element.height = glyphHeight;

    const newGlyphStyle = mx.mxUtils.clone(this.baseGlyphStyle);
    newGlyphStyle[mx.mxConstants.STYLE_IMAGE] = element.src;
    const styleName = 'cellStyle:' + element.src;
    this.graph.getStylesheet().putCellStyle(styleName, newGlyphStyle);

    const insertGlyph = function(graph, evt, target, x, y) {
      // When executed, 'this' is the dragSource, not the graphService

      graph.getModel().beginUpdate();
      try {
        const glyphCell = graph.insertVertex(graph.getDefaultParent(), null, '', x, y, glyphWidth, glyphHeight, styleName+";fillColor=#ffffff;");
        glyphCell.setConnectable(false);
        glyphCell.data = new GlyphInfo();

        const leftPort = graph.insertVertex(glyphCell, null, '', 1, .5, portWidth, portWidth);
        leftPort.geometry.offset = new mx.mxPoint(-1 * portWidth / 2, -1 * portWidth / 2);
        leftPort.geometry.relative = true;

        const rightPort = graph.insertVertex(glyphCell, null, '', 0, .5, portWidth, portWidth);
        rightPort.geometry.offset = new mx.mxPoint(-1 * portWidth / 2, -1 * portWidth / 2);
        rightPort.geometry.relative = true;

      } finally {
        graph.getModel().endUpdate();
      }
    };

    const ds: mxDragSource = mx.mxUtils.makeDraggable(element, this.graph, insertGlyph, this.glyphDragPreviewElt);
    ds.isGridEnabled = function() {
      return this.graph.graphHandler.guidesEnabled;
    };
  }

  /**
   * Find the selected cell, and if there is a cell selected, update its color.
   */
  updateSelectedCellColor(color: string) {
    var selectedCell = this.graph.getSelectionCell();

    if (selectedCell != null) {
      this.graph.getModel().beginUpdate();
      this.graph.setCellStyles(mx.mxConstants.STYLE_FILLCOLOR, color, [selectedCell]);
      this.graph.getModel().endUpdate();
    }
  }

  /**
   * Find the selected cell, and it there is a glyph selected, update its metadata.
   */
  updateSelectedCellInfo(glyphInfo: GlyphInfo) {
    var selectedCell = this.graph.getSelectionCell();

    if (selectedCell != null) {
      selectedCell.data = glyphInfo;
    }
  }

  /**
   * Handles a click event in the graph.
   * @param sender
   * @param event
   */
  handleClickEvent(sender, event) {
    var cell = event.getProperty('cell');

    if (cell != null) {
      console.log('cell clicked :');
      //console.log(cell);

      // Example GlyphInfo
      const glyphInfo = cell.data;
      this.metadataService.setSelectedGlyphInfo(glyphInfo);

      // Example Color
      const color = this.graph.getCellStyle(cell)['fillColor'];
      this.metadataService.setColor(color);
    }
  }

  // noinspection JSUnusedGlobalSymbols
  /**
   * Returns the mxGraph object. This is meant for temporary testing - any permanent code should not rely on this.
   */
  getGraph() {
    return this.graph;
  }

  graphToString(): string {
    var encoder = new mx.mxCodec();
    var result = encoder.encode(this.graph.getModel());
    console.log(result);
    console.log(this.graph.model);
    var xml = mx.mxUtils.getXml(result);
    return xml;
  }

  oldStringToGraph(graphString: string){
    var doc = mx.mxUtils.parseXml(graphString);
    var codec = new mx.mxCode(doc);
    this.graph.getModel().clear();
    codec.decode(doc.documentElement, this.graph.getModel());
  }

  stringToGraph(graphString: string) {
    var doc = mx.mxUtils.parseXml(graphString);
    var codec = new mx.mxCodec(doc);
    this.graph.getModel().clear();
    //this.graph = new mx.mxGraph(this.graphContainer);
    var elt = doc.documentElement.firstChild.firstChild;

    this.graph.getModel().beginUpdate();
    let vertecies = new Map<number, any>();
    let edges = [];
    while (elt != null) {
      if (elt.attributes.getNamedItem('value') == null) {
        elt = elt.nextSibling;
        continue;
      }
      var id = elt.attributes.getNamedItem('id').value;
      var value = elt.attributes.getNamedItem('value').value;
      if (elt.attributes.getNamedItem('vertex') != null) {
        var geo = elt.firstChild;
        var x = 0.0;
        if(geo.attributes.getNamedItem('x') != null)
          x = <number> geo.attributes.getNamedItem('x').value;
        var y = geo.attributes.getNamedItem('y').value;
        var width = geo.attributes.getNamedItem('width').value;
        var height = geo.attributes.getNamedItem('height').value;
        var parent = this.graph.getDefaultParent();
        if(elt.attributes.getNamedItem('parent').value != 1){
          // ports
          parent = vertecies.get(elt.attributes.getNamedItem('parent').value);
          const port = this.graph.insertVertex(parent, null, '', x/1, y/1, width/1, height/1);
          var point = geo.firstChild;
          x = point.attributes.getNamedItem("x").value;
          y = point.attributes.getNamedItem("y").value;
          port.geometry.offset = new mx.mxPoint(x/1,y/1);
          port.geometry.relative = true;

          vertecies.set(id, port);
        }else{
          var style = elt.attributes.getNamedItem('style').value;
          const vertex = this.graph.insertVertex(parent, null, value, x/1, y/1, width/1, height/1, style);
          vertex.setConnectable(false);
          vertecies.set(id, vertex);
        }
      } else if (elt.attributes.getNamedItem('edge') != null) {
        var style = elt.attributes.getNamedItem('style').value;
        var geo = null;
        var source = null;
        if (elt.attributes.getNamedItem('source') != null) {
          source = elt.attributes.getNamedItem('source').value;
        } else {
          geo = elt.firstChild.firstChild;
          var x = <number> geo.attributes.getNamedItem('x').value;
          var y = geo.attributes.getNamedItem('y').value;
          source = [x, y];
        }
        var target = null;
        if (elt.attributes.getNamedItem('target') != null) {
          target = elt.attributes.getNamedItem('target').value;
        } else {
          if (geo == null) {
            geo = elt.firstChild.firstChild;
          } else {
            geo = geo.nextSibling;
          }
          var x = <number> geo.attributes.getNamedItem('x').value;
          var y = geo.attributes.getNamedItem('y').value;
          target = [x, y];
        }
        edges.push([value, source, target, style]);
      }
      elt = elt.nextSibling;
    }
    // edges have to have their source and target set after all the vertecies have been read in
    for (var i = 0; i < edges.length; i++) {
      var source = null;
      var target = null;
      if (!(edges[i][1] instanceof Array)) {
        source = vertecies.get(edges[i][1]);
      }
      if (!(edges[i][2] instanceof Array)) {
        target = vertecies.get(edges[i][2]);
      }
      edges[i][3];
      const edge = this.graph.insertEdge(this.graph.getDefaultParent(), null, edges[i][0], source, target, edges[i][3]);
      // if you remove the /1's below the positioning of the edge scales up by 10
      // we don't want any scaling, so there it shall stay
      // this sure is fun
      if (edges[i][1] instanceof Array) {
        edge.geometry.setTerminalPoint(new mx.mxPoint(edges[i][1][0] / 1, edges[i][1][1] / 1), true);
      }
      if (edges[i][2] instanceof Array) {
        edge.geometry.setTerminalPoint(new mx.mxPoint(edges[i][2][0] / 1, edges[i][2][1] / 1), false);
      }
      edge.relative = true;
    }
    this.graph.getModel().endUpdate();
    this.graph.refresh();
  }
}
