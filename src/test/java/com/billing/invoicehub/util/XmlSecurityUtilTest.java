package com.billing.invoicehub.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class XmlSecurityUtilTest {

    private static final String SAFE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><item id=\"1\">Hello</item></root>";

    private static final String XXE_PAYLOAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE root [\n" +
            "  <!ENTITY leak SYSTEM \"http://127.0.0.1:9999/secret\">\n" +
            "]>\n" +
            "<root><item>&leak;</item></root>";

    @Test
    @DisplayName("Safe DocumentBuilderFactory parses valid XML cleanly")
    void safeDocumentBuilderParsesSafeXml() throws Exception {
        DocumentBuilderFactory dbf = XmlSecurityUtil.createSafeDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(SAFE_XML.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(doc);
        assertEquals("root", doc.getDocumentElement().getNodeName());
    }

    @Test
    @DisplayName("Safe DocumentBuilderFactory disallows DOCTYPE declarations (XXE defense)")
    void safeDocumentBuilderDisallowsDoctype() throws Exception {
        DocumentBuilderFactory dbf = XmlSecurityUtil.createSafeDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Exception ex = assertThrows(Exception.class, () ->
                db.parse(new ByteArrayInputStream(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8)))
        );
        assertTrue(ex.getMessage().contains("disallow-doctype-decl") || ex instanceof SAXException,
                "Expected DOCTYPE disallowed error, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Safe SAXParserFactory disallows DOCTYPE declarations (XXE defense)")
    void safeSaxParserDisallowsDoctype() throws Exception {
        SAXParserFactory spf = XmlSecurityUtil.createSafeSAXParserFactory();
        XMLReader reader = spf.newSAXParser().getXMLReader();
        Exception ex = assertThrows(Exception.class, () ->
                reader.parse(new InputSource(new ByteArrayInputStream(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8))))
        );
        assertTrue(ex.getMessage().contains("disallow-doctype-decl") || ex instanceof SAXException,
                "Expected DOCTYPE disallowed error, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Safe XMLInputFactory (StAX) disallows DTD (XXE defense)")
    void safeXmlInputFactoryDisallowsDtd() throws Exception {
        XMLInputFactory xif = XmlSecurityUtil.createSafeXMLInputFactory();
        XMLStreamReader reader = xif.createXMLStreamReader(new ByteArrayInputStream(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> {
            while (reader.hasNext()) {
                reader.next();
            }
        });
    }

    @Test
    @DisplayName("validateXmlSecurity allows benign XML and blocks XXE payloads")
    void validateXmlSecurityWorks() {
        assertDoesNotThrow(() ->
                XmlSecurityUtil.validateXmlSecurity(SAFE_XML.getBytes(StandardCharsets.UTF_8))
        );

        assertThrows(Exception.class, () ->
                XmlSecurityUtil.validateXmlSecurity(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8))
        );
    }
}
