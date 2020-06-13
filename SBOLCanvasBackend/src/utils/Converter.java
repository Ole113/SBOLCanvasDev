package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.Annotation;
import org.sbolstandard.core2.Component;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.DirectionType;
import org.sbolstandard.core2.FunctionalComponent;
import org.sbolstandard.core2.GenericTopLevel;
import org.sbolstandard.core2.Interaction;
import org.sbolstandard.core2.Location;
import org.sbolstandard.core2.MapsTo;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.OrientationType;
import org.sbolstandard.core2.Participation;
import org.sbolstandard.core2.RefinementType;
import org.sbolstandard.core2.RestrictionType;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLReader;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.SBOLWriter;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.SequenceAnnotation;
import org.sbolstandard.core2.SystemsBiologyOntology;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.mxgraph.io.mxCodec;
import com.mxgraph.io.mxCodecRegistry;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxGraphModel;
import com.mxgraph.model.mxGraphModel.Filter;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxUtils;
import com.mxgraph.util.mxXmlUtils;
import com.mxgraph.view.mxGraph;

import data.GlyphInfo;
import data.InteractionInfo;

public class Converter {

	static String uriPrefix = "https://sbolcanvas.org/";
	static String annPrefix = "SBOLCanvas";

	static final String STYLE_CIRCUIT_CONTAINER = "circuitContainer";
	static final String STYLE_BACKBONE = "backbone";
	static final String STYLE_TEXTBOX = "textBox";
	static final String STYLE_SCAR = "Scar (Assembly Scar)";
	static final String STYLE_NGA = "NGA (No Glyph Assigned)";
	static final String STYLE_MOLECULAR_SPECIES = "molecularSpeciesGlyph";
	static final String STYLE_SEQUENCE_FEATURE = "sequenceFeatureGlyph";
	static final String STYLE_INTERACTION = "interactionGlyph";
	static final String STYLE_MODULE_VIEW = "moduleViewCell";
	static final String STYLE_COMPONENT_VIEW = "componentViewCell";

	/**
	 * Filters mxCells that contain "textBox" in the style string
	 */
	static Filter textBoxFilter = new Filter() {
		@Override
		public boolean filter(Object arg0) {
			return arg0 instanceof mxCell && ((mxCell) arg0).getStyle().contains(STYLE_TEXTBOX);
		}
	};

	/**
	 * Filters mxCells that contain "protein" in the style string
	 */
	static Filter proteinFilter = new Filter() {
		@Override
		public boolean filter(Object arg0) {
			return arg0 instanceof mxCell && ((mxCell) arg0).getStyle().contains(STYLE_MOLECULAR_SPECIES);
		}
	};

	/**
	 * Filters mxCells that contain "circuitContainer" in the style string
	 */
	static Filter containerFilter = new Filter() {
		@Override
		public boolean filter(Object arg0) {
			return arg0 instanceof mxCell && ((mxCell) arg0).getStyle().contains(STYLE_CIRCUIT_CONTAINER);
		}
	};

	/**
	 * Filters mxCells that contain "backbone" in the style string
	 */
	static Filter backboneFilter = new Filter() {
		@Override
		public boolean filter(Object arg0) {
			return arg0 instanceof mxCell && ((mxCell) arg0).getStyle().contains(STYLE_BACKBONE);
		}
	};

	/**
	 * Filters mxCells that contain "sequenceFeatureGlyph" in the style string
	 */
	static Filter sequenceFeatureFilter = new Filter() {
		@Override
		public boolean filter(Object arg0) {
			return arg0 instanceof mxCell && ((mxCell) arg0).getStyle().contains(STYLE_SEQUENCE_FEATURE);
		}
	};

	static {
		// Necessary for encoding/decoding GlyphInfo and InteractionInfo
		mxCodecRegistry.addPackage("data");
	};

	private Hashtable<String, GlyphInfo> glyphInfoDict = new Hashtable<String, GlyphInfo>();
	private GenericTopLevel layout;

	@SuppressWarnings("unchecked")
	public void toSBOL(InputStream graphStream, OutputStream sbolStream, String filename)
			throws SAXException, IOException, ParserConfigurationException, SBOLValidationException,
			SBOLConversionException, TransformerFactoryConfigurationError, TransformerException, URISyntaxException {
		// read in the mxGraph
		mxGraph graph = parseGraph(graphStream);
		mxGraphModel model = (mxGraphModel) graph.getModel();
		glyphInfoDict = (Hashtable<String, GlyphInfo>) ((mxCell) model.getCell("0")).getValue();

		// create the document
		SBOLDocument document = new SBOLDocument();
		document.setDefaultURIprefix(uriPrefix);
		document.setComplete(true);
		document.setCreateDefaults(true);
		layout = document.createGenericTopLevel("Layout", new QName(uriPrefix, "Layout", annPrefix));

		// Arrays.stream is the java 8 way to cast Object[] to some other array
		mxCell[] viewCells = Arrays.stream(mxGraphModel.getChildCells(model, model.getCell("1"), true, false))
				.toArray(mxCell[]::new);

		// filter the circuit containers and create component definitions
		for (mxCell viewCell : viewCells) {
			mxCell[] viewChildren = Arrays.stream(mxGraphModel.getChildCells(model, viewCell, true, false))
					.toArray(mxCell[]::new);
			mxCell[] circuitContainers = Arrays.stream(mxGraphModel.filterCells(viewChildren, containerFilter))
					.toArray(mxCell[]::new);
			for (mxCell circuitContainer : circuitContainers) {
				// avoid duplicates from aliases in modules
				if (document.getComponentDefinition(new URI((String) circuitContainer.getValue())) != null)
					continue;
				createComponentDefinition(document, model, circuitContainer);
			}
		}

		// construct the module definitions, and add text annotations for component
		// definitions
		for (mxCell viewCell : viewCells) {
			Object[] viewChildren = mxGraphModel.getChildCells(model, viewCell, true, true);
			mxCell[] circuitContainers = Arrays.stream(mxGraphModel.filterCells(viewChildren, containerFilter))
					.toArray(mxCell[]::new);
			mxCell[] proteins = Arrays.stream(mxGraphModel.filterCells(viewChildren, proteinFilter))
					.toArray(mxCell[]::new);
			if (viewCell.getStyle().equals(STYLE_MODULE_VIEW) || circuitContainers.length > 1 || proteins.length > 0) {
				// TODO when moddefs are supported the id should already be correct
				// module definitions
				((mxCell) viewCell).setId(filename);
				createModuleDefinition(document, model, viewCell);
			} else {
				// component definitions
				attachTextBoxAnnotation(document, model, viewCell);
			}
		}

		// link the component definitions (create components and set up references)
		Set<String> handledContainers = new HashSet<String>();
		for (mxCell viewCell : viewCells) {
			Object[] viewChildren = mxGraphModel.getChildCells(model, viewCell, true, false);
			mxCell[] circuitContainers = Arrays.stream(mxGraphModel.filterCells(viewChildren, containerFilter))
					.toArray(mxCell[]::new);
			for (mxCell circuitContainer : circuitContainers) {
				if (handledContainers.contains((String) circuitContainer.getValue()))
					continue;
				linkComponentDefinition(document, graph, model, circuitContainer);
				handledContainers.add((String) circuitContainer.getValue());
			}
		}

		// link the module definitions
		for (mxCell viewCell : viewCells) {
			Object[] viewChildren = mxGraphModel.getChildCells(model, viewCell, true, true);
			mxCell[] circuitContainers = Arrays.stream(mxGraphModel.filterCells(viewChildren, containerFilter))
					.toArray(mxCell[]::new);
			mxCell[] proteins = Arrays.stream(mxGraphModel.filterCells(viewChildren, proteinFilter))
					.toArray(mxCell[]::new);
			if (viewCell.getStyle().equals(STYLE_MODULE_VIEW) || circuitContainers.length > 1 || proteins.length > 0) {
				// module definitions
				linkModuleDefinition(document, graph, model, viewCell);
			}
		}

		// write to body
		SBOLWriter.setKeepGoing(true);
		SBOLWriter.write(document, sbolStream);
	}

