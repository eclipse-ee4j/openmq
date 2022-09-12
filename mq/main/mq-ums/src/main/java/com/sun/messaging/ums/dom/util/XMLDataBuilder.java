/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.messaging.ums.dom.util;

import java.io.ByteArrayOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * This is a utility class to build XML document as a UMS response message.
 *
 */
public class XMLDataBuilder {

    public static final String UMSNS = "https://mq.java.net/ums";

    private static final String UMS_ENVELOPE = "ums";

    /**
     * Get a new instance of UMS xml document.
     *
     * @return a new instance of UMS document.
     */
    public static Document newUMSDocument() {
        Document doc = MyInstance.parser.newDocument();

        Element root = doc.createElementNS(UMSNS, UMS_ENVELOPE);
        doc.appendChild(root);

        return doc;
    }

    /**
     * Get the root element of the UMS XML document.
     *
     * @param doc The document in which to get the root element.
     * @return The root element of the document.
     */
    public static Element getRootElement(Document doc) {
        Element element = doc.getDocumentElement();

        return element;
    }

    /**
     * Add the specified child element to the parent element.
     *
     * @param parent The parent element.
     * @param child The child element.
     * @return The child element.
     */
    public static Node addChildElement(Element parent, Element child) {
        return parent.appendChild(child);
    }

    /**
     * Create a new ums xml element for the specified document.
     *
     * @param doc the doc from which the element is created.
     * @param elementName the name of the xml element.
     * @return the created element.
     */
    public static Element createUMSElement(Document doc, String elementName) {

        Element element = doc.createElementNS(UMSNS, elementName);

        return element;
    }

    /**
     * Set the text value to the specified ums xml element.
     *
     * @param doc the document associated with the element.
     * @param element the element in which the text value is set to.
     * @param value the value to set to the xml element.
     */
    public static void setElementValue(Document doc, Element element, String value) {

        Node node = doc.createTextNode(value);

        element.appendChild(node);
    }

    /**
     * Set the specified attribute name/value to the element.
     */
    public static void setElementAttribute(Element element, String attrName, String attrValue) {

        element.setAttribute(attrName, attrValue);
    }

    /**
     * Transform the specified xml document to a string.
     */
    public static String domToString(Document doc) throws TransformerConfigurationException, TransformerException {

        doc.normalizeDocument();
        DOMSource domSource = new DOMSource(doc);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        StreamResult sr = new StreamResult(baos);

        Transformer transformer = MyInstance.transformerFactory.newTransformer();

        transformer.transform(domSource, sr);

        String xml = baos.toString();

        return xml;
    }

    /**
     * my private singleton objects.
     */
    private static class MyInstance {

        private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        private static DocumentBuilder parser = null;
        private static TransformerFactory transformerFactory = TransformerFactory.newInstance();
        // private static Transformer transformer = null;

        static {

            try {
                parser = factory.newDocumentBuilder();
                // transformer = transformerFactory.newTransformer();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
