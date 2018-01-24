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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.BigPacketException;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQIPConnection;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.service.imq.OperationRunnable;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;


/**
 * @author amyk
 */
public final class WebSocketMQIPConnection extends IMQIPConnection implements Runnable
{

    private static boolean DEBUG = (false || Globals.getLogger().getLevel() <= Logger.DEBUG);

    private MQWebSocket websocket = null;
    private Thread writerThread = null;
    private Object assignWriteLock = new Object();
    private boolean writeThreadAssigned = false;

    public WebSocketMQIPConnection(WebSocketIPService svc, PacketRouter router, MQWebSocket ws)
    throws IOException, BrokerException {

        super(svc, null, router);
        this.websocket = ws;
        setRemoteIP(getRemoteAddress().getAddress());
        if (svc.useDedicatedWriter()) {
            writerThread = new MQThread(this, "GrizzlyMQIPConnection");
            writerThread.start(); 
        }
    }
    
    @Override
    protected InetAddress getRemoteAddress() { 
        if (websocket == null) {
            return null;
        }
        return websocket.getRemoteAddress();
    }

    @Override
    protected int getRemotePort() { 
        return websocket.getRemotePort();
    }

    @Override
    public int getLocalPort() {
        return ((MQWebSocket)websocket).getLocalPort();
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public synchronized AbstractSelectableChannel getChannel() {
    throw new RuntimeException("Unexpected call: "+getClass().getName()+".getChannel()");
    }

    @Override
    protected void closeProtocolStream() throws IOException {
        websocket.close();
    }

    @Override
    public void sendControlMessage(Packet msg) {
        if (DEBUG) {
            logger.log(Logger.INFO, 
            "GrizzlyMQIPConnection:sendControlMessage: "+msg+", "+isValid());
        }
        if (!isValid() && msg.getPacketType() != PacketType.GOODBYE ) {
            logger.log(Logger.INFO,"Internal Warning: message " + msg
                  + "queued on destroyed connection " + this);
        }
        if (!websocket.isConnected() && msg.getPacketType() == PacketType.GOODBYE) {
            return;
        }
        try {
            if (getDEBUG() || getDumpPacket() || getDumpOutPacket()) {
                dumpControlPacket(msg);
            }
            websocket.writePacket(msg);
        } catch (Exception e) {
            logger.logStack(logger.WARNING, 
            "Failed to send control packet "+msg+" to "+websocket, e);
        }
    }

    public void receivedPacket(Packet pkt) {
        readpkt = pkt;
    }

    @Override
    protected boolean readInPacket(Packet p)
    throws IllegalArgumentException, StreamCorruptedException,
           BigPacketException, IOException {

        if (DEBUG) {
            logger.log(Logger.INFO, "GrizzlyMQIPConnection:readInPacket: "+readpkt);
        }

        if (readpkt == null) {
            throw new IOException("No packet to read");
        }
        return true;
    }

    @Override
    protected boolean writeOutPacket(Packet p) throws IOException {
        if (DEBUG) {
            logger.log(Logger.INFO, "GrizzlyMQIPConnection:writeOutPacket("+p+") to "+websocket);
        }
        websocket.writePacket(p);
        return true; //XXX
    }

  
    @Override
    protected void handleWriteException(Throwable e)
    throws IOException, OutOfMemoryError {
       super.handleWriteException(e);
    }

    @Override
    protected void handleBigPacketException(Packet pkt, BigPacketException e) {
        super.handleBigPacketException(pkt, e);
    }

    @Override
    protected void handleIllegalArgumentExceptionPacket(
        Packet pkt, IllegalArgumentException e) {
        super.handleIllegalArgumentExceptionPacket(pkt, e);
    }

    @Override
    public synchronized void threadAssigned(
        OperationRunnable runner, int events)
        throws IllegalAccessException {
        throw new UnsupportedOperationException(
        "Unexpected call: GrizzlyMQIPConnection.threadAssigned()");
    }

    @Override
    protected void localFlushCtrl() {
        throw new UnsupportedOperationException(
        "Unexpected call: GrizzlyMQIPConnection.localFlushCtrl()");
    }

    @Override
    protected void localFlush() {
        throw new UnsupportedOperationException(
        "Unexpected call: GrizzlyMQIPConnection.localFlush()");
    }

    protected boolean assignWriteThread(boolean b) {
        synchronized(assignWriteLock) {
            if (b && writeThreadAssigned) {
                return false;
            }
            writeThreadAssigned = b;
            return true;
        }
    }

    public void run() {
        while (isValid()) {
            try {
                writeData(true);
            } catch (IOException e) {
                if (isValid()) {
                    logger.logStack(logger.ERROR,
                    "Exception in writing data on conection "+this, e);
                }
            }
        }
    }
}