	public void toGraph(InputStream sbolStream, OutputStream graphStream) throws SBOLValidationException, IOException,
			SBOLConversionException, ParserConfigurationException, TransformerException, SAXException {
		// load the sbol file into the proper objects
		SBOLDocument document = SBOLReader.read(sbolStream);
		toGraph(document, graphStream);
	}

	public void toGraph(SBOLDocument document, OutputStream graphStream) throws IOException,
			ParserConfigurationException, TransformerException, SBOLValidationException, SAXException {

		document.setDefaultURIprefix(uriPrefix);

		// set up the graph and glyphdict
		mxGraph graph = new mxGraph();
		mxGraphModel model = (mxGraphModel) graph.getModel();
		mxCell cell0 = (mxCell) model.getCell("0");
		cell0.setValue(glyphInfoDict);

		layout = document.getGenericTopLevel("Layout", null);

		ModuleDefinition modDef = null;
		if (document.getRootModuleDefinitions().size() > 0) {
			modDef = document.getRootModuleDefinitions().iterator().next();
		}

		// top level component definitions
		Set<ComponentDefinition> compDefs = document.getComponentDefinitions();
		Set<ComponentDefinition> handledCompDefs = new HashSet<ComponentDefinition>();
		if (modDef != null) {
			handledCompDefs = createModuleView(document, graph, modDef);
		}

		// we don't want to create views for componentDefinitions handled in the module
		// definition (proteins)
		compDefs.removeAll(handledCompDefs);
		for (ComponentDefinition compDef : compDefs) {
			createComponentView(document, graph, compDef);
		}

		// convert the objects to the graph xml
		graphStream.write(encodeMxGraphObject(model).getBytes());
	}

	public void toSubGraph(InputStream sbolStream, OutputStream graphStream)
			throws SBOLValidationException, IOException, SBOLConversionException, SAXException,
			ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException {
		SBOLDocument document = SBOLReader.read(sbolStream);
		toSubGraph(document, graphStream);
	}

	public void toSubGraph(SBOLDocument document, OutputStream graphStream)
			throws SAXException, IOException, ParserConfigurationException, SBOLValidationException,
			TransformerFactoryConfigurationError, TransformerException {
		// set up the graph and glyphdict
		mxGraph graph = new mxGraph();
		mxGraphModel model = (mxGraphModel) graph.getModel();
		mxCell cell0 = (mxCell) model.getCell("0");
		cell0.setValue(glyphInfoDict);

		// top level component definition
		ComponentDefinition rootCompDef = document.getRootComponentDefinitions().iterator().next();

		graph.insertVertex((mxCell) model.getCell("1"), null, rootCompDef.getIdentity().toString(), 0, 0, 0, 0);

		Set<ComponentDefinition> compDefs = document.getComponentDefinitions();

		for (ComponentDefinition compDef : compDefs) {
			createComponentView(document, graph, compDef);
		}

		// convert the objects to the graph xml
		graphStream.write(encodeMxGraphObject(model).getBytes());
	}

	// helpers

	private int getSequenceLength(SBOLDocument document, ComponentDefinition componentDef) {
		if (componentDef.getSequences() != null && componentDef.getSequences().size() > 0) {
			Sequence sequence = componentDef.getSequences().iterator().next();
			return sequence.getElements().length();
		} else {
			if (componentDef.getSequenceAnnotations() != null && componentDef.getSequenceAnnotations().size() > 0) {
				int total = 0;
				for (SequenceAnnotation annotation : componentDef.getSequenceAnnotations()) {
					if (annotation.getComponent() != null) {
						Component component = annotation.getComponent();

						ComponentDefinition subComponentDef = component.getDefinition();
						total = total + getSequenceLength(document, subComponentDef);
					}
				}
				return total;
			} else {
				return 0;
			}
		}

	}

