/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.docket;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.jaxen.JaxenException;
import org.jaxen.jdom.JDOMXPath;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.kitodo.api.docket.DocketData;
import org.kitodo.api.docket.Property;
import org.kitodo.config.KitodoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides xml logfile generation. After the generation the file
 * will be written to user home directory
 *
 * @author Robert Sehr
 * @author Steffen Hankiewicz
 *
 */
public class ExportXmlLog {
    private static final Logger logger = LoggerFactory.getLogger(ExportXmlLog.class);
    private static final String LABEL = "label";
    private static final String NAMESPACE = "http://www.kitodo.org/logfile";
    private static final String PROPERTIES = "properties";
    private static final String PROPERTY = "property";
    private static final String PROPERTY_IDENTIFIER = "propertyIdentifier";
    private static final String VALUE = "value";

    /**
     * This method exports the production metadata as xml to a given stream.
     *
     * @param docketData
     *            the docket data to export
     * @param os
     *            the OutputStream to write the contents to
     * @throws IOException
     *             Throws IOException, when document creation fails.
     */
    static void startExport(DocketData docketData, OutputStream os) throws IOException {
        try {
            Document doc = createDocument(docketData, true);

            XMLOutputter outp = new XMLOutputter();
            outp.setFormat(Format.getPrettyFormat());

            outp.output(doc, os);
            os.close();

        } catch (RuntimeException e) {
            logger.error("Document creation failed.");
            throw new IOException(e);
        }
    }

    /**
     * This method exports the production metadata for al list of processes as a
     * single file to a given stream.
     *
     * @param docketDataList
     *            a list of Docket data
     * @param outputStream
     *            The output stream, to write the docket to.
     */

