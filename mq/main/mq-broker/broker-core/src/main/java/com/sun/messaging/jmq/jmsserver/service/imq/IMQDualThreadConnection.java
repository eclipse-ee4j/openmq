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

import java.io.IOException;
import java.security.Principal;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import javax.jms.JMSException;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.ReadWritePacket;
import com.sun.messaging.jmq.io.PacketDispatcher;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.IMQBlockingQueue;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;
import com.sun.messaging.jmq.jmsservice.HandOffQueue;
import com.sun.messaging.jmq.util.lists.EventType;
import com.sun.messaging.jmq.util.lists.Reason;
import com.sun.messaging.jmq.util.log.Logger;

public class IMQDualThreadConnection extends IMQBasicConnection implements DirectBrokerConnection
{
    class DummyQueue<Packet> implements HandOffQueue<Packet>
    {
        public Packet take() throws InterruptedException {
            //does nothing
            return null;
        }
        public void put(Packet p) throws InterruptedException{
            processReadPacket((com.sun.messaging.jmq.io.Packet)p);
        }
        public void close() {
        }
    }

    DummyQueue inputQueue;
    
    // in this mode, packets from the client are processed by this class in the same thread,
    // so we send the reply back using a ThreadLocal for maximum performance
    ThreadLocal<Queue<Packet>> replies = new ThreadLocal<Queue<Packet>>();
 	// packets which originate in the broker (e.g. a message being sent to a consumer)
	// are written to the output queue for the client to pick up in another thread
    IMQBlockingQueue outputQueue;
    
    PacketDispatcher replyDispatcher;

    /**
     * constructor
     */

    public IMQDualThreadConnection(Service svc, 
             PacketRouter router) 
        throws IOException, BrokerException
    {
        super(svc, router);

        inputQueue = new DummyQueue();
        outputQueue = new IMQBlockingQueue();
        setConnectionState(Connection.STATE_CONNECTED);
    }

    /**
     * sets the connection state
     * @return false if connection being destroyed
     */
    public boolean setConnectionState(int state) {
        this.state = state;
        if (this.state >= Connection.STATE_CLOSED) {
            return false;
        }
        if (state == Connection.STATE_AUTHENTICATED) {
            logConnectionInfo(false);
        }
        return true;
    }


    public HandOffQueue getClientToBrokerQueue() {
        return inputQueue;
    }

    public HandOffQueue getBrokerToClientQueue() {
        return outputQueue;
    }

    /**
     * This method is used only if "sync replies" have been enabled
     * 
     * Puts the specified reply packet onto the Queue for the current thread, which will be saved in a ThreadLocal
     * so that is can be obtained by the requester, which will be the same thread
     * 
     * This method then calls into the client to process (dispatch) the reply packet.
     * We need to do this now rather than after this method returns to avoid packets being processed by the client out of order
     * 
     * @param packet the reply packet for the current thread
     */
    public void putReply(Packet packet) {
        Queue<Packet> packets = replies.get();
    	
        if (packets==null){
            packets = new LinkedList<Packet>();
            replies.set(packets);
        }
    	
    	packets.add(packet);
    	
    	// process the packet on the client
        try {
            replyDispatcher.dispatch((ReadWritePacket)packet);
        } catch (JMSException e) {
            logger.logStack(Logger.ERROR, "Error dispatching reply packet "+packet, e);
        }
    }

    /**
     * Fetches the reply packet from the reply queue for the current thread, which is saved in a ThreadLocal
     * 
     * @return the reply packet for the current thread
     */
    public Packet fetchReply(){
    	Queue<Packet> packets = replies.get();
    	return packets.poll();
    }
        
    public void checkState() {
    	// do nothing    	
    }
    
    /**
     * start sending JMS messages to the connection
     */
    public void startConnection() {
    	
    	super.startConnection();

		// added to fix CR 6879664
		synchronized (sessions) {
			Iterator itr = sessions.values().iterator();
			while (itr.hasNext()) {
				Session session = ((Session) itr.next());

				// Pull messages until not busy
				while (session.isBusy()) {
					// NOTE: this should work for queues because they require a
					// resume flow from the client
					Packet emptyPacket = new Packet();
					session.fillNextPacket(emptyPacket);
					// write packet
					writePacket(emptyPacket, false);
				}

			}
		}
		
    }

    public boolean isBlocking() {
        return true;
    }
    //turn off flow
    public void setFlowCount() {
        //do nothing
    }
    public void haltFlow() {
        //do nothing
    }
    public void resumeFlow(int cnt)
    {
        //do nothing
    }

    public void cleanupControlPackets(boolean shutdown) {
        //LKS-XXX
        // not sure, for now do nothing
    }

    public boolean useDirectBuffers() {
        return true;
    }

    protected void checkConnection() {
       //do nothing
    }
    protected void flushConnection(long timeout) {
        //do nothing
    }

    public void logConnectionInfo(boolean closing) {
        this.logConnectionInfo(closing,"Unknown");
    }