	/**
	 * @param document
	 * @param model
	 * @param viewCell
	 * @throws SBOLValidationException
	 * @throws TransformerFactoryConfigurationError
	 * @throws TransformerException
	 * @throws URISyntaxException
	 */
	private void createModuleDefinition(SBOLDocument document, mxGraphModel model, mxCell viewCell)
			throws SBOLValidationException, TransformerFactoryConfigurationError, TransformerException,
			URISyntaxException {
		mxCell[] viewChildren = Arrays.stream(mxGraphModel.getChildCells(model, viewCell, true, false))
				.toArray(mxCell[]::new);
		mxCell[] circuitContainers = Arrays.stream(mxGraphModel.filterCells(viewChildren, containerFilter))
				.toArray(mxCell[]::new);
		mxCell[] proteins = Arrays.stream(mxGraphModel.filterCells(viewChildren, proteinFilter)).toArray(mxCell[]::new);
		mxCell[] textBoxes = Arrays.stream(mxGraphModel.filterCells(viewChildren, textBoxFilter))
				.toArray(mxCell[]::new);

		ModuleDefinition modDef = document.createModuleDefinition(viewCell.getId());

		// text boxes
		if (textBoxes.length > 0) {
			modDef.createAnnotation(new QName(uriPrefix, "textBoxes", annPrefix), encodeMxGraphObject(textBoxes));
		}

		// proteins
		for (mxCell protein : proteins) {
			// proteins also have glyphInfos
			GlyphInfo proteinInfo = (GlyphInfo) glyphInfoDict.get(protein.getValue());
			ComponentDefinition proteinCD = document.getComponentDefinition(new URI((String) protein.getValue()));
			if (proteinCD == null) {
				proteinCD = document.createComponentDefinition(proteinInfo.getDisplayID(), proteinInfo.getVersion(),
						SBOLData.types.getValue(proteinInfo.getPartType()));
				proteinCD.setDescription(proteinInfo.getDescription());
				proteinCD.setName(proteinInfo.getName());
				proteinCD.addRole(SystemsBiologyOntology.INHIBITOR); // TODO determine from interaction
			}
			FunctionalComponent proteinFuncComp = modDef.createFunctionalComponent(
					proteinCD.getDisplayId() + "_" + protein.getId(), AccessType.PUBLIC, proteinCD.getIdentity(),
					DirectionType.INOUT);
			// the layout information in the component definition
			proteinFuncComp.createAnnotation(new QName(uriPrefix, "protein", annPrefix), encodeMxGraphObject(protein));
		}

		// component definitions (should already have been created, just need to link
		// them with functional components)
		for (mxCell circuitContainer : circuitContainers) {

			ComponentDefinition containerCD = document
					.getComponentDefinition(new URI((String) circuitContainer.getValue()));

			FunctionalComponent funcComp = modDef.createFunctionalComponent(
					containerCD.getDisplayId() + "_" + circuitContainer.getId(), AccessType.PUBLIC,
					containerCD.getIdentity(), DirectionType.INOUT);

			// store extra graph information
			funcComp.createAnnotation(new QName(uriPrefix, "containerCell", annPrefix),
					encodeMxGraphObject(circuitContainer));
		}
	}

	private void attachTextBoxAnnotation(SBOLDocument document, mxGraphModel model, mxCell viewCell)
			throws SBOLValidationException, TransformerFactoryConfigurationError, TransformerException,
			URISyntaxException {
		ComponentDefinition compDef = document.getComponentDefinition(new URI(viewCell.getId()));

		// store extra mxGraph information
		Object[] viewChildren = mxGraphModel.getChildCells(model, viewCell, true, false);
		mxCell[] textBoxes = Arrays.stream(mxGraphModel.filterCells(viewChildren, textBoxFilter))
				.toArray(mxCell[]::new);

		compDef.createAnnotation(new QName(uriPrefix, "textBoxes", annPrefix), encodeMxGraphObject(textBoxes));
	}

	private void createComponentDefinition(SBOLDocument document, mxGraphModel model, mxCell circuitContainer)
			throws URISyntaxException, SBOLValidationException, TransformerFactoryConfigurationError,
			TransformerException {
		// get the glyph info associated with this view cell
		GlyphInfo glyphInfo = glyphInfoDict.get(circuitContainer.getValue());

		// if there isn't a uri prefix give it the default
		if (glyphInfo.getUriPrefix() == null || glyphInfo.getUriPrefix().equals(""))
			glyphInfo.setUriPrefix(uriPrefix);

		ComponentDefinition compDef = document.createComponentDefinition(glyphInfo.getUriPrefix(),
				glyphInfo.getDisplayID(), glyphInfo.getVersion(), SBOLData.types.getValue(glyphInfo.getPartType()));
		if (glyphInfo.getOtherTypes() != null) {
			for (String type : glyphInfo.getOtherTypes()) {
				compDef.addType(new URI(type));
			}
		}

		if (glyphInfo.getPartRefine() == null || glyphInfo.getPartRefine().equals("")) {
			// if there isn't a part refine set the role
			if (glyphInfo.getPartRole() == null || glyphInfo.getPartRole().equals("")) {
				glyphInfo.setPartRole(STYLE_NGA);
			}
			compDef.addRole(SBOLData.roles.getValue(glyphInfo.getPartRole()));
		} else {
			// otherwise set the part refinement
			compDef.addRole(SBOLData.refinements.getValue(glyphInfo.getPartRefine()));
		}
		if (glyphInfo.getOtherRoles() != null) {
			for (String role : glyphInfo.getOtherRoles()) {
				compDef.addRole(new URI(role));
			}
		}

		compDef.setName(glyphInfo.getName());
		compDef.setDescription(glyphInfo.getDescription());

		// component sequence
		if (glyphInfo.getSequence() != null && !glyphInfo.getSequence().equals("")) {
			Sequence seq = document.createSequence(compDef.getDisplayId() + "Sequence", glyphInfo.getSequence(),
					Sequence.IUPAC_DNA);
			compDef.addSequence(seq.getIdentity());
		}

		// store extra mxGraph information
		Object[] containerChildren = mxGraphModel.getChildCells(model, circuitContainer, true, false);
		mxCell backboneCell = (mxCell) mxGraphModel.filterCells(containerChildren, backboneFilter)[0];
		compDef.createAnnotation(new QName(uriPrefix, "backboneCell", annPrefix), encodeMxGraphObject(backboneCell));
	}

	private void linkModuleDefinition(SBOLDocument document, mxGraph graph, mxGraphModel model, mxCell viewCell)
			throws SBOLValidationException, TransformerFactoryConfigurationError, TransformerException,
			URISyntaxException {
		mxCell[] edges = Arrays.stream(mxGraphModel.getChildCells(model, viewCell, false, true)).toArray(mxCell[]::new);

		ModuleDefinition modDef = document.getModuleDefinition(viewCell.getId(), null);

		// edges to interactions
		for (mxCell edge : edges) {

			// interaction
			InteractionInfo intInfo = (InteractionInfo) edge.getValue();
			Interaction interaction = modDef.createInteraction(intInfo.getDisplayID(),
					SBOLData.interactions.getValue(intInfo.getInteractionType()));
			this.createGraphicalLayout(graph, edge, interaction.getIdentity());

			// participants
			mxCell source = (mxCell) edge.getSource();
			mxCell target = (mxCell) edge.getTarget();
			GlyphInfo sourceInfo = null;
			GlyphInfo targetInfo = null;
			if (source != null)
				sourceInfo = glyphInfoDict.get(source.getValue());
			if (target != null)
				targetInfo = glyphInfoDict.get(target.getValue());

			// source participant
			if (source != null) {
				FunctionalComponent sourceFC = getOrCreateParticipant(document, modDef, sourceInfo, source);
				interaction.createParticipation(sourceInfo.getDisplayID() + "_" + source.getId(),
						sourceFC.getIdentity(), getParticipantType(true, interaction.getTypes()));
			}

			// target participant
			if (target != null) {
				FunctionalComponent targetFC = getOrCreateParticipant(document, modDef, targetInfo, target);
				interaction.createParticipation(targetInfo.getDisplayID() + "_" + target.getId(),
						targetFC.getIdentity(), getParticipantType(false, interaction.getTypes()));
			}

		}

	}

