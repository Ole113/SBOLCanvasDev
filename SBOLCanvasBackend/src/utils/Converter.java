package utils;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

import javax.xml.namespace.QName;

import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.Component;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.RestrictionType;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.SBOLWriter;
import org.sbolstandard.core2.SequenceOntology;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import data.GlyphInfo;
import data.MxCell;
import data.MxGeometry;

public class Converter {

	public static void toSBOL(Document graph, OutputStream body) {
		// create objects from the document
		HashMap<Integer, MxCell> containers = new HashMap<Integer, MxCell>();
		HashMap<Integer, MxCell> backbones = new HashMap<Integer, MxCell>();
		HashMap<Integer, MxCell> components = new HashMap<Integer, MxCell>();
		graph.normalize();
		NodeList nList = graph.getElementsByTagName("mxCell");
		for (int temp = 0; temp < nList.getLength(); temp++) {
			Node node = nList.item(temp);

			// cell info
			Element cellElement = (Element) node;
			MxCell cell = new MxCell();
			cell.setId(Integer.parseInt(cellElement.getAttribute("id")));
			cell.setValue(cellElement.getAttribute("value"));
			cell.setStyle(cellElement.getAttribute("style"));
			if (cellElement.hasAttribute("vertex"))
				cell.setVertex(Integer.parseInt(cellElement.getAttribute("vertex")) == 1 ? true : false);
			if (cellElement.hasAttribute("connectable"))
				cell.setConnectable(Integer.parseInt(cellElement.getAttribute("connectable")) == 1 ? true : false);
			if (cellElement.hasAttribute("parent"))
				cell.setParent(Integer.parseInt(cellElement.getAttribute("parent")));
			else
				cell.setParent(-1);

			// geometry info
			if (cellElement.getElementsByTagName("mxGeometry").getLength() > 0) {
				Element geoElement = (Element) cellElement.getElementsByTagName("mxGeometry").item(0);
				MxGeometry geometry = new MxGeometry();
				if (geoElement.hasAttribute("x"))
					geometry.setX(Double.parseDouble(geoElement.getAttribute("x")));
				if (geoElement.hasAttribute("y"))
					geometry.setY(Double.parseDouble(geoElement.getAttribute("y")));
				if (geoElement.hasAttribute("width"))
					geometry.setWidth(Double.parseDouble(geoElement.getAttribute("width")));
				if (geoElement.hasAttribute("height"))
					geometry.setHeight(Double.parseDouble(geoElement.getAttribute("height")));
				cell.setGeometry(geometry);
			}

			// glyph info
			if (cellElement.getElementsByTagName("GlyphInfo").getLength() > 0) {
				Element infoElement = (Element) cellElement.getElementsByTagName("GlyphInfo").item(0);
				GlyphInfo info = new GlyphInfo();
				info.setPartType(infoElement.getAttribute("partType"));
				info.setPartRole(infoElement.getAttribute("partRole"));
				info.setPartRefine(infoElement.getAttribute("partRefine"));
				info.setDisplayID(infoElement.getAttribute("displayID"));
				info.setName(infoElement.getAttribute("name"));
				info.setDescription(infoElement.getAttribute("description"));
				info.setVersion(infoElement.getAttribute("version"));
				cell.setInfo(info);
			}

			if (cell.getStyle().equals("circuitContainer")) {
				containers.put(cell.getId(), cell);
			} else if (cell.getStyle().equals("backbone")) {
				backbones.put(cell.getId(), cell);
			} else if (cell.getStyle().startsWith("glyph")) {
				components.put(cell.getId(), cell);
			}
		}

		// create the document
		String uriPrefix = "https://sbolcanvas.org/";
		SBOLDocument document = new SBOLDocument();
		document.setDefaultURIprefix(uriPrefix);
		document.setComplete(true);
		document.setCreateDefaults(true);

		try {
			// create the top level component definitions, aka strands
			for (MxCell cell : backbones.values()) {
				MxCell container = containers.get(cell.getParent());
				ComponentDefinition cd = document.createComponentDefinition("cd" + container.getId(),
						ComponentDefinition.DNA_REGION);
				cd.addRole(SequenceOntology.ENGINEERED_REGION);
				
				// container annotations
				cd.createAnnotation(new QName(uriPrefix, "containerCell", "id"), container.getId());
				cd.createAnnotation(new QName(uriPrefix, "containerGeometry", "x"), container.getGeometry().getX());
				cd.createAnnotation(new QName(uriPrefix, "containerGeometry", "y"), container.getGeometry().getY());
				cd.createAnnotation(new QName(uriPrefix, "containerGeometry", "width"), container.getGeometry().getWidth());
				cd.createAnnotation(new QName(uriPrefix, "containerGeometry", "height"), container.getGeometry().getHeight());
				
				// backbone geometry
				cd.createAnnotation(new QName(uriPrefix, "backboneGeometry", "x"), cell.getGeometry().getX());
				cd.createAnnotation(new QName(uriPrefix, "backboneGeometry", "y"), cell.getGeometry().getY());
				cd.createAnnotation(new QName(uriPrefix, "backboneGeometry", "width"), cell.getGeometry().getWidth());
				cd.createAnnotation(new QName(uriPrefix, "backboneGeometry", "height"), cell.getGeometry().getHeight());
			}

			// create the things needed for parts
			for (MxCell cell : components.values()) {
				ComponentDefinition componentCD = document.createComponentDefinition(cell.getInfo().getDisplayID(),
						SBOLData.types.get(cell.getInfo().getPartType()));
				if (cell.getInfo().getPartRefine() == null || cell.getInfo().getPartRefine().equals("")) {
					componentCD.addRole(SBOLData.roles.get(cell.getInfo().getPartRole()));
				} else {
					componentCD.addRole(SBOLData.refinements.get(cell.getInfo().getPartRefine()));
				}
				componentCD.setName(cell.getInfo().getName());
				componentCD.setDescription(cell.getInfo().getDescription());

				Component component = document.getComponentDefinition("cd" + cell.getParent(), null)
						.createComponent(cell.getInfo().getDisplayID(), AccessType.PUBLIC, componentCD.getDisplayId());
				
				// cell annotation
				component.createAnnotation(new QName(uriPrefix, "cell", "id"), cell.getId());
				component.createAnnotation(new QName(uriPrefix, "cell", "style"), cell.getStyle());
				component.createAnnotation(new QName(uriPrefix, "cell", "id"), cell.getId());
				component.createAnnotation(new QName(uriPrefix, "cell", "connectable"), cell.isConnectable());
				
				// geometry annotation
				component.createAnnotation(new QName(uriPrefix, "geometry", "x"), cell.getGeometry().getX());
				component.createAnnotation(new QName(uriPrefix, "geometry", "y"), cell.getGeometry().getY());
				component.createAnnotation(new QName(uriPrefix, "geometry", "width"), cell.getGeometry().getWidth());
				component.createAnnotation(new QName(uriPrefix, "geometry", "height"), cell.getGeometry().getHeight());
			}

			// create sequence constraints
			for (ComponentDefinition cd : document.getRootComponentDefinitions()) {
				Set<Component> componentSet = cd.getComponents();
				Component[] componentArr = componentSet.toArray(new Component[0]);
				Arrays.sort(componentArr, new Comparator<Component>() {

					@Override
					public int compare(Component o1, Component o2) {
						return o1.getAnnotation(new QName(uriPrefix, "geometry", "x")).getDoubleValue() < o2
								.getAnnotation(new QName(uriPrefix, "geometry", "x")).getDoubleValue() ? -1 : 1;
					}

				});

				for (int i = 0; i < componentArr.length - 1; i++) {
					cd.createSequenceConstraint("constraint" + i, RestrictionType.PRECEDES,
							componentArr[i].getIdentity(), componentArr[i + 1].getIdentity());
				}
			}

			// write to body
			SBOLWriter.setKeepGoing(true);
			SBOLWriter.write(document, body);

		} catch (SBOLValidationException | SBOLConversionException e) {
			e.printStackTrace();
		}
	}

	public static String toGraph(String sbol) {
		return null;

	}

}