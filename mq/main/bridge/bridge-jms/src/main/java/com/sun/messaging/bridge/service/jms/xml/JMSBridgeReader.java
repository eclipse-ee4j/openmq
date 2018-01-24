/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.bridge.service.jms.xml;

import java.io.*;
import java.util.*;
import java.net.URL;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

/**
 * @author amyk
 */
public class JMSBridgeReader extends DefaultHandler {

    private Logger _logger = null;

    private JMSBridgeElement _jmsbridge = null;
    private LinkElement _link = null;
    private DMQElement _dmq = null;
    private TargetElement _target = null;

    private Properties _attrs = null;
    private Properties _props = null;
    private String _refname = null;

    private String _xmlurl = null;


    /**
     */
    public JMSBridgeReader(String url, String sysid, Logger logger) throws Exception {
        _logger =  logger;
        _xmlurl = url;
        parseXML(url, sysid);
    }

    /**
     */
    private void parseXML(String url, String sysid) throws Exception {

        SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(true);
        InputStream is = null;
        try {
            SAXParser parser = factory.newSAXParser();
            is = (new URL(url)).openStream();
            parser.parse(is, this, sysid);

        } catch (Exception e) {
            e.printStackTrace ();
            throw e;
        } finally {
            if (is != null) {
                try {
                is.close();
                } catch (Exception e) {}
            }
        }
    }

    public JMSBridgeElement getJMSBridgeElement() {
        return _jmsbridge;
    }


    /***********************************************************
     * SAX2 DefaultHandler methods
     ***********************************************************/

    public void startDocument () throws SAXException {
        _logger.log(Level.FINE,  "Start parsing "+_xmlurl);
    }

    public void endDocument () throws SAXException {
        _logger.log(Level.FINE,  "End of parsing "+_xmlurl);
    }

    public void startElement(String uri, String localName,
                             String qName, Attributes attributes)
                                            throws SAXException {
        String elemname = qName;
        String name = null;
        String attrName = null;
        String attrValue = null;
        String propName = null;
        String propValue = null;

        _logger.log(Level.FINE, "startElement: uri="+uri+", localName="+localName+
                                 ", qName="+qName+", attributes="+attributes);

        try {

        if (elemname.equals(JMSBridgeXMLConstant.Element.JMSBRIDGE) ||
		    elemname.equals(JMSBridgeXMLConstant.Element.CF) ||
            elemname.equals(JMSBridgeXMLConstant.Element.LINK) ||
            elemname.equals(JMSBridgeXMLConstant.Element.SOURCE) ||
            elemname.equals(JMSBridgeXMLConstant.Element.TARGET) ||
            elemname.equals(JMSBridgeXMLConstant.Element.DESTINATION) ||
            elemname.equals(JMSBridgeXMLConstant.Element.DMQ)) {

            _attrs = new Properties();
            _props = new Properties();

            for (int i = 0; i < attributes.getLength(); i++) {
                attrName = attributes.getQName(i);
                attrValue = attributes.getValue(i);
                _logger.log(Level.FINE, "attr:"+attrName+"="+attrValue);
                _attrs.put(attrName, attrValue);
                if (attrName.equals(JMSBridgeXMLConstant.Common.NAME)) {
                    name = attrValue;
                    JMSBridgeXMLConstant.checkReserved(name);
                } 
                else if (attrName.equals(JMSBridgeXMLConstant.Common.REFNAME)) {
                    _refname = attrValue;
                    JMSBridgeXMLConstant.checkReserved(name);
                }
            }

            if (elemname.equals(JMSBridgeXMLConstant.Element.JMSBRIDGE)) { 
                if (_jmsbridge != null) {
                    throw new SAXNotSupportedException(
                    "Multiple "+JMSBridgeXMLConstant.Element.JMSBRIDGE+
                    " element is not allowed");
                }
                _logger.log(Level.FINE, "jmsbridge: name="+name);
                _jmsbridge = new JMSBridgeElement(); 
                _jmsbridge.setAttributes(_attrs);
            }
            else if (elemname.equals(JMSBridgeXMLConstant.Element.LINK)) { 
                _logger.log(Level.FINE, "new link: name="+name);
                _link = new LinkElement();
                _link.setAttributes(_attrs);
            }
            else if (elemname.equals(JMSBridgeXMLConstant.Element.TARGET)) { 
                _logger.log(Level.FINE, "target");
                _target = new TargetElement();
                _target.setAttributes(_attrs);
            }
            else if (elemname.equals(JMSBridgeXMLConstant.Element.DMQ)) { 
                _logger.log(Level.FINE, "new dmq: name="+name);
                _dmq = new DMQElement();
                _dmq.setAttributes(_attrs);
            }
        } else if (elemname.equals(JMSBridgeXMLConstant.Element.PROPERTY)) {
            propName = attributes.getValue(0);
            propValue = attributes.getValue(1);
            _logger.log(Level.FINE, "prop:"+propName+"="+propValue);
            _props.put(propName, propValue);
        } else if (elemname.equals(JMSBridgeXMLConstant.Element.DESCRIPTION)) {
        } else {
            throw new SAXException("Unknow element "+elemname);
        }

        } catch (Exception e) {
            _logger.log(Level.SEVERE, e.getMessage(), e);
            throw new SAXException(e.getMessage(), e); 
        }
    }