	private FunctionalComponent getOrCreateParticipant(SBOLDocument document, ModuleDefinition modDef,
			GlyphInfo partInfo, mxCell part) throws SBOLValidationException {
		FunctionalComponent sourceFC = modDef.getFunctionalComponent(partInfo.getDisplayID() + "_" + part.getId());
		if (sourceFC == null) {
			ComponentDefinition sourceCD = document.getComponentDefinition(URI.create((String) part.getValue()));
			sourceFC = modDef.createFunctionalComponent(partInfo.getDisplayID() + "_" + part.getId(), AccessType.PUBLIC,
					sourceCD.getIdentity(), DirectionType.INOUT);

			// the functional component doesn't represent a top level componentDefinition,
			// so create a mapsTo
			GlyphInfo parentInfo = glyphInfoDict.get(part.getParent().getValue());
			FunctionalComponent parentFC = modDef
					.getFunctionalComponent(parentInfo.getDisplayID() + "_" + part.getParent().getId());
			ComponentDefinition parentCD = parentFC.getDefinition();
			String componentID = partInfo.getDisplayID() + "_" + part.getParent().getIndex(part);
			Component sourceComponent = parentCD.getComponent(componentID);
			parentFC.createMapsTo("mapsTo_" + componentID, RefinementType.USEREMOTE, sourceFC.getIdentity(),
					sourceComponent.getIdentity());
		}
		return sourceFC;
	}

	private void linkComponentDefinition(SBOLDocument document, mxGraph graph, mxGraphModel model,
			mxCell circuitContainer) throws SBOLValidationException, TransformerFactoryConfigurationError,
			TransformerException, URISyntaxException {

		ComponentDefinition compDef = document.getComponentDefinition(URI.create((String) circuitContainer.getValue()));
		Object[] containerChildren = mxGraphModel.getChildCells(model, circuitContainer, true, false);
		mxCell[] glyphs = Arrays.stream(mxGraphModel.filterCells(containerChildren, sequenceFeatureFilter))
				.toArray(mxCell[]::new);
		Component previous = null;
		int count = 0, start = 0, end = 0;
		for (mxCell glyph : glyphs) {
			GlyphInfo info = glyphInfoDict.get(glyph.getValue());
			ComponentDefinition glyphCD = document.getComponentDefinition(URI.create((String) glyph.getValue()));
			Component component = compDef.createComponent(info.getDisplayID() + "_" + glyph.getParent().getIndex(glyph),
					AccessType.PUBLIC, URI.create((String) glyph.getValue()));

			// cell annotation
			this.createGraphicalLayout(graph, glyph, component.getIdentity());

			// layout.addGraphicalObject(this.createGraphicalLayout(graph, glyph));
			// sequence constraints
			if (previous != null) {
				compDef.createSequenceConstraint(compDef.getDisplayId() + "Constraint" + count,
						RestrictionType.PRECEDES, previous.getIdentity(), component.getIdentity());
			}

			// container sequence annotation
			OrientationType orientation = OrientationType.INLINE;
			String direction = (String) graph.getCellStyle(glyph).get(mxConstants.STYLE_DIRECTION);
			if (direction != null && !direction.equals("east")) {
				orientation = OrientationType.REVERSECOMPLEMENT;
			}
			int length = getSequenceLength(document, glyphCD);
			if (length > 0) {
				start = end + 1;
				end = start + length - 1;
				SequenceAnnotation annotation = compDef.createSequenceAnnotation(
						compDef.getDisplayId() + "Annotation" + count, "location" + count, start, end, orientation);
				annotation.setComponent(component.getIdentity());
			} else {
				SequenceAnnotation annotation = compDef.createSequenceAnnotation(
						compDef.getDisplayId() + "Annotation" + count, "location" + count, orientation);
				annotation.setComponent(component.getIdentity());
			}

			previous = component;
			count++;
		}
	}

