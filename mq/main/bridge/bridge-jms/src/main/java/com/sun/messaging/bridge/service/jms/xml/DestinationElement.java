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

import java.util.*;
import com.sun.messaging.bridge.service.jms.JMSBridge;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 *
 * @author amyk
 */

public class DestinationElement 
{
    private Properties _attrs = null;
    private Properties _props = null;
    private String _type = null;

    public DestinationElement() {}

    public void setAttributes(Properties a) throws IllegalArgumentException {
        if (a != null) {
            String refname = a.getProperty(JMSBridgeXMLConstant.Common.REFNAME);
            if (refname.equals(DMQElement.BUILTIN_DMQ_DESTNAME) ||
                refname.equals(DMQElement.BUILTIN_DMQ_NAME) ||
                refname.equals(JMSBridgeXMLConstant.Target.DESTINATIONREF_AS_SOURCE)) {
                throw new IllegalArgumentException(
                    JMSBridge.getJMSBridgeResources().getKString(
                    JMSBridgeResources.X_XML_IS_RESERVED, JMSBridgeXMLConstant.Destination.REFNAME+"="+refname));
            }
            if (a.getProperty(JMSBridgeXMLConstant.Destination.LOOKUPNAME) == null) {
                String name =  a.getProperty(JMSBridgeXMLConstant.Destination.NAME);
                if (name == null) {
                    String[] params = { JMSBridgeXMLConstant.Destination.LOOKUPNAME,
                                        JMSBridgeXMLConstant.Destination.NAME,
                                        JMSBridgeXMLConstant.Element.DESTINATION };
                    throw new IllegalArgumentException(JMSBridge.getJMSBridgeResources().getKString(
                                             JMSBridgeResources.X_XML_NO_LOOKUP_NO_NAME_ELEMENT, params));
                }
                if (name.equals(DMQElement.BUILTIN_DMQ_DESTNAME)) {
                    throw new IllegalArgumentException(
                        JMSBridge.getJMSBridgeResources().getKString(
                        JMSBridgeResources.X_XML_IS_RESERVED,JMSBridgeXMLConstant.Destination.NAME+"="+name));
                }
            }
        } 
        _attrs = a;
    }

    public void setProperties(Properties a) {
        _props = a;
    }

    public Properties getAttributes() {
        return _attrs;
    }

    public Properties getProperties() {
        return _props;
    }

    public String getName() throws Exception {
        String name =  _attrs.getProperty(JMSBridgeXMLConstant.Destination.NAME);
        String lookup = _attrs.getProperty(JMSBridgeXMLConstant.Destination.LOOKUPNAME);
        if (lookup != null) {
            throw new UnsupportedOperationException(
            "Called when "+JMSBridgeXMLConstant.Destination.LOOKUPNAME+ " is specified");
        }
        if (name == null) {
            throw new IllegalArgumentException(
            JMSBridge.getJMSBridgeResources().getKString(
                JMSBridgeResources.X_XML_ATTR_NOT_SPECIFIED,
                JMSBridgeXMLConstant.Destination.NAME,
                JMSBridgeXMLConstant.Element.DESTINATION));
        }
        return name;
    }

    public String getLookupName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.Destination.LOOKUPNAME);
    }

    public String getRefName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.Destination.REFNAME);
    }

    public boolean isQueue() throws Exception {
        if (_type == null) {
            _type = _attrs.getProperty(JMSBridgeXMLConstant.Destination.TYPE);
        }
        if (getLookupName() != null) {
            throw new UnsupportedOperationException(
            "Called when "+JMSBridgeXMLConstant.Destination.LOOKUPNAME+ " is specified");
        }
        if (_type == null) {
            throw new IllegalArgumentException(
            JMSBridge.getJMSBridgeResources().getKString(
                JMSBridgeResources.X_XML_ATTR_NOT_SPECIFIED,
                JMSBridgeXMLConstant.Destination.TYPE,
                JMSBridgeXMLConstant.Element.DESTINATION));
        }
        _type = _type.trim().toLowerCase();
        return !_type.equals(JMSBridgeXMLConstant.Destination.TOPIC);
    }

    public String toString() {
        return JMSBridgeXMLConstant.Element.DESTINATION+"["+getRefName()+"]";
    }
}