    static void startMultipleExport(Iterable<DocketData> docketDataList, OutputStream outputStream) {
        Document answer = new Document();
        Element root = new Element("processes");
        answer.setRootElement(root);
        Namespace xmlns = Namespace.getNamespace(NAMESPACE);

        Namespace xsi = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.addNamespaceDeclaration(xsi);
        root.setNamespace(xmlns);
        Attribute attSchema = new Attribute("schemaLocation", NAMESPACE + " XML-logfile.xsd",
                xsi);
        root.setAttribute(attSchema);
        for (DocketData docketData : docketDataList) {
            Document doc = createDocument(docketData, false);
            Element processRoot = doc.getRootElement();
            processRoot.detach();
            root.addContent(processRoot);
        }

        XMLOutputter outp = new XMLOutputter(Format.getPrettyFormat());

        try {
            outp.output(answer, outputStream);
        } catch (IOException e) {
            logger.error("Generating XML Output failed.", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("Closing the output stream failed.", e);
                }
            }
        }

    }

    /**
     * This method creates a new xml document with process metadata.
     *
     * @param docketData
     *            the docketData to export
     * @return a new xml document
     */
    private static Document createDocument(DocketData docketData, boolean addNamespace) {

        Element processElm = new Element("process");
        final Document doc = new Document(processElm);

        processElm.setAttribute("processID", String.valueOf(docketData.getProcessId()));

        Namespace xmlns = Namespace.getNamespace(NAMESPACE);
        processElm.setNamespace(xmlns);
        // namespace declaration
        if (addNamespace) {

            Namespace xsi = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            processElm.addNamespaceDeclaration(xsi);
            Attribute attSchema = new Attribute("schemaLocation", NAMESPACE + " XML-logfile.xsd",
                    xsi);
            processElm.setAttribute(attSchema);
        }
        // process information

        ArrayList<Element> processElements = new ArrayList<>();
        Element processTitle = new Element("title", xmlns);
        processTitle.setText(docketData.getProcessName());
        processElements.add(processTitle);

        Element project = new Element("project", xmlns);
        project.setText(docketData.getProjectName());
        processElements.add(project);

        Element date = new Element("time", xmlns);
        date.setAttribute("type", "creation date");
        date.setText(String.valueOf(docketData.getCreationDate()));
        processElements.add(date);

        Element ruleset = new Element("ruleset", xmlns);
        ruleset.setText(docketData.getRulesetName());
        processElements.add(ruleset);

        Element comment = new Element("comment", xmlns);
        comment.setText(docketData.getComment());
        processElements.add(comment);

        List<Element> processProperties = prepareProperties(docketData.getProcessProperties(), xmlns);

        if (!processProperties.isEmpty()) {
            Element properties = new Element(PROPERTIES, xmlns);
            properties.addContent(processProperties);
            processElements.add(properties);
        }

        // template information
        ArrayList<Element> templateElements = new ArrayList<>();
        Element template = new Element("original", xmlns);

        ArrayList<Element> templateProperties = new ArrayList<>();
        if (docketData.getTemplateProperties() != null) {
            for (Property prop : docketData.getTemplateProperties()) {
                Element property = new Element(PROPERTY, xmlns);
                property.setAttribute(PROPERTY_IDENTIFIER, prop.getTitle());
                if (prop.getValue() != null) {
                    property.setAttribute(VALUE, replacer(prop.getValue()));
                } else {
                    property.setAttribute(VALUE, "");
                }

                Element label = new Element(LABEL, xmlns);

                label.setText(prop.getTitle());
                property.addContent(label);

                templateProperties.add(property);
                if (prop.getTitle().equals("Signatur")) {
                    Element secondProperty = new Element(PROPERTY, xmlns);
                    secondProperty.setAttribute(PROPERTY_IDENTIFIER, prop.getTitle() + "Encoded");
                    if (prop.getValue() != null) {
                        secondProperty.setAttribute(VALUE, "vorl:" + replacer(prop.getValue()));
                        Element secondLabel = new Element(LABEL, xmlns);
                        secondLabel.setText(prop.getTitle());
                        secondProperty.addContent(secondLabel);
                        templateProperties.add(secondProperty);
                    }
                }
            }
        }
        if (!templateProperties.isEmpty()) {
            Element properties = new Element(PROPERTIES, xmlns);
            properties.addContent(templateProperties);
            template.addContent(properties);
        }
        templateElements.add(template);

        Element templates = new Element("originals", xmlns);
        templates.addContent(templateElements);
        processElements.add(templates);

        // digital document information
        ArrayList<Element> docElements = new ArrayList<>();
        Element dd = new Element("digitalDocument", xmlns);

        List<Element> docProperties = prepareProperties(docketData.getWorkpieceProperties(), xmlns);

        if (!docProperties.isEmpty()) {
            Element properties = new Element(PROPERTIES, xmlns);
            properties.addContent(docProperties);
            dd.addContent(properties);
        }
        docElements.add(dd);

        Element digdoc = new Element("digitalDocuments", xmlns);
        digdoc.addContent(docElements);
        processElements.add(digdoc);


        // METS information
        Element metsElement = new Element("metsInformation", xmlns);
        List<Element> metadataElements = createMetadataElements(xmlns, docketData);
        metsElement.addContent(metadataElements);
        processElements.add(metsElement);

        processElm.setContent(processElements);
        return doc;
    }

    private static List<Element> prepareProperties(List<Property> properties, Namespace xmlns) {
        ArrayList<Element> preparedProperties = new ArrayList<>();
        for (Property property : properties) {
            Element propertyElement = new Element(PROPERTY, xmlns);
            propertyElement.setAttribute(PROPERTY_IDENTIFIER, property.getTitle());
            if (property.getValue() != null) {
                propertyElement.setAttribute(VALUE, replacer(property.getValue()));
            } else {
                propertyElement.setAttribute(VALUE, "");
            }
    
            Element label = new Element(LABEL, xmlns);
    
            label.setText(property.getTitle());
            propertyElement.addContent(label);
            preparedProperties.add(propertyElement);
        }
        return preparedProperties;
    }

    private static List<Element> createMetadataElements(Namespace xmlns, DocketData docketData) {
        List<Element> metadataElements = new ArrayList<>();
        try {
            HashMap<String, Namespace> namespaces = new HashMap<>();

            HashMap<String, String> names = getNamespacesFromConfig();
            for (Map.Entry<String, String> entry : names.entrySet()) {
                String key = entry.getKey();
                namespaces.put(key, Namespace.getNamespace(key, entry.getValue()));
            }

            prepareMetadataElements(metadataElements, false, docketData, namespaces, xmlns);
            if (Objects.nonNull(docketData.getParent())) {
                prepareMetadataElements(metadataElements, true, docketData.getParent(), namespaces, xmlns);
            }

        } catch (IOException | JDOMException | JaxenException e) {
            logger.error(e.getMessage(), e);
        }
        return metadataElements;
    }

    private static HashMap<String, String> getNamespacesFromConfig() {
        HashMap<String, String> nss = new HashMap<>();
        try {
            File file = new File(KitodoConfig.getKitodoConfigDirectory() + "kitodo_exportXml.xml");
            if (file.exists() && file.canRead()) {
                XMLConfiguration config = new XMLConfiguration(file);
                config.setListDelimiter('&');
                config.setReloadingStrategy(new FileChangedReloadingStrategy());
    
                int count = config.getMaxIndex("namespace");
                for (int i = 0; i <= count; i++) {
                    String name = config.getString("namespace(" + i + ")[@name]");
                    String value = config.getString("namespace(" + i + ")[@value]");
                    nss.put(name, value);
                }
            }
        } catch (ConfigurationException | RuntimeException e) {
            logger.debug(e.getMessage(), e);
            nss = new HashMap<>();
        }
        return nss;
    }

    private static void prepareMetadataElements(List<Element> metadataElements, boolean useAnchor, DocketData docketData,
            HashMap<String, Namespace> namespaces, Namespace xmlns)
            throws IOException, JDOMException, JaxenException {
        HashMap<String, String> fields = getMetsFieldsFromConfig(useAnchor);
        Document metsDoc = new SAXBuilder().build(docketData.metadataFile());
        prepareMetadataElements(metadataElements, fields, metsDoc, namespaces, xmlns);
    }

    private static HashMap<String, String> getMetsFieldsFromConfig(boolean useAnchor) {
        String xmlpath = "mets." + PROPERTY;
        if (useAnchor) {
            xmlpath = "anchor." + PROPERTY;
        }

        HashMap<String, String> fields = new HashMap<>();
        try {
            File file = new File(KitodoConfig.getKitodoConfigDirectory() + "kitodo_exportXml.xml");
            if (file.exists() && file.canRead()) {
                XMLConfiguration config = new XMLConfiguration(file);
                config.setListDelimiter('&');
                config.setReloadingStrategy(new FileChangedReloadingStrategy());

                int count = config.getMaxIndex(xmlpath);
                for (int i = 0; i <= count; i++) {
                    String name = config.getString(xmlpath + "(" + i + ")[@name]");
                    String value = config.getString(xmlpath + "(" + i + ")[@value]");
                    fields.put(name, value);
                }
            }
        } catch (ConfigurationException | RuntimeException e) {
            logger.debug(e.getMessage(), e);
            fields = new HashMap<>();
        }
        return fields;
    }

    private static void prepareMetadataElements(List<Element> metadataElements, Map<String, String> fields, Document document,
            HashMap<String, Namespace> namespaces, Namespace xmlns) throws JaxenException {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            List<Element> metsValues = getMetsValues(entry.getValue(), document, namespaces);
            for (Element element : metsValues) {
                Element ele = new Element(PROPERTY, xmlns);
                ele.setAttribute("name", key);
                ele.addContent(element.getTextTrim());
                metadataElements.add(ele);
            }
        }
    }

    /**
     * Get METS values.
     *
     * @param expr
     *            String
     * @param element
     *            Object
     * @param namespaces
     *            HashMap
     * @return list of elements
     */
    @SuppressWarnings("unchecked")
    private static List<Element> getMetsValues(String expr, Object element,
            HashMap<String, Namespace> namespaces) throws JaxenException {
        JDOMXPath xpath = new JDOMXPath(expr.trim().replace("\n", ""));
        // Add all namespaces
        for (Map.Entry<String, Namespace> entry : namespaces.entrySet()) {
            xpath.addNamespace(entry.getKey(), entry.getValue().getURI());
        }
        return xpath.selectNodes(element);
    }

    private static String replacer(String in) {
        in = in.replace("°", "?");
        in = in.replace("^", "?");
        in = in.replace("|", "?");
        in = in.replace(">", "?");
        in = in.replace("<", "?");
        return in;
    }
}
