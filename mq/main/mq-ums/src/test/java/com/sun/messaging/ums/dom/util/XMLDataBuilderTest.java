/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation.
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

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XMLDataBuilderTest {

    /* Example API usage for XMLDataBuilder. */
    @Test
    void usageExample() throws TransformerConfigurationException, TransformerException {
        // create a new instance of ums xml document.
        Document doc = XMLDataBuilder.newUMSDocument();

        // get the root element
        Element root = XMLDataBuilder.getRootElement(doc);

        // create the first child element
        Element firstChild = XMLDataBuilder.createUMSElement(doc, "firstChild");

        // set text value to the first child
        long ctms1 = System.currentTimeMillis();
        XMLDataBuilder.setElementValue(doc, firstChild, String.valueOf(ctms1));

        // set attribute to the first child
        XMLDataBuilder.setElementAttribute(firstChild, "attr1", "value1");

        // add the first child to the root element
        XMLDataBuilder.addChildElement(root, firstChild);

        // create second child element
        Element secondChild = XMLDataBuilder.createUMSElement(doc, "secondChild");

        // set element text value
        long ctms2 = System.currentTimeMillis();
        XMLDataBuilder.setElementValue(doc, secondChild, String.valueOf(ctms2));

        // set attribute to the second child
        XMLDataBuilder.setElementAttribute(secondChild, "attr2", "value2");

        // add second child to the root element.
        XMLDataBuilder.addChildElement(root, secondChild);

        // transform xml document to a string
        String xml = XMLDataBuilder.domToString(doc);

        String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"
                + "<ums xmlns=\"https://mq.java.net/ums\">"
                + "<firstChild attr1=\"value1\">" + ctms1 + "</firstChild>"
                + "<secondChild attr2=\"value2\">" + ctms2 + "</secondChild>"
                        + "</ums>";
        assertThat(xml).isEqualTo(expectedXml);
    }
}