	private Set<ComponentDefinition> createModuleView(SBOLDocument document, mxGraph graph, ModuleDefinition modDef)
			throws SAXException, IOException, ParserConfigurationException, SBOLValidationException {
		mxGraphModel model = (mxGraphModel) graph.getModel();
		mxCell cell1 = (mxCell) model.getCell("1");

		Set<ComponentDefinition> handledCompDefs = new HashSet<ComponentDefinition>();

		// create the root view cell
		// TODO pull the the module id when multiple modules are supported.
		mxCell rootViewCell = (mxCell) graph.insertVertex(cell1, "rootView", null, 0, 0, 0, 0, STYLE_MODULE_VIEW);

		// text boxes
		Annotation textBoxAnn = modDef.getAnnotation(new QName(uriPrefix, "textBoxes", annPrefix));
		if (textBoxAnn != null) {
			@SuppressWarnings("unchecked")
			List<mxCell> textBoxes = (List<mxCell>) decodeMxGraphObject(textBoxAnn.getStringValue());
			for (mxCell textBox : textBoxes) {
				model.add(rootViewCell, textBox, 0);
			}
		}

		// only non mapped FunctionalComponents represent top level strands so filter
		// them
		Set<FunctionalComponent> modDefFCs = modDef.getFunctionalComponents();
		Set<FunctionalComponent> notMappedFCs = modDef.getFunctionalComponents();
		HashMap<URI, URI> uriMaps = new HashMap<URI, URI>();
		for (FunctionalComponent funcComp : modDefFCs) {
			Set<MapsTo> mapsTos = funcComp.getMapsTos();
			if (mapsTos != null && mapsTos.size() > 0) {
				for (MapsTo mapsTo : mapsTos) {
					FunctionalComponent mappedFC = modDef.getFunctionalComponent(mapsTo.getLocalIdentity());
					notMappedFCs.remove(mappedFC);
					uriMaps.put(mapsTo.getLocalIdentity(), mapsTo.getRemoteIdentity());
				}
			}
		}

		// create the top level component definitions and proteins
		HashMap<String, mxCell> compToCell = new HashMap<String, mxCell>();
		for (FunctionalComponent funcComp : notMappedFCs) {
			ComponentDefinition compDef = funcComp.getDefinition();

			// proteins
			if (!compDef.getTypes().contains(ComponentDefinition.DNA_REGION)) {
				// proteins don't have a mapping, but we need it for interactions
				Annotation protienAnn = funcComp.getAnnotation(new QName(uriPrefix, "protein", annPrefix));
				mxCell protien = null;
				if (protienAnn != null) {
					protien = (mxCell) decodeMxGraphObject(protienAnn.getStringValue());
					protien.setValue(compDef.getIdentity().toString());
					model.add(rootViewCell, protien, 0);
				} else {
					protien = (mxCell) graph.insertVertex(rootViewCell, null, compDef.getIdentity().toString(), 0, 0, 0,
							0, STYLE_MOLECULAR_SPECIES);
				}
				compToCell.put(funcComp.getIdentity() + "_" + compDef.getIdentity(), protien);
				GlyphInfo info = genGlyphInfo(compDef);
				glyphInfoDict.put(info.getFullURI(), info);
				handledCompDefs.add(compDef);
				continue;
			}

			// add the container cell and backbone
			Annotation containerAnn = funcComp.getAnnotation(new QName(uriPrefix, "containerCell", annPrefix));
			mxCell container = null;
			if (containerAnn != null) {
				container = (mxCell) decodeMxGraphObject(containerAnn.getStringValue());
				container.setValue(compDef.getIdentity().toString());
				model.add(rootViewCell, container, 0);
			} else {
				container = (mxCell) graph.insertVertex(rootViewCell, null, compDef.getIdentity().toString(), 0, 0, 0,
						0, STYLE_CIRCUIT_CONTAINER);
			}
			Annotation backboneAnn = compDef.getAnnotation(new QName(uriPrefix, "backboneCell", annPrefix));
			mxCell backbone = null;
			if (backboneAnn != null) {
				backbone = (mxCell) decodeMxGraphObject(backboneAnn.getStringValue());
				model.add(container, backbone, 0);
			} else {
				backbone = (mxCell) graph.insertVertex(container, null, null, 0, 0, 0, 0, STYLE_BACKBONE);
			}
			GlyphInfo info = genGlyphInfo(compDef);
			glyphInfoDict.put(info.getFullURI(), info);

			// glyphs
			Component[] glyphArray = compDef.getSortedComponents().toArray(new Component[0]);
			double maxX = 0;
			for (int glyphIndex = 0; glyphIndex < glyphArray.length; glyphIndex++) {
				Component glyphComponent = glyphArray[glyphIndex];
				mxCell glyphCell = this.getGraphicalLayout(graph, glyphArray[glyphIndex].getIdentity());
				if (glyphCell != null) {
					glyphCell.setValue(glyphComponent.getDefinition().getIdentity().toString());
					//TODO fix the base style name
					glyphCell.setStyle(STYLE_SEQUENCE_FEATURE);
					model.add(container, glyphCell, glyphIndex);
				} else {
					glyphCell = (mxCell) graph.insertVertex(container, null,
							glyphComponent.getDefinition().getIdentity().toString(), maxX++, 0, 0, 0,
							STYLE_SEQUENCE_FEATURE);
				}

				// style filp
				SequenceAnnotation seqAnn = compDef.getSequenceAnnotation(glyphComponent);
				if (seqAnn != null) {
					Location loc = seqAnn.getLocations().iterator().next();
					if (loc.getOrientation() == OrientationType.REVERSECOMPLEMENT) {
						graph.setCellStyles(mxConstants.STYLE_DIRECTION, "west", new Object[] { glyphCell });
					}
				}

				// store the cell so we can use it in interactions
				for (MapsTo mapsTo : funcComp.getMapsTos()) {
					if (mapsTo.getLocalDefinition().equals(glyphComponent.getDefinition())) {
						compToCell.put(mapsTo.getLocalIdentity() + "_" + glyphComponent.getIdentity(), glyphCell);
						break;
					}
				}
			}
		}

		// interactions
		Set<Interaction> interactions = modDef.getInteractions();
		for (Interaction interaction : interactions) {
			Annotation interactionAnn = interaction.getAnnotation(new QName(uriPrefix, "edge", annPrefix));
			mxCell edge = null;
			if (interactionAnn != null) {
				edge = (mxCell) decodeMxGraphObject(interactionAnn.getStringValue());
				edge = (mxCell) model.add(rootViewCell, edge, 0);
			} else {
				edge = (mxCell) graph.insertEdge(rootViewCell, null, null, null, null);
			}
			edge.setValue(genInteractionInfo(interaction));

			URI targetType = getParticipantType(false, interaction.getTypes());
			URI sourceType = getParticipantType(true, interaction.getTypes());

			Participation[] participations = interaction.getParticipations().toArray(new Participation[0]);
			for (int i = 0; i < participations.length; i++) {
				// theoretically more than 2, but we currently only support 2
				if (participations[i].getRoles().contains(sourceType)) {
					URI mappedURI = uriMaps.get(participations[i].getParticipant().getIdentity());
					if (mappedURI == null)
						mappedURI = participations[i].getParticipant().getDefinition().getIdentity();
					mxCell source = compToCell.get(participations[i].getParticipant().getIdentity() + "_" + mappedURI);
					edge.setSource(source);
				} else if (participations[i].getRoles().contains(targetType)) {
					URI mappedURI = uriMaps.get(participations[i].getParticipant().getIdentity());
					if (mappedURI == null)
						mappedURI = participations[i].getParticipant().getDefinition().getIdentity();
					mxCell target = compToCell.get(participations[i].getParticipant().getIdentity() + "_" + mappedURI);
					edge.setTarget(target);
				}
			}
		}

		return handledCompDefs;
	}