    public void logConnectionInfo(boolean closing, String reason) {

        String[] args = {
            getRemoteConnectionString(),
            localServiceString(),
            Integer.toString(Globals.getConnectionManager().size()),
            reason,
            "0", /* LKS-XXX what are the right values */
            Integer.toString(service.size()) 
        };

        if (!closing) {
            logger.log(Logger.INFO, BrokerResources.I_ACCEPT_CONNECTION, args);
} else {
            logger.log(Logger.INFO, BrokerResources.I_DROP_CONNECTION, args);
        }
    }



    public void processReadPacket(Packet p) {
       msgsIn ++;
       if (p.getPacketType() < PacketType.LAST)
           pktsIn[p.getPacketType()] ++;
       if (DEBUG || DUMP_PACKET || IN_DUMP_PACKET) {
            int flag = (DUMP_PACKET || IN_DUMP_PACKET) ? Logger.INFO
                    : Logger.DEBUGHIGH;

                logger.log(flag, "\n------------------------------"
                    + "\nReceived incoming Packet - Dumping"
                    + "\nConnection: " + this 
                    + "\n------------------------------"
                    + "\n" + p.dumpPacketString(">>>>****") 
                    + "\n------------------------------");
       }
   	   router.handleMessage(this, p);
    }

    public boolean isDirectBuffers() {
        // does not apply
        return false;
    }

       
    public void sendControlMessage(Packet p)
    {
        if (p.getPacketType() > PacketType.MESSAGE) {
           p.setIP(ipAddress);
           p.setPort(0);
        }

      	writePacket(p, true);
    }
    public void writePacket(Packet p, boolean control) {
        if (control) {
                if (DEBUG || DUMP_PACKET || OUT_DUMP_PACKET) {
                    int flag = (DUMP_PACKET || OUT_DUMP_PACKET) 
                            ? Logger.INFO : Logger.DEBUGHIGH;
                    logger.log(flag, "\n------------------------------"
                            +"\nSending Control Packet - Dumping"
                            + "\n------------------------------"
                            + "\n" + p.dumpPacketString("<<<<****")
                            + "\n------------------------------");
                }
            
        } else {
                if (DEBUG || DUMP_PACKET || OUT_DUMP_PACKET) {
                    int flag = (DUMP_PACKET || OUT_DUMP_PACKET) 
                            ? Logger.INFO : Logger.DEBUGHIGH;
                    logger.log(flag, "\n------------------------------"
                            +"\nSending JMS Message -"
                            + " Dumping"
                            + "\n------------------------------"
                            + "\n" + p.dumpPacketString("<<<<****")
                            + "\n------------------------------");
                }
        }
        pktsOut[p.getPacketType()] ++;
        //LKS- deal with metrics
        try {
            ReadWritePacket rp = new ReadWritePacket();
            rp.fill(p, !control);
            
            // CR 6897721 always sent STOP_REPLY via the output queue
            if (Globals.getAPIDirectTwoThreadSyncReplies() && 
                rp.isReply() && rp.getPacketType()!=PacketType.STOP_REPLY) {
            	// this is a reply packet 
            	// this is the same thread that sent the request, so we
                // save the reply in a ThreadLocal to pass it directly to the requester
                putReply(rp);
            } else {
             	// this packet is not a reply to the client but originates in the broker 
            	// (e.g. a message being sent to a consumer)
            	// write it to the output queue for the client to pick up in due course
            	outputQueue.add(rp);
            }
        } catch (IOException ex) {
            logger.log(Logger.DEBUG,"Unable to duplicate packet ", ex);
        }
    }
    public void eventOccured(EventType type, Reason r, Object target, Object oldval, Object newval,
        Object userdata)
    { 
        //a session has something to do
        Session s = (Session)target;
        
        if (!runningMsgs){
        	// Connection is stopped so not sending messages to consumers
        	return;
        }
        // Pull messages until not busy
        while (s.isBusy()) {
//NOTE: this should work for queues because they require a resume flow from the client
            Packet emptyPacket = new Packet();
            s.fillNextPacket(emptyPacket);
            //write packet
            writePacket(emptyPacket, false);
        }
    }

    /** 
     * The debug state of this object
     */
    public synchronized Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        // LKS - XXX
        ht.put("transport","Embedded2");
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
                if (DEBUG)
                    logger.log(Logger.DEBUG,"Exception getting authentication name "
                        + conId, e );
                        
            }
        }


        String retstr = userString + "@" +
            "Direct2" + ":" +
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
         //LKS-XXX 
        notifyConnectionClosed();
    }

    
	/**
	 * This method is used only if "sync replies" have been enabled
	 * 
	 * Specify that the supplied ReplyDispatcher should be used to process reply packets
	 * 
	 * @param rd The ReplyDispatcher to be used
	 */
    public void setReplyDispatcher(PacketDispatcher rd){
    	replyDispatcher = rd;
    }

}



