package org.goobi.production.plugin.CataloguePlugin.ModsPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.ImportException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.fileformats.mets.MetsModsImportExport;
import ugh.fileformats.mets.PersonalNamespaceContext;

public class MetsModsKalliopeImport extends MetsModsImportExport {

	protected static final String METS_PREFS_NODE_NAME_STRING = "KALLIOPE_MODS";

	public MetsModsKalliopeImport(Prefs inPrefs) throws PreferencesException {
		super(inPrefs);
	}

	@Override
	protected DocStruct checkForAnchorReference(String inMods, String filename, String topAnchorClassName){
		return null;
	}

	@Override
	protected void parseMODS(Node inMods, DocStruct inStruct) throws ReadException, ClassNotFoundException, InstantiationException,
		IllegalAccessException {

	    // Document in DOM tree which represents the MODS.
	    Document modsDocument = null;

	    DOMImplementationRegistry registry = null;
	    registry = DOMImplementationRegistry.newInstance();

	    DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");

	    // Test, if the needed DOMImplementation (DOM 3!) is available, else
	    // throw Exception. We are using Xerxes here!
	    if (impl == null) {
	        String message =
	                "There is NO implementation of DOM3 in your ClassPath! We are using Xerxes here, I have no idea why that's not available!";
	        LOGGER.error(message);
	        System.err.println(message);
	        throw new UnsupportedOperationException(message);
	    }
	    LSSerializer writer = impl.createLSSerializer();

	    // Get string for MODS.
	    String modsstr = writer.writeToString(inMods);

	    // Parse MODS section; create a DOM tree just for the MODS from the
	    // string new document builder instance.
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

	    // Do not validate xml file (for we want to store unfinished files, too)
	    factory.setValidating(false);
	    // Namespace does not matter.
	    factory.setNamespaceAware(true);

	    Reader r = new StringReader(modsstr);

	    // Read file and parse it.
	    try {
	        DocumentBuilder builder = factory.newDocumentBuilder();
	        InputSource is = new InputSource();
	        is.setCharacterStream(r);

	        modsDocument = builder.parse(is);
	    } catch (SAXParseException e) {
	        // Error generated by the parser.
	        String message = "Parse error on line: " + e.getLineNumber() + ", uri: " + e.getSystemId();
	        LOGGER.error(message, e);
	        throw new ReadException(message, e);
	    } catch (SAXException e) {
	        // Error generated during parsing.
	        String message = "Exception while parsing METS file! Can't create DOM tree!";
	        LOGGER.error(message, e);
	        throw new ReadException(message, e);
	    } catch (ParserConfigurationException e) {
	        // Parser with specified options can't be built.
	        String message = "XML parser not configured correctly!";
	        LOGGER.error(message, e);
	        throw new ReadException(message, e);
	    } catch (IOException e) {
	        String message = "Exception while parsing METS file! Can't create DOM tree!";
	        LOGGER.error(message, e);
	        throw new ReadException(message, e);
	    }

	    LOGGER.trace("\n" + LINE + "\nMODS\n" + LINE + "\n" + modsstr + "\n" + LINE);

	    // Result of XQuery.
	    Object xqueryresult = null;

	    // Create XQuery.
	    XPathFactory xpathfactory = XPathFactory.newInstance();

	    // New namespace context.
	    PersonalNamespaceContext pnc = new PersonalNamespaceContext();
	    pnc.setNamespaceHash(this.namespaces);
	    XPath xpath = xpathfactory.newXPath();
	    xpath.setNamespaceContext(pnc);

	    // Get the first element; this is where we start with out XPATH.
	    Node startingNode = null;
	    NodeList nl = modsDocument.getChildNodes();
	    if (nl.getLength() > 0) {
	        for (int i = 0; i < nl.getLength(); i++) {
	            Node n = nl.item(i);
	            if (n.getNodeType() == ELEMENT_NODE) {
	                startingNode = n;
	            }
	        }
	    }

	    //
	    // Only look for Goobi internal MODS metadata extensions in the MODS
	    // data here, used for internal METS file reading.
	    //
	    XPathExpression expr;
		try {
			expr = xpath.compile(GOOBI_INTERNAL_METADATA_XPATH);
			xqueryresult = expr.evaluate(startingNode, XPathConstants.NODESET);
		} catch (XPathExpressionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	    LOGGER.debug("Query expression: " + GOOBI_INTERNAL_METADATA_XPATH);

	    // Get metadata node and handle Goobi extension metadata (and persons).
	    NodeList metadataAndPersonNodes = (NodeList) xqueryresult;
	    if (metadataAndPersonNodes != null) {
	        for (int i = 0; i < metadataAndPersonNodes.getLength(); i++) {

	            Node metabagu = metadataAndPersonNodes.item(i);
	            if (metabagu.getNodeType() == ELEMENT_NODE && metabagu.getAttributes().getNamedItem("anchorId") == null
	                    && metabagu.getAttributes().getNamedItem("type") == null) {
	                String name = metabagu.getAttributes().getNamedItem("name").getNodeValue();
	                String value = metabagu.getTextContent();

	                LOGGER.debug("Metadata '" + name + "' with value '" + value + "' found in Goobi's MODS extension");

	                // Check if metadata exists in prefs.
	                MetadataType mdt = this.myPreferences.getMetadataTypeByName(name);
	                if (mdt == null) {
	                    // No valid metadata type found.
	                    String message =
	                            "Can't find internal Metadata with name '" + name + "' for DocStruct '" + inStruct.getType().getName() + "' in prefs";
	                    LOGGER.error(message);
	                    throw new ImportException(message);
	                }

	                // Create and add metadata.
	                try {
	                    Metadata md = new Metadata(mdt);
	                    md.setValue(value);
	                    if (metabagu.getAttributes().getNamedItem("authority") != null && metabagu.getAttributes().getNamedItem("authorityURI") != null && metabagu.getAttributes().getNamedItem("valueURI") != null) {
	                        String authority =  metabagu.getAttributes().getNamedItem("authority").getNodeValue();
	                        String authorityURI = metabagu.getAttributes().getNamedItem("authorityURI").getNodeValue();
	                        String valueURI = metabagu.getAttributes().getNamedItem("valueURI").getNodeValue();
	                        md.setAutorityFile(authority, authorityURI, valueURI);
	                     }

	                    inStruct.addMetadata(md);

	                    LOGGER.debug("Added metadata '" + mdt.getName() + "' to DocStruct '" + inStruct.getType().getName() + "' with value '"
	                            + value + "'");
	                } catch (DocStructHasNoTypeException e) {
	                    String message = "DocumentStructure for which metadata should be added, has no type!";
	                    LOGGER.error(message, e);
	                    throw new ImportException(message, e);
	                } catch (MetadataTypeNotAllowedException e) {
	                    String message =
	                            "Metadata '" + mdt.getName() + "' (" + value + ") is not allowed as a child for '" + inStruct.getType().getName()
	                                    + "' during MODS import!";
	                    LOGGER.error(message, e);
	                    throw new ImportException(message, e);
	                }
	            }

	            if (metabagu.getNodeType() == ELEMENT_NODE && metabagu.getAttributes().getNamedItem("anchorId") == null
	                    && metabagu.getAttributes().getNamedItem("type") != null
	                    && metabagu.getAttributes().getNamedItem("type").getTextContent().equals("group")) {
	                String groupName = metabagu.getAttributes().item(0).getTextContent();
	                // Check if group exists in prefs.
	                MetadataGroupType mgt = this.myPreferences.getMetadataGroupTypeByName(groupName);

	                if (mgt == null) {
	                    // No valid metadata type found.
	                    String message =
	                            "Can't find internal Metadata with name '" + groupName + "' for DocStruct '" + inStruct.getType().getName()
	                                    + "' in prefs";
	                    LOGGER.error(message);
	                    throw new ImportException(message);
	                }
	                // Create and add group.
	                try {
	                    MetadataGroup metadataGroup = new MetadataGroup(mgt);

	                    inStruct.addMetadataGroup(metadataGroup);

	                    NodeList metadataNodelist = metabagu.getChildNodes();
	                    for (int j = 0; j < metadataNodelist.getLength(); j++) {
	                        Node metadata = metadataNodelist.item(j);

	                        // metadata
	                        if (metadata.getNodeType() == ELEMENT_NODE && metadata.getAttributes().getNamedItem("type") == null) {

	                            String metadataName = metadata.getAttributes().getNamedItem("name").getTextContent();
	                            String value = metadata.getTextContent();
	                            String authority = null;
	                            String authorityURI = null;
	                            String valueURI = null;
	                            if (metadata.getAttributes().getNamedItem("authority") != null && metadata.getAttributes().getNamedItem("authorityURI") != null && metadata.getAttributes().getNamedItem("valueURI") != null) {
	                                authority =  metadata.getAttributes().getNamedItem("authority").getNodeValue();
	                                authorityURI = metadata.getAttributes().getNamedItem("authorityURI").getNodeValue();
	                                valueURI = metadata.getAttributes().getNamedItem("valueURI").getNodeValue();
	                            }

	                            List<Metadata> metadataList = new ArrayList<Metadata>(metadataGroup.getMetadataList());
	                            for (Metadata meta : metadataList) {
	                                if (meta.getType().getName().equals(metadataName)) {
	                                    if (meta.getValue() == null || meta.getValue().isEmpty()) {
	                                        meta.setValue(value);
	                                        if (authority != null && authorityURI != null && valueURI != null) {
	                                            meta.setAutorityFile(authority, authorityURI, valueURI);
	                                        }
	                                        break;
	                                    } else {
	                                        Metadata mdnew = new Metadata(meta.getType());
	                                        mdnew.setValue(value);
	                                        if (authority != null && authorityURI != null && valueURI != null) {
	                                            mdnew.setAutorityFile(authority, authorityURI, valueURI);
	                                        }
	                                        metadataGroup.addMetadata(mdnew);
	                                    }
	                                }
	                            }
	                        }

	                        // person
	                        else if (metadata.getNodeType() == ELEMENT_NODE && metadata.getAttributes().getNamedItem("type") != null
	                                && metadata.getAttributes().getNamedItem("type").getTextContent().equals("person")) {

	                            String role = metadata.getAttributes().item(0).getTextContent();
	                            MetadataType mdt = this.myPreferences.getMetadataTypeByName(role);
	                            if (mdt == null) {
	                                // No valid metadata type found.
	                                String message = "Can't find person with name '" + role + "' in prefs";
	                                LOGGER.error(message);
	                                throw new ImportException(message);
	                            }

	                            // Create and add person.
	                            if (mdt.getIsPerson()) {
	                                List<Person> metadataList = new ArrayList<Person>(metadataGroup.getPersonList());
	                                for (Person ps : metadataList) {

	                                    if (ps.getType().getName().equals(mdt.getName())) {
	                                        if ((ps.getLastname() == null || ps.getLastname().isEmpty())
	                                                && (ps.getFirstname() == null || ps.getFirstname().isEmpty())) {

	                                            ps.setRole(mdt.getName());

	                                        } else {
	                                            ps = new Person(mdt);
	                                            ps.setRole(mdt.getName());
	                                            metadataGroup.addPerson(ps);
	                                        }
	                                        // Iterate over every person's data.
	                                        NodeList personNodelist = metadata.getChildNodes();
	                                        String authorityID = null;
	                                        String authorityURI = null;
	                                        String authortityValue = null;
	                                        for (int k = 0; k < personNodelist.getLength(); k++) {

	                                            Node personbagu = personNodelist.item(k);
	                                            if (personbagu.getNodeType() == ELEMENT_NODE) {
	                                                String name = personbagu.getLocalName();
	                                                String value = personbagu.getTextContent();


	                                                // Get and set values.
	                                                if (name.equals(GOOBI_PERSON_FIRSTNAME_STRING)) {
	                                                    ps.setFirstname(value);
	                                                }
	                                                if (name.equals(GOOBI_PERSON_LASTNAME_STRING)) {
	                                                    ps.setLastname(value);
	                                                }
	                                                if (name.equals(GOOBI_PERSON_AFFILIATION_STRING)) {
	                                                    ps.setAffiliation(value);
	                                                }
	                                                if (name.equals(GOOBI_PERSON_AUTHORITYID_STRING)) {
	                                                    authorityID =value;
	                                                }
	                                                if (name.equals(GOOBI_PERSON_AUTHORITYURI_STRING)) {
	                                                    authorityURI =value;
	                                                }
	                                                if (name.equals(GOOBI_PERSON_AUTHORITYVALUE_STRING)) {
	                                                    authortityValue =value;
	                                                }

	                                                if (name.equals(GOOBI_PERSON_PERSONTYPE_STRING)) {
	                                                    ps.setPersontype(value);
	                                                }
	                                                if (name.equals(GOOBI_PERSON_DISPLAYNAME_STRING)) {
	                                                    ps.setDisplayname(value);
	                                                }
	                                            }

	                                        }
	                                        if (authorityID != null && authorityURI != null && authortityValue != null) {
	                                            ps.setAutorityFile(authorityID, authorityURI, authortityValue);
	                                        }
	                                    }
	                                }
	                            }
	                        }

	                    }

	                    LOGGER.debug("Added metadataGroup '" + mgt.getName() + "' to DocStruct '" + inStruct.getType().getName() + "'");

	                } catch (DocStructHasNoTypeException e) {
	                    String message = "DocumentStructure for which metadata should be added, has no type!";
	                    LOGGER.error(message, e);
	                    throw new ImportException(message, e);

	                } catch (MetadataTypeNotAllowedException e) {
	                    String message =
	                            "MetadataGroup '" + mgt.getName() + "' is not allowed as a child for '" + inStruct.getType().getName()
	                                    + "' during MODS import!";
	                    LOGGER.error(message, e);
	                    throw new ImportException(message, e);
	                }
	            }

	            // We have a person node here!
	            if (metabagu.getNodeType() == ELEMENT_NODE && metabagu.getAttributes().getNamedItem("anchorId") == null
	                    && metabagu.getAttributes().getNamedItem("type") != null
	                    && metabagu.getAttributes().getNamedItem("type").getTextContent().equals("person")) {
	                String role = metabagu.getAttributes().item(0).getTextContent();

	                LOGGER.debug("Person metadata '" + role + "' found in Goobi's MODS extension");

	                // Ccheck if person does exist in prefs.
	                MetadataType mdt = this.myPreferences.getMetadataTypeByName(role);
	                if (mdt == null) {
	                    // No valid metadata type found.
	                    String message = "Can't find person with name '" + role + "' in prefs";
	                    LOGGER.error(message);
	                    throw new ImportException(message);
	                }

	                // Create and add person.
	                if (mdt.getIsPerson()) {
	                    Person ps;
	                    try {
	                        ps = new Person(mdt);
	                    } catch (MetadataTypeNotAllowedException e) {
	                        String message = "Can't add person! MetadataType must not be null!";
	                        LOGGER.error(message, e);
	                        throw new ReadException(message, e);
	                    }
	                    ps.setRole(mdt.getName());

	                    // Iterate over every person's data.
	                    NodeList personNodelist = metabagu.getChildNodes();
	                    String authorityFileID= null;
	                    String authorityURI = null;
	                    String authortityValue= null;
	                    for (int j = 0; j < personNodelist.getLength(); j++) {

	                        Node personbagu = personNodelist.item(j);
	                        if (personbagu.getNodeType() == ELEMENT_NODE) {
	                            String name = personbagu.getLocalName();
	                            String value = personbagu.getTextContent();

	                            // Get and set values.
	                            if (name.equals(GOOBI_PERSON_FIRSTNAME_STRING)) {
	                                ps.setFirstname(value);
	                            }
	                            if (name.equals(GOOBI_PERSON_LASTNAME_STRING)) {
	                                ps.setLastname(value);
	                            }
	                            if (name.equals(GOOBI_PERSON_AFFILIATION_STRING)) {
	                                ps.setAffiliation(value);
	                            }
	                            if (name.equals(GOOBI_PERSON_AUTHORITYID_STRING)) {
	                                authorityFileID =value;
	                            }
	                            if (name.equals(GOOBI_PERSON_AUTHORITYURI_STRING)) {
	                                authorityURI =value;
	                            }
	                            if (name.equals(GOOBI_PERSON_AUTHORITYVALUE_STRING)) {
	                                authortityValue =value;
	                            }

	                            if (name.equals(GOOBI_PERSON_PERSONTYPE_STRING)) {
	                                ps.setPersontype(value);
	                            }
	                            if (name.equals(GOOBI_PERSON_DISPLAYNAME_STRING)) {
	                                ps.setDisplayname(value);
	                            }
	                        }
	                    }
	                    if (authorityFileID != null && authorityURI != null && authortityValue != null) {
	                        ps.setAutorityFile(authorityFileID, authorityURI, authortityValue);
	                    }
	                    try {
	                        inStruct.addPerson(ps);

	                        LOGGER.debug("Added person '" + mdt.getName() + "' to DocStruct '" + inStruct.getType().getName() + "'");
	                    } catch (DocStructHasNoTypeException e) {
	                        String message = "DocumentStructure for which metadata should be added has no type!";
	                        LOGGER.error(message, e);
	                        throw new ImportException(message, e);
	                    } catch (MetadataTypeNotAllowedException e) {
	                        String message =
	                                "Person '" + mdt.getName() + "' " + ps.getDisplayname() + ") is not allowed as a child for '"
	                                        + inStruct.getType().getName() + "' during MODS import!";
	                        LOGGER.error(message, e);
	                        throw new ImportException(message);
	                    }
	                }
	            }
	        }
	    }
	}
}
