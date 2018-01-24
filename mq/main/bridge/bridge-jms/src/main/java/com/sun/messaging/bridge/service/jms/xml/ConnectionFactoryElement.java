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

public class ConnectionFactoryElement 
{
    private Properties _attrs = null;
    private Properties _props = null;

    public ConnectionFactoryElement() {}

    public void setAttributes(Properties a) {
        String refname = null;
        if (a != null) {
            refname = a.getProperty(JMSBridgeXMLConstant.Common.REFNAME);
            if (refname.equals(DMQElement.BUILTIN_DMQ_NAME) ||
                refname.equals(DMQElement.BUILTIN_DMQ_DESTNAME)) {
                throw new IllegalArgumentException(JMSBridge.getJMSBridgeResources().getKString(
                    JMSBridgeResources.X_XML_IS_RESERVED, JMSBridgeXMLConstant.Common.REFNAME+"="+refname));
            }
            String username = a.getProperty(JMSBridgeXMLConstant.CF.USERNAME);
            if (username != null) {
                if (username.trim().equals("")) {
                    String[] eparam = { JMSBridgeXMLConstant.CF.USERNAME+"="+username, 
                                        JMSBridgeXMLConstant.Element.CF+"="+refname };
                    throw new IllegalArgumentException(JMSBridge.getJMSBridgeResources().getKString(
                                     JMSBridgeResources.X_XML_INVALID_USERNAME_FOR_CF, eparam));
                }
                a.setProperty(JMSBridgeXMLConstant.CF.USERNAME, username.trim());
                String password = a.getProperty(JMSBridgeXMLConstant.CF.PASSWORD);
                if (password == null) {
                    String[] eparam = { JMSBridgeXMLConstant.CF.PASSWORD, 
                                        JMSBridgeXMLConstant.Element.CF+" "+refname };
                    throw new IllegalArgumentException(JMSBridge.getJMSBridgeResources().getKString(
                                             JMSBridgeResources.X_XML_NAME_NOT_SPECIFIED_FOR, eparam));
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

    public String getLookupName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.CF.LOOKUPNAME);
    }

    public String getRefName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.CF.REFNAME);
    }

    public String getUsername() {
        String u =  _attrs.getProperty(JMSBridgeXMLConstant.CF.USERNAME);
        if (u == null) return null;
        return u.trim();
    }

    public String getPassword() {
        return _attrs.getProperty(JMSBridgeXMLConstant.CF.PASSWORD);
          
    }

    public boolean isMultiRM() {
        return Boolean.valueOf(_attrs.getProperty(JMSBridgeXMLConstant.CF.MULTIRM,
                                        JMSBridgeXMLConstant.CF.MULTIRM_DEFAULT));
    }

    public String toString() {
        return JMSBridgeXMLConstant.Element.CF+"["+getRefName()+"]";
    }
}
