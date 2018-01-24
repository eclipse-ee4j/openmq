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

package com.sun.messaging.jms.ra;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

/**
 *  Implements the ConnectionManager interface for the S1 MQ RA.
 *  An instance of this ConnectionManager is used when the RA is
 *  used in a Non-Managed environment/scenario.
 */

public class ConnectionManager
implements java.io.Serializable,
           javax.resource.spi.ConnectionManager,
           javax.resource.spi.ConnectionEventListener
{
    /* Simple partitioned pool implementation */
    // Each pool is keyed by the mcfId of the MCF
    // Each partition is keyed by the userName+clientID on the connection
    //
    // AS ConnectionManager has to store a pool for each MCF
    //    which gets partitioned by Subject info
    private transient Vector connections = null;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.ConnectionManager";
    protected static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_CM";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
 
    /** Public Constructor */
    public ConnectionManager()
    {
        _loggerOC.entering(_className, "constructor()");

        //PENDING: CM Pooling
        //connections = new Vector();
    }
    
    // ConnectionManager interface methods //
    // 

    /** Allocates a ManagedConnection.
     *
     *  @param mcf The ManagedConnectionFactory to use.
     *  @param cxRequestInfo The ConnectionRequestInfo to use.
     *
     *  @return The ManagedConnection instance
     */
    public Object
    allocateConnection(javax.resource.spi.ManagedConnectionFactory mcf,
            javax.resource.spi.ConnectionRequestInfo cxRequestInfo)
    throws javax.resource.ResourceException
    {
        Object params[] = new Object[2];
        params[0] = mcf;
        params[1] = cxRequestInfo;

        _loggerOC.entering(_className, "allocateConnection()", params);

        javax.resource.spi.ManagedConnection mc = null;
        if (false) {
            //PENDING: CM Pooling
            //_loggerOC.finer(_lgrMID_INF+
            //mc = match and return from connections if non-empty
            return mc; //null
        } else {
            //_loggerOC.finer(_lgrMID_INF+
            mc = mcf.createManagedConnection(null, cxRequestInfo);
            mc.addConnectionEventListener(this);
            return mc.getConnection(null, cxRequestInfo);
        }
    }


    // ConnectionEventListener interface methods
    //

    /** connectionClosed
    *
    *    Close the physical connection
    *   
    */
    public void connectionClosed(javax.resource.spi.ConnectionEvent event)
    {
        _loggerOC.entering(_className, "connectionClosed()", event);
        if (event != null) {
            com.sun.messaging.jms.ra.ManagedConnection mc = (com.sun.messaging.jms.ra.ManagedConnection)event.getSource();
            //connections.add(mc);
            try {
                _loggerOC.fine(_lgrMID_INF+"connectionClosed:event="+event+":cleanup&destroy mc="+mc.toString());
                mc.cleanup();
                mc.destroy();
            } catch (Exception re) {
                _loggerOC.warning(_lgrMID_WRN+"connectionErrorOccurred:Exception on cleanup&destroy:"+re.getMessage()+":event="+event+":mc="+mc.toString());
                re.printStackTrace();
            }
        }
    }

    /** connectionErrorOccurred
    *
    *
    */
    public void connectionErrorOccurred(javax.resource.spi.ConnectionEvent event)
    {
        _loggerOC.entering(_className, "connectionErrorOccurred()", event);
        if (event != null) {
            com.sun.messaging.jms.ra.ManagedConnection mc = (com.sun.messaging.jms.ra.ManagedConnection)event.getSource();
            try {
                _loggerOC.warning(_lgrMID_WRN+"connectionErrorOccurred:event="+event+":Destroying mc="+mc.toString());
                mc.destroy();
            } catch (Exception re) {
                _loggerOC.warning(_lgrMID_WRN+"connectionErrorOccurred:Exception on destroy():"+re.getMessage()+":event="+event+":mc="+mc.toString());
                re.printStackTrace();
            }
        }
    }

    /** localTransactionCommitted
    *
    *
    */
    public void localTransactionCommitted(javax.resource.spi.ConnectionEvent event)
    {
        _loggerOC.entering(_className, "localTransactionCommitted()", event);
    }

    /** localTransactionRolledback
    *
    *
    */
    public void localTransactionRolledback(javax.resource.spi.ConnectionEvent event)
    {
        _loggerOC.entering(_className, "localTransactionRolledback()", event);
    }

    /** localTransactionStarted
    *
    *
    */
    public void localTransactionStarted(javax.resource.spi.ConnectionEvent event)
    {
        _loggerOC.entering(_className, "localTransactionStarted()", event);
    }

    // Public methods
    //

    /** destroy connections
    *
    *
    *  PENDING: CM pooling 
    */
    public void
    destroyConnections()
    {
        if (false) {
        if (connections != null) {
            for (int i=0; i<connections.size(); i++) {
                //System.out.println("MQRA:CM:destroyConnections:destroy mc#:"+i);
                try {
                    ((com.sun.messaging.jms.ra.ManagedConnection)connections.elementAt(i)).destroy();
                } catch (Exception e) {
                    System.err.println("MQRA:CM:destroyConnections:Exception"+e.getMessage());
                    e.printStackTrace();
                }
            }
            connections.clear();
        }
        }
    }
}

