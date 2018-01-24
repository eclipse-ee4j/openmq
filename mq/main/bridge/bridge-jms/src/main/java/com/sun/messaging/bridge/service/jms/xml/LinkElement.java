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

public class LinkElement 
{
    private Properties _attrs = null;
    private Properties _source = null;
    private TargetElement _target = null;

    public LinkElement() {}

    public void setAttributes(Properties a) {
        _attrs = a;
    }

    public Properties getAttributes() {
        return _attrs;
    }

    public boolean isTransacted() {
        return Boolean.valueOf(_attrs.getProperty(JMSBridgeXMLConstant.Link.TRANSACTED,
                            JMSBridgeXMLConstant.Link.TRANSACTED_DEFAULT)).booleanValue();
    }

    public String getName() {
        return _attrs.getProperty(JMSBridgeXMLConstant.Common.NAME);
    }

    public boolean isEnabled() {
        return Boolean.valueOf(_attrs.getProperty(JMSBridgeXMLConstant.Link.ENABLED, 
                           JMSBridgeXMLConstant.Link.ENABLED_DEFAULT)).booleanValue();
    }
    
    public Properties getSource() {
        return _source;
    }

    public TargetElement getTarget() {
        return _target;
    }

    public void setSource(Properties s) {
        _source = s;
    }

    public void setTarget(TargetElement t) {
        _target = t;
    }

    public String toString() { 
        return JMSBridgeXMLConstant.Element.LINK+"["+getName()+"]";
    }
}