    public void endElement(String uri, String localName, String qName)
                                                 throws SAXException {
        String elemname = qName;

        _logger.log(Level.FINE, "startElement: uri="+uri+", localName="+
                                 localName+", qName="+qName);

        try {

        if (elemname.equals(JMSBridgeXMLConstant.Element.CF)) {
            _logger.log(Level.FINE, "add connection factory: "+_refname);
            ConnectionFactoryElement ecf = new ConnectionFactoryElement();
            ecf.setAttributes(_attrs);
            ecf.setProperties(_props);
            _jmsbridge.addCF(_refname, ecf);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.DESTINATION) ) {
            _logger.log(Level.FINE, "add destination: "+_refname);
            DestinationElement de = new DestinationElement();
            de.setAttributes(_attrs);
            de.setProperties(_props);
            _jmsbridge.addDestination(_refname, de);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.LINK) ) {
            _jmsbridge.addLink(_link);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.DMQ) ) {
            _dmq.setProperties(_props);
            _jmsbridge.addDMQ(_dmq);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.SOURCE) ) {
            _logger.log(Level.FINE, "set source: "+_attrs+" to "+_link);
            _link.setSource(_attrs);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.TARGET) ) {
            _logger.log(Level.FINE, "set target: "+_attrs+" to "+_link);
            _target.setProperties(_props);
            _link.setTarget(_target);
        }
        else if (elemname.equals(JMSBridgeXMLConstant.Element.JMSBRIDGE)) {
            _logger.log(Level.FINE, "End of Document"); 
        }

        } catch (Exception e) {
            _logger.log(Level.SEVERE, e.getMessage(), e);
            throw new SAXException(e.getMessage(), e);
        }

    }

    public void characters(char buf [], int offset, int len) 
                                       throws SAXException {
        _logger.log(Level.FINE, "XML Parser characters: "+String.valueOf(buf, offset, len));
    }

    public void warning(SAXParseException e) throws SAXException {
        _logger.log(Level.WARNING, e.getSystemId()+", line "+
                          e.getLineNumber()+": "+e.getMessage());
    }
        
    public void error(SAXParseException e) throws SAXException {
        _logger.log(Level.SEVERE, e.getSystemId()+", line "+
                          e.getLineNumber()+": "+e.getMessage());
        throw e;
    }

    public void fatalError(SAXParseException e) throws SAXException {
        _logger.log(Level.SEVERE, e.getSystemId()+", line "+
                          e.getLineNumber()+": "+e.getMessage());
        super.fatalError(e);
        throw e;
    }

}