	private void createComponentView(SBOLDocument document, mxGraph graph, ComponentDefinition compDef)
			throws SAXException, IOException, ParserConfigurationException, SBOLValidationException {
		mxGraphModel model = (mxGraphModel) graph.getModel();
		mxCell cell1 = (mxCell) model.getCell("1");

		// create the glyphInfo and store it in the dictionary
		GlyphInfo info = genGlyphInfo(compDef);
		glyphInfoDict.put(info.getFullURI(), info);

		// create the top view cell
		mxCell viewCell = (mxCell) graph.insertVertex(cell1, compDef.getIdentity().toString(), null, 0, 0, 0, 0,
				STYLE_COMPONENT_VIEW);

		// if there are text boxes add them
		Annotation textBoxAnn = compDef.getAnnotation(new QName(uriPrefix, "textBoxes", annPrefix));
		if (textBoxAnn != null) {
			@SuppressWarnings("unchecked")
			List<mxCell> textBoxes = (List<mxCell>) decodeMxGraphObject(textBoxAnn.getStringValue());
			for (mxCell textBox : textBoxes) {
				model.add(viewCell, textBox, 0);
			}
		}

		// add the container cell and backbone
		Annotation containerAnn = compDef.getAnnotation(new QName(uriPrefix, "containerCell", annPrefix));
		mxCell container = null;
		mxCell backbone = null;
		if (containerAnn != null) {
			container = (mxCell) decodeMxGraphObject(containerAnn.getStringValue());
			container.setValue(compDef.getIdentity().toString());
			model.add(viewCell, container, 0);
			Annotation backboneAnn = compDef.getAnnotation(new QName(uriPrefix, "backboneCell", annPrefix));
			backbone = (mxCell) decodeMxGraphObject(backboneAnn.getStringValue());
			model.add(container, backbone, 0);
		} else {
			container = (mxCell) graph.insertVertex(viewCell, null, compDef.getIdentity().toString(), 0, 0, 0, 0,
					STYLE_CIRCUIT_CONTAINER);
			backbone = (mxCell) graph.insertVertex(container, null, null, 0, 0, 0, 0, STYLE_BACKBONE);
		}

		// glyphs
		Component[] glyphArray = compDef.getSortedComponents().toArray(new Component[0]);
		for (int glyphIndex = 0; glyphIndex < glyphArray.length; glyphIndex++) {
			Component glyphComponent = glyphArray[glyphIndex];
			Annotation glyphAnn = glyphComponent.getAnnotation(new QName(uriPrefix, "glyphCell", annPrefix));
			mxCell glyphCell = null;
			double maxX = 0;
			if (glyphAnn != null) {
				glyphCell = (mxCell) decodeMxGraphObject(glyphAnn.getStringValue());
				maxX = glyphCell.getGeometry().getX();
				glyphCell.setValue(glyphComponent.getDefinition().getIdentity().toString());
				model.add(container, glyphCell, glyphIndex);
			} else {
				glyphCell = (mxCell) graph.insertVertex(container, null,
						glyphComponent.getDefinition().getIdentity().toString(), maxX++, 0, 0, 0,
						STYLE_SEQUENCE_FEATURE);
			}

			// style flip
			SequenceAnnotation seqAnn = compDef.getSequenceAnnotation(glyphComponent);
			if (seqAnn != null) {
				Location loc = seqAnn.getLocations().iterator().next();
				if (loc.getOrientation() == OrientationType.REVERSECOMPLEMENT) {
					graph.setCellStyles(mxConstants.STYLE_DIRECTION, "west", new Object[] { glyphCell });
				}
			}

		}
	}

	private mxGraph parseGraph(InputStream graphStream) throws IOException {
		mxGraph graph = new mxGraph();
		Document document = mxXmlUtils.parseXml(mxUtils.readInputStream(graphStream));
		mxCodec codec = new mxCodec(document);
		codec.decode(document.getDocumentElement(), graph.getModel());
		return graph;
	}

	private String encodeMxGraphObject(Object obj) throws TransformerFactoryConfigurationError, TransformerException {
		mxCodec codec = new mxCodec();
		Node cellNode = codec.encode(obj);
		StringWriter sw = new StringWriter();
		Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		t.setOutputProperty(OutputKeys.INDENT, "no");
		t.transform(new DOMSource(cellNode), new StreamResult(sw));
		return sw.toString();
	}

	private Object decodeMxGraphObject(String xml) throws SAXException, IOException, ParserConfigurationException {
		Document stringDoc = mxXmlUtils.parseXml(xml);
		mxCodec codec = new mxCodec(stringDoc);
		Node node = stringDoc.getDocumentElement();
		Object obj = codec.decode(node);
		return obj;
	}

	private GlyphInfo genGlyphInfo(ComponentDefinition glyphCD) {
		GlyphInfo glyphInfo = new GlyphInfo();
		glyphInfo.setDescription(glyphCD.getDescription());
		glyphInfo.setDisplayID(glyphCD.getDisplayId());
		glyphInfo.setName(glyphCD.getName());

		// There will only be one visual related role
		ArrayList<String> otherRoles = new ArrayList<String>();
		for (URI glyphRole : glyphCD.getRoles()) {
			if (SBOLData.roles.containsValue(glyphRole)) {
				glyphInfo.setPartRole(SBOLData.roles.getKey(glyphRole));
			} else if (SBOLData.refinements.containsValue(glyphRole)) {
				glyphInfo.setPartRole(SBOLData.roles.getKey(SBOLData.parents.get(glyphRole)));
				glyphInfo.setPartRefine(SBOLData.refinements.getKey(glyphRole));
			} else {
				otherRoles.add(glyphRole.toString());
			}
		}
		glyphInfo.setOtherRoles(otherRoles.toArray(new String[0]));

		// There will only be one important type
		ArrayList<String> otherTypes = new ArrayList<String>();
		for (URI glyphType : glyphCD.getTypes()) {
			if (SBOLData.types.containsValue(glyphType)) {
				glyphInfo.setPartType(SBOLData.types.getKey(glyphType));
			} else {
				otherTypes.add(glyphType.toString());
			}
		}
		glyphInfo.setOtherTypes(otherTypes.toArray(new String[0]));

		if (glyphCD.getSequences().size() > 0)
			glyphInfo.setSequence(glyphCD.getSequences().iterator().next().getElements());
		glyphInfo.setVersion(glyphCD.getVersion());
		String identity = glyphCD.getIdentity().toString();
		int lastIndex = 0;
		if (glyphInfo.getVersion() != null)
			lastIndex = identity.lastIndexOf(glyphInfo.getDisplayID() + "/" + glyphInfo.getVersion());
		else
			lastIndex = identity.lastIndexOf(glyphInfo.getDisplayID());
		glyphInfo.setUriPrefix(identity.substring(0, lastIndex - 1));
		// glyphInfo.setUriPrefix(uriPrefix.substring(0, uriPrefix.length() - 1));
		return glyphInfo;
	}

	private InteractionInfo genInteractionInfo(Interaction interaction) {
		InteractionInfo info = new InteractionInfo();
		info.setDisplayID(interaction.getDisplayId());
		info.setInteractionType(SBOLData.interactions.getKey(interaction.getTypes().iterator().next()));
		return info;
	}

	private URI getParticipantType(boolean source, Set<URI> interactionTypes) {
		if (interactionTypes.contains(SystemsBiologyOntology.BIOCHEMICAL_REACTION)) {
			return source ? SystemsBiologyOntology.REACTANT : SystemsBiologyOntology.PRODUCT;
		} else if (interactionTypes.contains(SystemsBiologyOntology.CONTROL)) {
			return source ? SystemsBiologyOntology.MODIFIER : SystemsBiologyOntology.MODIFIED;
		} else if (interactionTypes.contains(SystemsBiologyOntology.DEGRADATION)) {
			return SystemsBiologyOntology.REACTANT;
		} else if (interactionTypes.contains(SystemsBiologyOntology.GENETIC_PRODUCTION)) {
			return source ? SystemsBiologyOntology.TEMPLATE : SystemsBiologyOntology.PRODUCT;
		} else if (interactionTypes.contains(SystemsBiologyOntology.INHIBITION)) {
			return source ? SystemsBiologyOntology.INHIBITOR : SystemsBiologyOntology.INHIBITED;
		} else if (interactionTypes.contains(SystemsBiologyOntology.NON_COVALENT_BINDING)) {
			return source ? SystemsBiologyOntology.REACTANT : SystemsBiologyOntology.PRODUCT;
		} else if (interactionTypes.contains(SystemsBiologyOntology.STIMULATION)) {
			return source ? SystemsBiologyOntology.STIMULATOR : SystemsBiologyOntology.STIMULATED;
		}
		return null;
	}

