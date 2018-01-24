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
 * @(#)DestinationLogHandler.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import com.sun.messaging.jmq.resources.SharedResources;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.io.*;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;


/**
 * A LogHandler that logs to a JMS destination
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.service.DestinationLogHandler")
@PerLookup
public class DestinationLogHandler extends Handler {

    static boolean open = false;
    
    private static final String PREFIX = "imq.log.destination.";
    public static final String CAPACITY = "capacity";
    public static final String TOPIC = "topic";
    public static final String TIMETOLIVE = "timetolive";
    public static final String PERSIST = "persist";
    public static final String OUTPUT = "output";

    // XXX REVISIT 6/20/2003 dipol. Should be configureable
    private static int DESTINATION_BEHAVIOR = DestLimitBehavior.REMOVE_OLDEST;
    private String topic = "";
    private Destination destination = null;
    private boolean persist = false;
    private int     ttl = 300; // in seconds
    private int     capacity = 100; // in seconds
    // Resource bundle for the Logging code to use to display it's error messages.
    protected static final SharedResources rb = SharedResources.getResources();
    private DestinationList DL = Globals.getDestinationList();

    public DestinationLogHandler() {
    	LogManager lm = LogManager.getLogManager();
    	// check to see if LogManager holds all configuration else use properties
    	if(!lm.getProperty("handlers").contains(this.getClass().getName())) {
    		configure(CommGlobals.getConfig());
    	} else {
	    	String property, capacityStr, topicStr, timeToLiveStr, persistStr, outputStr;
	    	
	    	property = PREFIX + CAPACITY;
	    	capacityStr = lm.getProperty(property);
	    	property = PREFIX + TOPIC;
	    	topicStr = lm.getProperty(property);
	    	property = PREFIX + TIMETOLIVE;
	    	timeToLiveStr = lm.getProperty(property);
	    	property = PREFIX + PERSIST;
	    	persistStr = lm.getProperty(property);
	    	property = PREFIX + OUTPUT;
	    	outputStr = lm.getProperty(property);
	    	configure(capacityStr, topicStr, timeToLiveStr, persistStr, outputStr);
    	}
    }

    public void configure(Properties props) {
    	String property, capacityStr, topicStr, timeToLiveStr, persistStr, outputStr;
    	property = PREFIX + CAPACITY;
    	capacityStr = props.getProperty(property);
    	property = PREFIX + TOPIC;
    	topicStr = props.getProperty(property);
    	property = PREFIX + TIMETOLIVE;
    	timeToLiveStr = props.getProperty(property);
    	property = PREFIX + PERSIST;
    	persistStr = props.getProperty(property);
    	property = PREFIX + OUTPUT;
    	outputStr = props.getProperty(property);
    	configure(capacityStr, topicStr, timeToLiveStr, persistStr, outputStr);
    }
    
    /**
     * Configure DestinationLogHandler with the values contained in
     * the passed Properties object. 
     * <P>
     * An example of valid properties are:
     * <PRE>
     * imq.log.destination.topic=mq.log.broker
     * imq.log.destination.output=ERROR|WARNING
     * imq.log.destination.timetolive=300
     * imq.log.destination.persist=false
     * </PRE>
     * In this case prefix would be "imq.log.destination"
     *
     * @throws IllegalArgumentException if one or more property values are
     *                                  invalid. All valid properties will
     *					still be set.
     */
	public synchronized void configure(String capacityStr, String topicStr,
			String timeToLiveStr, String persistStr, String outputStr)
			throws IllegalArgumentException {

	String error_msg = null;
	//LogManager lm = LogManager.getLogManager();
	

	if (capacityStr != null) {
            try {
            	capacity = Integer.parseInt(capacityStr);
	    } catch (NumberFormatException e) {
		error_msg = rb.getString(rb.W_BAD_NFORMAT, PREFIX+CAPACITY, capacityStr);
            }
	}

	if (topicStr != null) {
            topic = topicStr;
	}

	if (timeToLiveStr != null) {
            try {
            	ttl = Integer.parseInt(timeToLiveStr);
	    } catch (NumberFormatException e) {
		error_msg = rb.getString(rb.W_BAD_NFORMAT, PREFIX + TIMETOLIVE, timeToLiveStr);
            }
	}

	if (persistStr != null) {
            persist = persistStr.equals("true");
	}

	if (outputStr != null) {
		try {
	    	int configLevel = Logger.levelStrToInt(outputStr);
	    	Level levelSetByConfig = Logger.levelIntToJULLevel(configLevel);
	        this.setLevel(levelSetByConfig);
	    } catch (IllegalArgumentException e) {
	        error_msg = (error_msg != null ? error_msg + "\n" : "") +
			PREFIX + OUTPUT + ": " + e.getMessage();
	    }
        } 

        if (error_msg != null) {
            throw new IllegalArgumentException(error_msg);
        }


        if (open) {
            this.close();
        }

        // Causes prop changes to take effect
        this.open();
    }

    /**
     * Publish LogRecord to log
     *
     * @param message	Message to write to log file
     *
     */
    public void publish(LogRecord record) {
    	
    	// ignore FORCE messages if we have explicitly been asked to ignore them
		if (!isLoggable(record)) {
			return;
		}

        if (!open) {
            return;
        }

        // Can't publish messages if we are shutting down
        if (BrokerStateHandler.isShuttingDown()) {
            return;
        }

//System.out.println("*****publish(" + topic + "): " + message );
        // We only publish messages if there are consumers. The loggin
        // destination is typically autocreated. If there are no consumers
        // it will go away.
        try {
            // Get the destination we are going to publish to
            Destination[] ds = DL.getLoadedDestination(null, topic, false);
            destination = ds[0];
            if (destination == null) {
                // No destination means no consumers
//System.out.println("******No destination");
                return;
            }

            if (destination.getCapacity() != capacity) {
                destination.setCapacity(capacity);
            }

            if (destination.getLimitBehavior() != DESTINATION_BEHAVIOR) {
                destination.setLimitBehavior(DESTINATION_BEHAVIOR);
            }
        } catch (Exception e) {
        	IOException e2 = new IOException(
                  "Could not get or configure logging destination \"" +
                      topic + "\". Closing destination logger: " + e);
          e2.initCause(e);
          this.close();
          CommGlobals.getLogger().publish(Logger.ERROR, 
          		"Could not get or configure logging destination \"" +
                      topic + "\". Closing destination logger: " + e);
        }

        // Only send message if there are consumers
        if (destination.getActiveConsumerCount() <= 0) {
//System.out.println("******No consumers");
            return;
        }

        Hashtable props = new Hashtable();
        Hashtable body = new Hashtable();

        long curTime = System.currentTimeMillis();

        PortMapper pm = Globals.getPortMapper();

        int port = 0;

        if (pm != null) {
            port = pm.getPort();
        }

        props.put("broker",  Globals.getMQAddress().getHostName() + ":" + port);
        props.put("brokerInstance", Globals.getConfigName() );
        props.put("type", topic );
        props.put("timestamp", Long.valueOf(curTime) );
        int logLevelInt = Logger.levelJULLevelToInt(record.getLevel());
	    body.put("level", Logger.levelIntToStr(logLevelInt));
	    body.put("text", record.getMessage());

        try {

        Packet pkt = new Packet(false);
        pkt.setProperties(props);
        pkt.setPacketType(PacketType.MAP_MESSAGE);
	pkt.setDestination(topic);
	pkt.setPriority(5);
	pkt.setIP(Globals.getBrokerInetAddress().getAddress());
	pkt.setPort(port);
	pkt.updateSequenceNumber();
	pkt.updateTimestamp();
	pkt.generateSequenceNumber(false);
	pkt.generateTimestamp(false);

	pkt.setIsQueue(false);
	pkt.setTransactionID(0);
	pkt.setSendAcknowledge(false);
	pkt.setPersistent(persist);
	pkt.setExpiration(ttl == 0 ? (long)0
			   : (curTime + ttl));

        ByteArrayOutputStream byteArrayOutputStream = 
                    new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = 
                    new ObjectOutputStream(byteArrayOutputStream);

        objectOutputStream.writeObject(body);
        objectOutputStream.flush();
        byteArrayOutputStream.flush();

        byte[] messageBody = byteArrayOutputStream.toByteArray();

        objectOutputStream.close();
        byteArrayOutputStream.close();
        pkt.setMessageBody(messageBody);

        PacketReference ref = PacketReference.createReference(
                              destination.getPartitionedStore(), pkt, null);

        destination.queueMessage(ref, false);
        Set s =destination.routeNewMessage(ref);
        destination.forwardMessage(s, ref);

        } catch (Exception e) {
            // Make sure we close so we don't recurse!
            this.close();
            // XXX L10N 6/20/2003 dipol
            Globals.getLogger().log(Logger.ERROR,
                "Destination logger: Can't log to destination: " + topic,
                e);
            Globals.getLogger().log(Logger.ERROR,
                "Closing destination logger.");
        }
    }


    /**
     * Open handler
     */
    public void open() {
       
        synchronized(DestinationLogHandler.class) {
            if (!open) {
                open = true;
            }
        }
    }

    /**
     * Close handler
     */
    public void close() {
        synchronized(DestinationLogHandler.class) {
            if (open) {
                open = false;
            }
        }
    }

    /**
     * Return a string description of this Handler. The descirption
     * is the class name followed by the destination we are logging to
     */
    public String toString() {
	return this.getClass().getName() + ":" + topic;
    }
    
    @Override
	public void flush() {
		// Nothing to do
	}
}
