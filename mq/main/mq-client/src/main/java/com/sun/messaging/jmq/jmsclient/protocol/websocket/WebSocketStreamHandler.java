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

/*
 */ 
package com.sun.messaging.jmq.jmsclient.protocol.websocket;

import java.io.*;
import javax.jms.JMSException;

import com.sun.messaging.PropertyOwner;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;

/**
 */
public class WebSocketStreamHandler implements StreamHandler, PropertyOwner {

    /**
     * Null constructor for use by AdministeredObject when used as a PropertyOwner
     */  
    public WebSocketStreamHandler() {}

    @Override
    public String[] getPropertyNames() {
        return (new String[0]);
    }

    @Override
    public String getPropertyType(String propname) {
        return null;
    }

    @Override
    public String getPropertyLabel(String propname) {
        return null;
    }
 
    @Override
    public String getPropertyDefault(String propname) {
        return null;
    }
 
    /**
     * @param connection is the ConnectionImpl object.
     */
    public ConnectionHandler
    openConnection(Object connection) throws JMSException {
        throw new JMSException(
            getClass().getSimpleName()+"(Object): unnexpected call");
    }

    public ConnectionHandler openConnection(
        MQAddress addr, ConnectionImpl connection) throws JMSException {
        return new WebSocketConnectionHandler(addr, connection);
    }

}
