package com.billing.invoicehub.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to provide secure XML parser configurations with external entities disabled (XXE defense).
 * Complies with OWASP Cheat Sheet recommendations for XML External Entity (XXE) Prevention.
 */
public final class XmlSecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(XmlSecurityUtil.class);

    private XmlSecurityUtil() {
    }

    /**
     * Creates and configures a secure DocumentBuilderFactory with DOCTYPE and external entities disabled.
     */
    public static DocumentBuilderFactory createSafeDocumentBuilderFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException | IllegalArgumentException e) {
            log.warn("Warning configuring secure DocumentBuilderFactory features: {}", e.getMessage());
        }
        return dbf;
    }

    /**
     * Creates and configures a secure SAXParserFactory with DOCTYPE and external entities disabled.
     */
    public static SAXParserFactory createSafeSAXParserFactory() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        try {
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            spf.setXIncludeAware(false);
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException e) {
            log.warn("Warning configuring secure SAXParserFactory features: {}", e.getMessage());
        }
        return spf;
    }

    /**
     * Creates and configures a secure XMLInputFactory (StAX) with DTD and external entities disabled.
     */
    public static XMLInputFactory createSafeXMLInputFactory() {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            xif.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            xif.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException e) {
            log.warn("Warning configuring secure XMLInputFactory properties: {}", e.getMessage());
        }
        return xif;
    }

    /**
     * Creates and configures a secure TransformerFactory with external stylesheet and DTD disabled.
     */
    public static TransformerFactory createSafeTransformerFactory() {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException e) {
            log.warn("Warning configuring secure TransformerFactory attributes: {}", e.getMessage());
        }
        return tf;
    }

    /**
     * Safely parses an XML file into a DOM Document with external entities disabled.
     */
    public static Document parseSafeDocument(File file) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = createSafeDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(file);
    }

    /**
     * Safely parses an XML InputStream into a DOM Document with external entities disabled.
     */
    public static Document parseSafeDocument(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = createSafeDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(is);
    }

    /**
     * Validates that the provided XML input stream does not contain DOCTYPE or external entities.
     * Throws SAXException if DOCTYPE or malicious external entities are present.
     */
    public static void validateXmlSecurity(InputStream is) throws Exception {
        SAXParserFactory spf = createSafeSAXParserFactory();
        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(new DefaultHandler());
        xmlReader.parse(new InputSource(is));
    }

    /**
     * Validates that the provided XML byte array does not contain DOCTYPE or external entities.
     * Throws SAXException if DOCTYPE or malicious external entities are present.
     */
    public static void validateXmlSecurity(byte[] xmlBytes) throws Exception {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(xmlBytes)) {
            validateXmlSecurity(bais);
        }
    }
}
