/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.ra;

import jakarta.jms.JMSException;

import jakarta.resource.spi.*;

import java.util.Vector;
import java.util.logging.Logger;

import com.sun.messaging.jmq.jmsclient.notification.BrokerAddressListChangedEvent;

/**
 * Implements the JMS ExceptionListener interface and is the generator of events to the ConnectionEventListener for the
 * SJS MQ RA.
 */

@SuppressWarnings("JdkObsolete")
public class ConnectionEventListener implements jakarta.jms.ExceptionListener, com.sun.messaging.jms.notification.EventListener {
    /** The connection event listener list */
    private Vector<jakarta.resource.spi.ConnectionEventListener> listeners = null;

    /** The ManagedConnection associated with this ConnectionEventListener */
    private com.sun.messaging.jms.ra.ManagedConnection mc = null;

    /* Loggers */
    private static final String _className = "com.sun.messaging.jms.ra.ConnectionEventListener";
    protected static final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    protected static final String _lgrMIDPrefix = "MQJMSRA_CL";
    protected static final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Constructor */
    public ConnectionEventListener(com.sun.messaging.jms.ra.ManagedConnection mc) {
        _loggerOC.entering(_className, "constructor()", mc);
        listeners = new Vector<>();
        this.mc = mc;
    }

    /** Adds a ConnectionEventListener to the list of listeners */
    public void addConnectionEventListener(jakarta.resource.spi.ConnectionEventListener listener) {
        _loggerOC.entering(_className, "addConnectionEventListener()", listener);
        listeners.addElement(listener);
    }

    /** Removes a ConnectionEventListener from the list of listeners */
    public void removeConnectionEventListener(jakarta.resource.spi.ConnectionEventListener listener) {
        _loggerOC.entering(_className, "removeConnectionEventListener()", listener);
        listeners.removeElement(listener);
    }

    /**
     * Sends a ConnectionEvent to the list of registered listeners
     * 
     * @param type The type of event
     *
     * @param ex The Exception (if an exception will be thrown)
     *
     * @param handle The connection handle to set into the ConnectionEvent
     */
    public void sendEvent(int type, Exception ex, Object handle) {
        Object params[] = new Object[3];
        params[0] = Integer.valueOf(type);
        params[1] = ex;
        params[2] = handle;

        _loggerOC.entering(_className, "sendEvent()", params);

        Vector list = (Vector) listeners.clone();
        ConnectionEvent cevent = null;
        if (ex != null) {
            cevent = new ConnectionEvent(mc, type, ex);
        } else {
            cevent = new ConnectionEvent(mc, type);
        }
        if (handle != null) {
            cevent.setConnectionHandle(handle);
        }
        for (int i = 0; i < list.size(); i++) {
            jakarta.resource.spi.ConnectionEventListener listener = (jakarta.resource.spi.ConnectionEventListener) list.elementAt(i);
            switch (type) {
            case ConnectionEvent.CONNECTION_ERROR_OCCURRED:
                listener.connectionErrorOccurred(cevent);
                break;
            case ConnectionEvent.CONNECTION_CLOSED:
                listener.connectionClosed(cevent);
                break;
            case ConnectionEvent.LOCAL_TRANSACTION_STARTED:
                listener.localTransactionStarted(cevent);
                break;
            case ConnectionEvent.LOCAL_TRANSACTION_COMMITTED:
                listener.localTransactionCommitted(cevent);
                break;
            case ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK:
                listener.localTransactionRolledback(cevent);
                break;
            default:
                IllegalArgumentException iae = new IllegalArgumentException(_lgrMID_WRN + "sendEvent:Unknown Event=" + type);
                _loggerOC.warning(iae.getMessage());
                _loggerOC.throwing(_className, "sendEvent()", iae);
                throw iae;

            }
        }
    }

    // jakarta.jms.Exceptionlistener interface method
    //

    /**
     * Upon receipt of a JMS Connection Exception 'onException' method call, this method sends a CONNECTION_ERROR_OCCURRED
     * ConnectionEvent to the registered listeners.
     */
    @Override
    public void onException(JMSException jmse) {
        // System.err.println("MQRA:CEL:onException():for mc="+mc.getMCId()+"
        // :xacId="+mc.getConnectionAdapter().xac._getConnectionID());
        _loggerOC.warning(_lgrMID_WRN + "onException:for mc=" + mc.getMCId() + " :xacId="/* +mc.getConnectionAdapter().xac._getConnectionID() */);
        sendEvent(ConnectionEvent.CONNECTION_ERROR_OCCURRED, jmse, null);
    }

    // com.sun.messaging.jms.notification.EventListener interface method
    @Override
    public void onEvent(com.sun.messaging.jms.notification.Event evnt) {
        _loggerOC.entering(_className, "onEvent()", evnt);
        _loggerOC.info(_lgrMID_INF + "onEvent:Connection Event for mc=" + mc.getMCId() + " :xacId="
                + /* mc.getConnectionAdapter().xac._getConnectionID()+ */":event:" + evnt.toString());

        if (evnt instanceof BrokerAddressListChangedEvent) {
            BrokerAddressListChangedEvent bAddressListChangedEvt = (BrokerAddressListChangedEvent) evnt;
            String addressList = bAddressListChangedEvt.getAddressList();
            if (addressList != null) {
                _loggerOC.info(_lgrMID_INF + "onEvent:Notification Event for mc=" + mc.getMCId() + " :xacId=" +
                /* mc.getConnectionAdapter().xac._getConnectionID()+ */
                        "New AddressList=" + addressList + ":event:" + evnt.toString());
                mc.getManagedConnectionFactory()._setMessageServiceAddressList(addressList);
            }
        }
    }
}
