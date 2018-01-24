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
 * @(#)IMQEmbeddedConnection.java  10/28/08
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.ReadOnlyPacket;
import com.sun.messaging.jmq.io.ReadWritePacket;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;
import com.sun.messaging.jmq.jmsservice.HandOffQueue;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.IMQBlockingQueue;
import java.util.*;
import java.io.*;
import java.security.Principal;




public class IMQEmbeddedConnection extends IMQIPConnection implements DirectBrokerConnection
{

    IMQBlockingQueue inputQueue;
    IMQBlockingQueue outputQueue;

    static class EOF { // note we could also do something like queue the exception
        String reason = null;
        public EOF(String reason) {
            this.reason = reason;
        }
        public String getReason() {
            return reason;
        }
     };


    /**
     * constructor
     */


    public IMQEmbeddedConnection(Service svc, 
             PacketRouter router) 
        throws IOException, BrokerException
    {
        super(svc, null, router);

        inputQueue = new IMQBlockingQueue();
        outputQueue = new IMQBlockingQueue();
    }

    public HandOffQueue getClientToBrokerQueue() {
        return inputQueue;
    }

    public HandOffQueue getBrokerToClientQueue() {
        return outputQueue;
    }

    public boolean isBlocking() {
        return true;
    }

    /** 
     * The debug state of this object
     */
    public synchronized Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        // LKS - XXX
        ht.put("transport","Embedded");
        ht.put("inputQueue",inputQueue.toString());
        ht.put("outputQueue",outputQueue.toString());
        return ht;
    }


    public String getRemoteConnectionString() {
        if (remoteConString != null)
            return remoteConString;

        boolean userset = false;

        String userString = "???";

        if (state >= Connection.STATE_AUTHENTICATED) {
            try {
                Principal principal = getAuthenticatedName();
                if (principal != null) {
                    userString = principal.getName();
                    userset = true;
                }
            } catch (BrokerException e) { 
                if (IMQBasicConnection.DEBUG)
                    logger.log(Logger.DEBUG,"Exception getting authentication name "
                        + conId, e );
                        
            }
        }


        String retstr = userString + "@" +
            "Direct" + ":" +
            getConnectionUID();
        if (userset) remoteConString = retstr;
        return retstr;
    }

    String localsvcstring = null;
    protected String localServiceString() {
        if (localsvcstring != null)
            return localsvcstring;
        localsvcstring = service.getName();
        return localsvcstring;
    }

// -------------------------------------------------------------------------
//   Basic Connection Management
// -------------------------------------------------------------------------

    public synchronized void closeConnection(
            boolean force, int reason, String reasonStr) 
    { 
        super.closeConnection(force, reason, reasonStr);

        //Stick an EOF packet on the readChannel to wake it up
        EOF eof = new EOF(reasonStr);
        try {
            inputQueue.put(eof);
        } catch (InterruptedException ex) {
            Globals.getLogger().logStack(Logger.DEBUG,"nothing we can do",ex);
        }

    }


// -------------------------------------------------------------------------
//   Sending/Receiving Messages
// -------------------------------------------------------------------------


    protected boolean readInPacket(Packet p)
        throws IOException
    {
        // get and fill packet
        try {
        Object o= inputQueue.take();
        if (o instanceof EOF) {
            EOF eof = (EOF)o;
            throw new IOException("Connection has been closed:"+eof.getReason());
        }
        Packet newp = (Packet)o; // note of type ReadWritePacket
        
        // Make a copy
        //
        // IF CLIENT IS MAKING A COPY, this can be a shallow copy
        // Otherwise, this needs to be a deep copy
        //
        p.fill(newp, false); //LKS-XXX: revisit and make sure it should be shallow
        } catch (IOException ex) {
            // rethrow
            throw ex;
        } catch (Exception ex) {
             //LKS-XXX handle better
             Globals.getLogger().logStack(Logger.DEBUG,"Error retrieving message",ex);
             throw new IOException("Issue processing :"+ex);
        }

        return true;
    }

    protected Packet clearReadPacket(Packet p) {
        // XXX - we don't need a new packet if its not message data
        // Revisit
        return null;
    }

    protected boolean writeOutPacket(Packet p) 
        throws IOException
    {
        // write packet
        // it needs to be of type ReadOnlyPacket
        ReadWritePacket rp = new ReadWritePacket();
        // this should be deep
        rp.fill(p, true);

        // stick on the queue
        outputQueue.add(rp); 

        return true;
    }

    protected Packet clearWritePacket(Packet p)
    {
        // not sure if we need to clear this or not
        // XXX- Revisit

        return null;
    }


}



