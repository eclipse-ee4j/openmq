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

public class TargetElement 
{
    private Properties _props = null;
    private Properties _attrs = null;

    public TargetElement() {}

    public void setAttributes(Properties a) {
        _attrs = a;
    }

    public void setProperties(Properties p) throws Exception {
        if (p != null && p.get(JMSBridge.BRIDGE_NAME_PROPERTY) != null) {
            throw new IllegalArgumentException(
		        JMSBridge.getJMSBridgeResources().getKString(
                JMSBridgeResources.X_XML_IS_RESERVED, 
                JMSBridgeXMLConstant.Element.PROPERTY+"="+JMSBridge.BRIDGE_NAME_PROPERTY));
        }
        _props = p;

    }

    public Properties getAttributes() {
        return _attrs;
    }

    public Properties getProperties() {
        return _props;
    }
}
