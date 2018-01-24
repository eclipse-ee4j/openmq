/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient.protocol.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.ReadOnlyPacket;
import com.sun.messaging.jmq.io.ReadWritePacket;
import com.sun.messaging.jmq.io.PacketDispatcher;
import com.sun.messaging.jmq.jmsclient.ConnectionHandler;
import com.sun.messaging.jmq.jmsclient.ConnectionImpl;
import com.sun.messaging.jmq.jmsclient.MQAddress;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsclient.runtime.impl.BrokerInstanceImpl;
import com.sun.messaging.jmq.jmsclient.runtime.impl.ClientRuntimeImpl;
import com.sun.messaging.jmq.jmsclient.runtime.impl.DirectBrokerInstance;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQDualThreadConnection;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;
import com.sun.messaging.jmq.jmsservice.HandOffQueue;

public class DirectConnectionHandler implements ConnectionHandler {
	
	private HandOffQueue inBoundQ = null;
	private HandOffQueue outBoundQ = null;
	private DirectBrokerConnection directConnection = null;
	
	//private ConnectionImpl connection = null;
	
	private volatile boolean isClosed = false;
	
	private static boolean directDebug = Boolean.getBoolean("imq.direct.debug");
	
	public boolean isDirectMode(){
		return true;
	}
		
	public DirectConnectionHandler (Object connection) throws JMSException {
		
		//this.connection = (ConnectionImpl) connection;
		this.init();
		
		if (directDebug) {
			ConnectionImpl.getConnectionLogger().info("Direct connection handler created...");
		}
	}
	
	public DirectConnectionHandler (MQAddress addr, ConnectionImpl conn) throws JMSException {
		//this.connection = conn;
		this.init();
		
		if (directDebug) {
			ConnectionImpl.getConnectionLogger().info("Direct connection handler created...");
		}
	}
	
	private void init() throws JMSException {
		
		try {
			
			boolean isdirect = ClientRuntime.getRuntime().isEmbeddedBrokerRunning();
			
			if (isdirect) {
				
				//get direct broker instance
				ClientRuntimeImpl runtime = (ClientRuntimeImpl) ClientRuntime.getRuntime();
				//get direct connection
				this.directConnection = runtime.createDirectConnection();
					
				//get inbound q
				this.inBoundQ = this.directConnection.getBrokerToClientQueue();
				//get outbound q
				this.outBoundQ = this.directConnection.getClientToBrokerQueue();
								
			} else {
				//Direct mode must be initialized
				throw new RuntimeException ("Direct broker not initialized for this client runtime.");
			}
			
		} catch (Exception e) {
			
			//ConnectionImpl.getConnectionLogger().log (Level.WARNING, e.getMessage(), e);
			
			e.printStackTrace();
			
			JMSException jmse = new JMSException (e.getMessage());
			jmse.setLinkedException(e);
			
			throw jmse;
		}
	}
	
	/**
	 * This method is used only if "dual-thread" mode is being used and "sync replies" have been enabled
	 * 
	 * Configure the IMQDualThreadConnection to use the specified ReplyDispatcher to process reply packets
	 * 
	 * @param rd The ReplyDispatcher to be configured
	 */
	public void setReplyDispatcher(PacketDispatcher rd){
		((IMQDualThreadConnection)directConnection).setReplyDispatcher(rd);
	}
	
	public void writePacket (ReadWritePacket pkt) throws IOException {
		
		try {
			
			if (isClosed) {
				throw new IOException ("Connection is closed.");
			}
			
			pkt.updateSequenceNumber();

			pkt.updateTimestamp();

			pkt.updateBuffers();
			
			ReadWritePacket newPkt = (ReadWritePacket) pkt.clone();
			this.outBoundQ.put(newPkt);
			
			if (directDebug) {
				System.out.println("Direct connection wrote pkt..." + newPkt);
				//pkt.dump(System.out);
				System.out.flush();
			}
			
		} catch (IOException ioe) {
			throw ioe;
		} catch (Exception e) {
			IOException ioe = new IOException(e.getMessage());
			throw ioe;
		}
		
		//XXX write to inbound for short-circuit client only testing
		//this.inBoundQ.put(newPkt);
	}
	
	public ReadWritePacket readPacket () throws IOException {
		
		ReadWritePacket pkt = null;
		
		//ConnectionImpl.getConnectionLogger().info("Direct connection reading pkt ...");
		
		try {
			
			if (isClosed == false) {
				pkt = (ReadWritePacket) this.inBoundQ.take();
			}
			
			if (directDebug) {
				System.out.println("Direct connection read pkt..." + pkt);
				//pkt.dump(System.out);
				System.out.flush();
			}
			
			if (isClosed) {
				throw new IOException ("Connection is closed.");
			}
			
		} catch (InterruptedException inte) {
			;
		}
		
		return pkt;
	}
	
	public synchronized void close() throws IOException {
		
		if (isClosed) {
			return;
		}
		
		// TODO Auto-generated method stub
		ReadWritePacket pkt = new ReadWritePacket();
		
		pkt.setPacketType(PacketType.NONE);
		
		this.isClosed = true;
		
		//wake up read channel
		try {
			this.inBoundQ.put(pkt);
		} catch (Exception e) {
			IOException ioe = new IOException (e.getMessage());
			throw ioe;
		}
	}

	public String getBrokerAddress() {
		// TODO Auto-generated method stub
		return "localhost";
	}

	public String getBrokerHostName() {
		// TODO Auto-generated method stub
		return "localhost";
	}

	public InputStream getInputStream() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public int getLocalPort() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	public OutputStream getOutputStream() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public Packet fetchReply(){
		return ((IMQDualThreadConnection)directConnection).fetchReply();
	}
	
	public void configure(Properties configuration) throws IOException {
        	        	
	}

}
