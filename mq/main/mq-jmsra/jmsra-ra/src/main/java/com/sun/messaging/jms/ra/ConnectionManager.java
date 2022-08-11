/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to Eclipse Foundation. All rights reserved.
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

import java.util.logging.Logger;

/**
 * Implements the ConnectionManager interface for the S1 MQ RA. An instance of this ConnectionManager is used when the
 * RA is used in a Non-Managed environment/scenario.
 */

public class ConnectionManager implements java.io.Serializable, jakarta.resource.spi.ConnectionManager, jakarta.resource.spi.ConnectionEventListener {
    /* Simple partitioned pool implementation */
    // Each pool is keyed by the mcfId of the MCF
    // Each partition is keyed by the userName+clientID on the connection
    //
    // AS ConnectionManager has to store a pool for each MCF
    // which gets partitioned by Subject info

    /* Loggers */
    private static transient final String _className = "com.sun.messaging.jms.ra.ConnectionManager";
    protected static transient final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static transient final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_CM";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /** Public Constructor */
    public ConnectionManager() {
        _loggerOC.entering(_className, "constructor()");

        // PENDING: CM Pooling
        // connections = new Vector();
    }

    // ConnectionManager interface methods //
    //

    /**
     * Allocates a ManagedConnection.
     *
     * @param mcf The ManagedConnectionFactory to use.
     * @param cxRequestInfo The ConnectionRequestInfo to use.
     *
     * @return The ManagedConnection instance
     */
    @Override
    public Object allocateConnection(jakarta.resource.spi.ManagedConnectionFactory mcf, jakarta.resource.spi.ConnectionRequestInfo cxRequestInfo)
            throws jakarta.resource.ResourceException {
        Object params[] = new Object[2];
        params[0] = mcf;
        params[1] = cxRequestInfo;

        _loggerOC.entering(_className, "allocateConnection()", params);

        // _loggerOC.finer(_lgrMID_INF+
        jakarta.resource.spi.ManagedConnection mc = mcf.createManagedConnection(null, cxRequestInfo);
        mc.addConnectionEventListener(this);
        return mc.getConnection(null, cxRequestInfo);
    }

    // ConnectionEventListener interface methods
    //

    /**
     * connectionClosed
     *
     * Close the physical connection
     * 
     */
    @Override
    public void connectionClosed(jakarta.resource.spi.ConnectionEvent event) {
        _loggerOC.entering(_className, "connectionClosed()", event);
        if (event != null) {
            com.sun.messaging.jms.ra.ManagedConnection mc = (com.sun.messaging.jms.ra.ManagedConnection) event.getSource();
            // connections.add(mc);
            try {
                _loggerOC.fine(_lgrMID_INF + "connectionClosed:event=" + event + ":cleanup&destroy mc=" + mc.toString());
                mc.cleanup();
                mc.destroy();
            } catch (Exception re) {
                _loggerOC.warning(
                        _lgrMID_WRN + "connectionErrorOccurred:Exception on cleanup&destroy:" + re.getMessage() + ":event=" + event + ":mc=" + mc.toString());
                re.printStackTrace();
            }
        }
    }

    /**
     * connectionErrorOccurred
     *
     *
     */
    @Override
    public void connectionErrorOccurred(jakarta.resource.spi.ConnectionEvent event) {
        _loggerOC.entering(_className, "connectionErrorOccurred()", event);
        if (event != null) {
            com.sun.messaging.jms.ra.ManagedConnection mc = (com.sun.messaging.jms.ra.ManagedConnection) event.getSource();
            try {
                _loggerOC.warning(_lgrMID_WRN + "connectionErrorOccurred:event=" + event + ":Destroying mc=" + mc.toString());
                mc.destroy();
            } catch (Exception re) {
                _loggerOC.warning(
                        _lgrMID_WRN + "connectionErrorOccurred:Exception on destroy():" + re.getMessage() + ":event=" + event + ":mc=" + mc.toString());
                re.printStackTrace();
            }
        }
    }

    /**
     * localTransactionCommitted
     *
     *
     */
    @Override
    public void localTransactionCommitted(jakarta.resource.spi.ConnectionEvent event) {
        _loggerOC.entering(_className, "localTransactionCommitted()", event);
    }

    /**
     * localTransactionRolledback
     *
     *
     */
    @Override
    public void localTransactionRolledback(jakarta.resource.spi.ConnectionEvent event) {
        _loggerOC.entering(_className, "localTransactionRolledback()", event);
    }

    /**
     * localTransactionStarted
     *
     *
     */
    @Override
    public void localTransactionStarted(jakarta.resource.spi.ConnectionEvent event) {
        _loggerOC.entering(_className, "localTransactionStarted()", event);
    }
}