	private void createGraphicalLayout(mxGraph graph, mxCell cell, URI reference)
			throws SBOLValidationException, URISyntaxException {
		Map<String, Object> styles = graph.getCellStyle(cell);

		if (cell.isVertex()) {

			List<Annotation> annList = new ArrayList<Annotation>();

			// positional
			mxGeometry cellGeometry = cell.getGeometry();
			annList.add(new Annotation(new QName(uriPrefix, "x", annPrefix), cellGeometry.getX()));
			annList.add(new Annotation(new QName(uriPrefix, "y", annPrefix), cell.getGeometry().getY()));
			annList.add(new Annotation(new QName(uriPrefix, "width", annPrefix), cellGeometry.getWidth()));
			annList.add(new Annotation(new QName(uriPrefix, "height", annPrefix), cellGeometry.getHeight()));

			// styling
			String strokeColor = (String) styles.get(mxConstants.STYLE_STROKECOLOR);
			if (strokeColor != null)
				annList.add(
						new Annotation(new QName(uriPrefix, "strokeColor", annPrefix), strokeColor));

			String strokeOpacity = (String) styles.get(mxConstants.STYLE_STROKE_OPACITY);
			if (strokeOpacity != null)
				annList.add(new Annotation(new QName(uriPrefix, "strokeOpacity", annPrefix),
						strokeOpacity));

			String strokeWidth = (String) styles.get(mxConstants.STYLE_STROKEWIDTH);
			if (strokeWidth != null)
				annList.add(
						new Annotation(new QName(uriPrefix, "strokeWidth", annPrefix), strokeWidth));

			String fillColor = (String) styles.get(mxConstants.STYLE_FILLCOLOR);
			if (fillColor != null)
				annList.add(new Annotation(new QName(uriPrefix, "fillColor", annPrefix), fillColor));

			String fillOpacity = (String) styles.get(mxConstants.STYLE_FILL_OPACITY);
			if (fillOpacity != null)
				annList.add(
						new Annotation(new QName(uriPrefix, "fillOpacity", annPrefix), fillOpacity));

			String fontColor = (String) styles.get(mxConstants.STYLE_FONTCOLOR);
			if (fontColor != null)
				annList.add(new Annotation(new QName(uriPrefix, "fontColor", annPrefix), fontColor));

			String fontSize = (String) styles.get(mxConstants.STYLE_FONTSIZE);
			if (fontSize != null)
				annList.add(new Annotation(new QName(uriPrefix, "fontSize", annPrefix), fontSize));

			if (cell.getStyle().contains(STYLE_TEXTBOX))
				annList.add(new Annotation(new QName(uriPrefix, "text", annPrefix), (String) cell.getValue()));

			// annList.add(new Annotation(new QName(uriPrefix, "reference", annPrefix),
			// reference));

			Annotation nodeGlyph = layout.createAnnotation(new QName(uriPrefix, "NodeGlyph", annPrefix),
					new QName(uriPrefix, "attributes", annPrefix), "attributes", annList);
			nodeGlyph.setNestedIdentity(reference);

		} else if (cell.isEdge()) {

			List<Annotation> annList = new ArrayList<Annotation>();

			// styling
			String strokeColor = (String) styles.get(mxConstants.STYLE_STROKECOLOR);
			if (strokeColor != null)
				annList.add(
						new Annotation(new QName(uriPrefix, "strokeColor", annPrefix), strokeColor));

			String strokeOpacity = (String) styles.get(mxConstants.STYLE_STROKE_OPACITY);
			if (strokeOpacity != null)
				annList.add(new Annotation(new QName(uriPrefix, "strokeOpacity", annPrefix),
						strokeOpacity));

			String strokeWidth = (String) styles.get(mxConstants.STYLE_STROKEWIDTH);
			if (strokeWidth != null)
				annList.add(
						new Annotation(new QName(uriPrefix, "strokeWidth", annPrefix), strokeWidth));

			String arrowSize = (String) styles.get(mxConstants.STYLE_ENDSIZE);
			if (arrowSize != null)
				annList.add(new Annotation(new QName(uriPrefix, "endSize", annPrefix), arrowSize));

			String sourceMargin = (String) styles.get(mxConstants.STYLE_SOURCE_PERIMETER_SPACING);
			if (sourceMargin != null)
				annList.add(new Annotation(new QName(uriPrefix, "sourceSpacing", annPrefix),
						sourceMargin));

			String targetMargin = (String) styles.get(mxConstants.STYLE_TARGET_PERIMETER_SPACING);
			if (targetMargin != null)
				annList.add(new Annotation(new QName(uriPrefix, "targetSpacing", annPrefix),
						targetMargin));

			String edgeStyle = (String) styles.get(mxConstants.STYLE_EDGE);
			if (edgeStyle != null)
				annList.add(new Annotation(new QName(uriPrefix, "edge", annPrefix), edgeStyle));

			String rounded = (String) styles.get(mxConstants.STYLE_ROUNDED);
			if (rounded != null)
				annList.add(new Annotation(new QName(uriPrefix, "rounded", annPrefix),
						Integer.parseInt(rounded) == 1));

			String curved = (String) styles.get("curved");
			if (curved != null)
				annList.add(new Annotation(new QName(uriPrefix, "curved", annPrefix), Integer.parseInt(curved) == 1));

			// annList.add(new Annotation(new QName(uriPrefix, "reference", annPrefix),
			// reference));

			Annotation edgeGlyphAnn = layout.createAnnotation(new QName(uriPrefix, "EdgeGlyph", annPrefix),
					new QName(uriPrefix, "attributes", annPrefix), "attributes", annList);
			edgeGlyphAnn.setNestedIdentity(reference);

			// positional
			mxGeometry geometry = cell.getGeometry();
			if (geometry.getSourcePoint() != null) {
				List<Annotation> sourcePointAnns = new ArrayList<Annotation>();
				sourcePointAnns
						.add(new Annotation(new QName(uriPrefix, "x", annPrefix), geometry.getSourcePoint().getX()));
				sourcePointAnns
						.add(new Annotation(new QName(uriPrefix, "y", annPrefix), geometry.getSourcePoint().getY()));
				edgeGlyphAnn.createAnnotation(new QName(uriPrefix, "sourcePoint", annPrefix),
						new QName(uriPrefix, "attributes", annPrefix), "attributes", sourcePointAnns);
			}
			if (geometry.getTargetPoint() != null) {
				List<Annotation> targetPointAnns = new ArrayList<Annotation>();
				targetPointAnns
						.add(new Annotation(new QName(uriPrefix, "x", annPrefix), geometry.getTargetPoint().getX()));
				targetPointAnns
						.add(new Annotation(new QName(uriPrefix, "y", annPrefix), geometry.getTargetPoint().getY()));
				edgeGlyphAnn.createAnnotation(new QName(uriPrefix, "targetPoint", annPrefix),
						new QName(uriPrefix, "attributes", annPrefix), "attributes", targetPointAnns);
			}
			if (geometry.getPoints() != null && geometry.getPoints().size() > 0) {
				for (mxPoint point : geometry.getPoints()) {
					List<Annotation> pointAnns = new ArrayList<Annotation>();
					pointAnns.add(new Annotation(new QName(uriPrefix, "x", annPrefix), point.getX()));
					pointAnns.add(new Annotation(new QName(uriPrefix, "y", annPrefix), point.getY()));
					edgeGlyphAnn.createAnnotation(new QName(uriPrefix, "point", annPrefix),
							new QName(uriPrefix, "attributes", annPrefix), "attributes", pointAnns);
				}
			}
		}
	}

