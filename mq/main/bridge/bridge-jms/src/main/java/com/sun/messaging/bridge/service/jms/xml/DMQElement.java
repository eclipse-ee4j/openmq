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

/**
 *
 * @author amyk
 */

public class DMQElement 
{
    public static final String BUILTIN_DMQ_NAME = "built-in-dmq";
    public static final String BUILTIN_DMQ_DESTNAME = "imq.bridge.jms.dmq";

    private Properties _props = null;
    private Properties _attrs = null;

    public DMQElement() {}

    public void setAttributes(Properties a) {
        _attrs = a;
    }

    public void setProperties(Properties p) {
        _props = p;
    }

    public Properties getAttributes() {
        return _attrs;
    }

    public Properties getProperties() {
        return _props;
    }

    public String getName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.Common.NAME);
    }

    public String getCFRef() {
        return _attrs.getProperty(JMSBridgeXMLConstant.DMQ.CFREF);
    }

    public String getDestinationRef() {
        return _attrs.getProperty(JMSBridgeXMLConstant.DMQ.DESTINATIONREF);
    }
}
