/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketException;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.memory.MemoryManager;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.ByteBufferOutput;
import com.sun.messaging.jmq.io.BigPacketException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.grizzly.GrizzlyMQPacketList;


/**
 * @author amyk
 */
public class JMSWebSocket extends MQWebSocket {

    private Object packetLock = new Object();
    private Packet packetPending = null;

    public JMSWebSocket(MQWebSocketServiceApp app, 
                       ProtocolHandler protocolHandler,
                       HttpRequestPacket request,
                       WebSocketListener... listeners) {
        super(app, protocolHandler, request, listeners);
    }

    @Override
    protected void writePacket(Packet pkt) throws IOException {
        if (!isConnected()) {
            throw new IOException("JMSWebSocket@"+hashCode()+" is not connected"); 
        }
        if (DEBUG) {
            logger.log(logger.INFO, Thread.currentThread()+"JMSWebSocket@"+hashCode()+": WRITE PACKET="+pkt);
        }

        pkt.writePacket(new ByteBufferOutput() {
            public void writeByteBuffer(ByteBuffer data) throws IOException {
                throw new IOException("Unexpected call", 
                    new UnsupportedOperationException("writeByteBuffer(ByteBuffer)"));
            }
            public void writeBytes(byte[] data) throws IOException {
                if (DEBUG) {
                    logger.log(logger.INFO, Thread.currentThread()+"JMSWebSocket@"+hashCode()+
                    ": writeBytes(data.len="+data.length+")");
                }
                send(data);
            }
            }, false);
        if (DEBUG) {
            logger.log(logger.INFO, Thread.currentThread()+"JMSWebSocket@"+hashCode()+": SENT PACKET="+pkt);
        }
    }

    @Override
    protected void processData(String text) throws Exception {
        throw new IOException("JMSWebSocket.processData(String): unexpected call");
    }

    @Override
    protected void processData(byte[] data) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(data);
        processData(buf);
    }

    private void processData(ByteBuffer buf) throws IOException {

        if (DEBUG) {
            logger.log(Logger.INFO, 
                Thread.currentThread()+" processData:buf.remaining="+buf.remaining());
        }

        List<Packet> packetList = null;

	while (buf.hasRemaining()) {
            synchronized(packetLock) {
                if (packetPending != null) {
                    try {
                        if (packetPending.readPacket(buf)) {
                            if (!packetPending.hasBigPacketException()) {
                                if (packetList == null) {
                                    packetList = new ArrayList<Packet>();
                                }
                                packetList.add(packetPending); 
                                if (DEBUG) {
                                logger.log(logger.INFO, 
                                    Thread.currentThread()+"JMSWebSocket@"+hashCode()+" processData(): READ pending PACKET="+
                                     packetPending+", buf.remaining="+buf.remaining());
                                }
                            }
                            packetPending = null;
                        }  
                    } catch (BigPacketException e) {
                        logger.log(logger.ERROR, Thread.currentThread()+"readPacket: "+e.getMessage(), e);
                        WebSocketMQIPConnection conn = websocketApp.getMQIPConnection(this);
                        conn.handleBigPacketException(packetPending, e); //XXopt close conn if too big

                    } catch (IllegalArgumentException e) {
                        WebSocketMQIPConnection conn = websocketApp.getMQIPConnection(this);
                        conn.handleIllegalArgumentExceptionPacket(packetPending, e);

                    } catch (OutOfMemoryError err) { //XXopt close conn
		        Globals.handleGlobalError(err,
                            Globals.getBrokerResources().getKString(
                            BrokerResources.M_LOW_MEMORY_READALLOC) + ": "
                            +packetPending.headerToString());
                    }
                    continue;
                }
            }

            if (packetList == null) {
                packetList = new ArrayList<Packet>();
            }

            Packet packet = new Packet(false);
            packet.generateSequenceNumber(false);
            packet.generateTimestamp(false);

            try {
                if (packet.readPacket(buf)) {
                    if (!packet.hasBigPacketException()) {
                        packetList.add(packet); 
                        if (DEBUG) { 
                            logger.log(logger.INFO, Thread.currentThread()+
                            "JMSWebSocket@"+hashCode()+" processData(): READ a PACKET="+packet);
                        }
                    }
                } else {
                    synchronized(packetLock) {
                        packetPending = packet;
                    }
                }
            } catch (BigPacketException e) {
                logger.log(logger.ERROR, "readPacket: "+e.getMessage(), e);
                WebSocketMQIPConnection conn = websocketApp.getMQIPConnection(this);
                conn.handleBigPacketException(packet, e); //XXopt close conn if too big

            } catch (IllegalArgumentException e) {
                logger.log(logger.ERROR, "readPacket: "+e.getMessage(), e);
                 WebSocketMQIPConnection conn = websocketApp.getMQIPConnection(this);
                 conn.handleIllegalArgumentExceptionPacket(packet, e);

            } catch (OutOfMemoryError err) { //XXopt close conn
		Globals.handleGlobalError(err,
                        Globals.getBrokerResources().getKString(
                        BrokerResources.M_LOW_MEMORY_READALLOC) + ": "
                        + packet.headerToString());
            }
            continue;
        }

        if (packetList == null || packetList.isEmpty()) {
            packetList = null;
            return;
        }

	if (DEBUG) {
            logger.log(logger.INFO,
            "[JMSWebSocket@"+this.hashCode()+"]processData() after processed buf: remaining="+buf.remaining());
	}

	WebSocketMQIPConnection conn = websocketApp.getMQIPConnection(this);

        for (int i = 0; i < packetList.size(); i++) {
	    try {
        	final Packet packet = packetList.get(i);
        	conn.receivedPacket(packet);
        	conn.readData();
            } catch (BrokerException e) { //XXclean
                Globals.getLogger().logStack(Logger.ERROR,
        	"Failed to process packet from connection "+this, e);
            } 
        }
        packetList.clear();
        packetList = null;
    }
}