	private mxCell getGraphicalLayout(mxGraph graph, URI refference) {
		mxCell cell = new mxCell();
		List<Annotation> annotations = layout.getAnnotations();
		Annotation ann = null;
		for (Annotation annotation : annotations) {
			if (annotation.getNestedIdentity().equals(refference)) {
				ann = annotation;
				break;
			}
		}
		if(ann == null)
			return null;

		mxCell[] cellArr = {cell};
		cell.setGeometry(new mxGeometry());
		if (ann.getQName().getLocalPart().equals("NodeGlyph")) {			
			for (Annotation attributeAnn : ann.getAnnotations()) {
				String value = attributeAnn.getStringValue();
				switch (attributeAnn.getQName().getLocalPart()) {
				case "x":
					cell.getGeometry().setX(Double.parseDouble(value)); break;
				case "y":
					cell.getGeometry().setY(Double.parseDouble(value)); break;
				case "width":
					cell.getGeometry().setWidth(Double.parseDouble(value)); break;
				case "height":
					cell.getGeometry().setHeight(Double.parseDouble(value)); break;
				case "strokeColor":
					graph.setCellStyles(mxConstants.STYLE_STROKECOLOR, value, cellArr); break;
				case "strokeOpacity":
					graph.setCellStyles(mxConstants.STYLE_STROKE_OPACITY, value, cellArr); break;
				case "strokeWidth":
					graph.setCellStyles(mxConstants.STYLE_STROKEWIDTH, value, cellArr); break;
				case "fillColor":
					graph.setCellStyles(mxConstants.STYLE_FILLCOLOR, value, cellArr); break;
				case "fillOpacity":
					graph.setCellStyles(mxConstants.STYLE_FILL_OPACITY, value, cellArr); break;
				case "fontColor":
					graph.setCellStyles(mxConstants.STYLE_FONTCOLOR, value, cellArr); break;
				case "fontSize":
					graph.setCellStyles(mxConstants.STYLE_FONTSIZE, value, cellArr); break;
				case "text":
					cell.setValue(value); break;
				}
			}
		} else if (ann.getQName().getLocalPart().equals("EdgeGlyph")) {
			for(Annotation attributeAnn : ann.getAnnotations()) {
				String value = attributeAnn.getStringValue();
				switch(attributeAnn.getQName().getLocalPart()) {
				case "strokeColor":
					graph.setCellStyles(mxConstants.STYLE_STROKECOLOR, value, cellArr); break;
				case "strokeOpacity":
					graph.setCellStyles(mxConstants.STYLE_STROKE_OPACITY, value, cellArr); break;
				case "strokeWidth":
					graph.setCellStyles(mxConstants.STYLE_STROKEWIDTH, value, cellArr); break;
				case "endSize":
					graph.setCellStyles(mxConstants.STYLE_ENDSIZE, value, cellArr); break;
				case "sourceSpacing":
					graph.setCellStyles(mxConstants.STYLE_SOURCE_PERIMETER_SPACING,  value, cellArr); break;
				case "targetSpacing":
					graph.setCellStyles(mxConstants.STYLE_TARGET_PERIMETER_SPACING, value, cellArr); break;
				case "edge":
					graph.setCellStyles(mxConstants.STYLE_EDGE, value, cellArr); break;
				case "rounded":
					graph.setCellStyles(mxConstants.STYLE_ROUNDED, value, cellArr); break;
				case "curved":
					graph.setCellStyles("curved", value, cellArr); break;
				case "sourcePoint":
					mxPoint sourcePoint = new mxPoint();
					for(Annotation sourceAnn: attributeAnn.getAnnotations()) {
						switch(sourceAnn.getQName().getLocalPart()) {
						case "x":
							sourcePoint.setX(Double.parseDouble(sourceAnn.getStringValue())); break;
						case "y":
							sourcePoint.setY(Double.parseDouble(sourceAnn.getStringValue())); break;
						}
					}
					cell.getGeometry().setSourcePoint(sourcePoint);
					break;
				case "targetPoint":
					mxPoint targetPoint = new mxPoint();
					for(Annotation sourceAnn: attributeAnn.getAnnotations()) {
						switch(sourceAnn.getQName().getLocalPart()) {
						case "x":
							targetPoint.setX(Double.parseDouble(sourceAnn.getStringValue())); break;
						case "y":
							targetPoint.setY(Double.parseDouble(sourceAnn.getStringValue())); break;
						}
					}
					cell.getGeometry().setSourcePoint(targetPoint);
					break;
				case "point":
					mxPoint point = new mxPoint();
					for(Annotation sourceAnn: attributeAnn.getAnnotations()) {
						switch(sourceAnn.getQName().getLocalPart()) {
						case "x":
							point.setX(Double.parseDouble(sourceAnn.getStringValue())); break;
						case "y":
							point.setY(Double.parseDouble(sourceAnn.getStringValue())); break;
						}
					}
					cell.getGeometry().getPoints().add(point);
					break;
				}
			}
		}
		return cell;
	}

}
