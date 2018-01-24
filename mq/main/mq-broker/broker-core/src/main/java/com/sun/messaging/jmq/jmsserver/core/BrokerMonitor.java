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
 * @(#)BrokerMonitor.java	1.31 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.MetricData;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import java.util.*;
import java.io.*;
import java.net.*;



public class BrokerMonitor
{
    private static final long		DEFAULT_INTERVAL	= 60;
    private static final boolean	DEFAULT_PERSIST		= false;
    private static final long		DEFAULT_TTL		= 5 * DEFAULT_INTERVAL;
    private static final boolean	DEFAULT_ENABLED		= true;

    /*
     * Broker monitoring property names
     */
    private static String METRICS_PROP_PREFIX = 
        Globals.IMQ + ".metrics.topic.";

    private static String METRICS_TIME_PROP = 
	METRICS_PROP_PREFIX + "interval";

    private static String PERSIST_PROP = 
	METRICS_PROP_PREFIX + "persist";

    private static String TTL_PROP = 
	METRICS_PROP_PREFIX + "timetolive";

    private static String ENABLED_PROP = 
	METRICS_PROP_PREFIX + "enabled";

    /*
     * Broker monitoring property values
     */
    private static long METRICS_TIME =
         Globals.getConfig().getLongProperty(
             METRICS_TIME_PROP,
             DEFAULT_INTERVAL) * 1000;

    static boolean PERSIST =
         Globals.getConfig().getBooleanProperty(
             PERSIST_PROP, DEFAULT_PERSIST);

    static long TTL =
         Globals.getConfig().getLongProperty(
             TTL_PROP, DEFAULT_TTL) * 1000;

    private static boolean ENABLED =
         Globals.getConfig().getBooleanProperty(
             ENABLED_PROP, DEFAULT_ENABLED);

    private static MQTimer timer = Globals.getTimer();

    Logger logger = Globals.getLogger();

    Monitor monitor = null;

    private static HashSet active = new HashSet();

    private static TimerTask task = null;

    boolean valid = true;
    boolean started = false;

    public static void shutdownMonitor() {
        if (task != null)
            task.cancel();
        active.clear();
	BrokerConfig cfg = Globals.getConfig();
	cfg.removeListener(METRICS_TIME_PROP, cl);
	cfg.removeListener(PERSIST_PROP, cl);
	cfg.removeListener(TTL_PROP, cl);
        cl = null;
    }

    public static final boolean isENABLED() {
        return ENABLED;
    }

    private static ConfigListener cl = new ConfigListener()  {
        public void validate(String name, String value)
    			throws PropertyUpdateException {
        }
            
        public boolean update(String name, String value) {
            //BrokerConfig cfg = Globals.getConfig();

            if (name.equals(METRICS_TIME_PROP))  {
	        METRICS_TIME = Globals.getConfig().getLongProperty(
	                METRICS_TIME_PROP, DEFAULT_INTERVAL) * 1000;

                synchronized (active) {
                    if (task != null) {
		       task.cancel();
                       task = new NotificationTask();
                       try {
                           timer.schedule(task, METRICS_TIME, METRICS_TIME);
                       } catch (IllegalStateException ex) {
                           Globals.getLogger().log(Logger.WARNING, 
                           "Update metrics timer schedule: "+ex, ex);
                       }
                    }
                }
			
            } else if (name.equals(PERSIST_PROP))  {
                PERSIST = Globals.getConfig().getBooleanProperty(
             		PERSIST_PROP, DEFAULT_PERSIST);
            } else if (name.equals(TTL_PROP))  {
                TTL = Globals.getConfig().getLongProperty(
             		TTL_PROP, DEFAULT_TTL) * 1000;
            } else if (name.equals(ENABLED_PROP))  {
                ENABLED = Globals.getConfig().getBooleanProperty(
             		ENABLED_PROP, DEFAULT_ENABLED);
	    }

            return true;
        }
    };
   
    static class NotificationTask extends TimerTask
    {
        public void run() {
            Iterator itr = null; 
            synchronized(active) {
                itr = (new HashSet(active)).iterator();
            }
            while (itr.hasNext()) {
                Monitor m = (Monitor)itr.next();
                m.run();
            }
        }
    }

    public static void init()  {
	/*
	 * The static listener 'cl' updates the 
	 * static variables METRICS_TIME, PERSIST,
	 * and TTL when their corresponding
	 * properties are updated.
	 */
	BrokerConfig cfg = Globals.getConfig();
	cfg.addListener(METRICS_TIME_PROP, cl);
	cfg.addListener(PERSIST_PROP, cl);
	cfg.addListener(TTL_PROP, cl);
	cfg.addListener(ENABLED_PROP, cl);
    }

    public BrokerMonitor(Destination d)
        throws IllegalArgumentException, BrokerException
    {
        monitor = createMonitor(d);
    }



    public static boolean isInternal(String dest)
    {
        return DestType.destNameIsInternal(dest);
    }


    public void start() {
        synchronized (this) {
            if (!valid)  {
                return;
            }
            if (!started) {
                started = true;
            } else {
                return;
            }
        }
        synchronized (active) {
            active.add(monitor);
            if (task == null) {
               task = new NotificationTask();
               try {
                   timer.schedule(task, METRICS_TIME, METRICS_TIME);
               } catch (IllegalStateException ex) {
                   logger.log(Logger.INFO,"InternalError: Shutting down metrics, timer has been canceled", ex);
               }
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (!valid) return;
            if (started) {
                started = false;
            } else {
                return;
            }
        }
        synchronized (active) {
            active.remove(monitor);
            if (active.size() == 0) {
               task.cancel();
               task = null;
            }
        }
    }

    public void destroy() {
        stop();
        synchronized(this) {
            valid = false;
            started = false;
            monitor = null;
        }
    }

    private Monitor createMonitor(Destination d)
        throws IllegalArgumentException, BrokerException
    {
        String destination = d.getDestinationName();

        //parse it
        if (!DestType.destNameIsInternal(destination)) {
            throw new IllegalArgumentException("Illegal Internal Name"
                 + destination);
        }
        String substring = destination.substring(
                  DestType.INTERNAL_DEST_PREFIX.length());

        StringTokenizer tk = new StringTokenizer(
                 substring, ".");

        if (!tk.hasMoreElements()) {
            throw new IllegalArgumentException("Missing type "
                + " for monitoring " + destination);
        }

        String type=(String)tk.nextElement();

        if (!type.equals("metrics")) {
            throw new IllegalArgumentException("Illegal type " + type
               + " for monitoring. Only Metrics is valid ["
                  + destination + "]");
        }
        if (!tk.hasMoreElements()) {
            throw new IllegalArgumentException("Missing area "
                + " for monitoring " + destination);
        }
        String area=(String)tk.nextElement();

        // parse
        if (area.equals("broker")) {
            if (tk.hasMoreElements()) {
                throw new IllegalArgumentException("Bad name "
                    + " for broker monitoring " + destination
                    + " should be " + DestType.INTERNAL_DEST_PREFIX
                    + "broker");
            }

            monitor = new BrokerMetricsMonitor(d);
            
        } else if (area.equals("jvm")) {
            if (tk.hasMoreElements()) {
                throw new IllegalArgumentException("Bad name "
                    + " for broker monitoring " + destination
                    + " should be " + DestType.INTERNAL_DEST_PREFIX 
                    + "jvm");
            }
            monitor = new JVMMonitor(d);

        } else if (area.equals("destination")) {
            if (!tk.hasMoreElements()) {
                throw new IllegalArgumentException(
		    "Missing destination type or list for broker destination monitoring "
			+ destination);
            }

            String destArea=(String)tk.nextElement();

	    if (destArea.equals("queue")) {
                if (!tk.hasMoreElements()) {
                    throw new IllegalArgumentException("Missing name "
                    + " for broker queue monitoring " + destination);
                }
                String prefix = "metrics.destination.queue.";
                String name = substring.substring(prefix.length());
                DestinationUID duid =DestinationUID.getUID(name, true);
                Destination[] ds = Globals.getDestinationList().getDestination(
                                       d.getPartitionedStore(), duid);
                Destination dest = ds[0];
                if (!Globals.getDestinationList().canAutoCreate(true) && dest == null) {
                    throw new BrokerException(
                       Globals.getBrokerResources().getKString(
                           BrokerResources.E_MONITOR_DEST_DISALLOWED,
                           duid.getName(),
                           duid.getDestType()),Status.FORBIDDEN);
                }
               

                monitor = new DestMonitor(d, duid);

            } else if (destArea.equals("topic")) {
                if (!tk.hasMoreElements()) {
                    throw new IllegalArgumentException("Missing name "
                    + " for broker topic monitoring " + destination);
                }
                String prefix = "metrics.destination.topic.";
                String name = substring.substring(prefix.length());
                DestinationUID duid =DestinationUID.getUID(name, false);
                Destination[] ds = Globals.getDestinationList().getDestination(
                                       d.getPartitionedStore(), duid);
                Destination dest = ds[0];
                if (!Globals.getDestinationList().canAutoCreate(false) && dest == null) {
                    throw new BrokerException(
                       Globals.getBrokerResources().getKString(
                           BrokerResources.E_MONITOR_DEST_DISALLOWED,
                           duid.getName(),
                           duid.getDestType()),Status.FORBIDDEN);
                }
                monitor = new DestMonitor(d, duid);
            }
        } else if (area.equals("destination_list")) {
                monitor = new DestListMonitor(d);
        } else {
            throw new IllegalArgumentException("Illegal area "
                + area + " for monitoring " + destination);
        }
        return monitor;

    }


    public void updateNewConsumer(Consumer c) {
        monitor.writeToSpecificMonitorConsumer(c);
    }
     

}



abstract class Monitor 
{
    Destination d = null;

    public Monitor(Destination d)
    {
        this.d = d;
    }

    protected abstract Hashtable getMonitorData();


    public void run() {
        Packet p = new Packet(false);
	Hashtable entries = getMonitorData();

	if (entries == null)  {
	    return;
	}

        if (writeMap(p, entries, d.getDestinationName())) {
           
            try {
                PacketReference ref = PacketReference.createReference(
                                      d.getPartitionedStore(), p, null);
                d.queueMessage(ref, false);
                Set s = d.routeNewMessage(ref);
                d.forwardMessage(s, ref);
            } catch (BrokerException ex) {
                Globals.getLogger().log(Logger.DEBUG,"Unable to writeMap for "
               + " metrics" + d, ex);
            } catch (SelectorFormatException ex) {
                Globals.getLogger().logStack(Logger.DEBUG,"Internal Error ", ex);
            }
        } else {
            Globals.getLogger().log(Logger.DEBUG,"Unable to writeMap for "
               + " metrics" + d);
        }
    }

    public void writeToSpecificMonitorConsumer(Consumer c)
    {
        Packet p = new Packet(true);
	Hashtable entries = getMonitorData();

	if (entries == null)  {
	    return;
	}
        if (c == null) {
            return;
        }

        if (writeMap(p, entries, d.getDestinationName())) {
            try {
                PacketReference ref = PacketReference.createReference(
                                      d.getPartitionedStore(), p, null);
                d.queueMessage(ref, false);
                ArrayList arl = new ArrayList(1);
                arl.add(c);
                ref.store(arl);
                c.routeMessage(ref, false);
            } catch (BrokerException ex) {
                Globals.getLogger().log(Logger.DEBUG,"Unable to writeMap for "
               + " metrics " + d + " : targeted for " + c.getConsumerUID(), ex);
            }
        } else {
            Globals.getLogger().log(Logger.DEBUG,"Unable to writeMap for "
               + " metrics" + d+ " : targeted for " + c.getConsumerUID());
        }
    }
 

    private static boolean writeMap(Packet pkt, Hashtable entries,
             String destination)
    {
        try {

            // write header information
            Hashtable props = new Hashtable();
	    Long curTime = Long.valueOf(System.currentTimeMillis());

            props.put("type",destination);
            props.put("timestamp", curTime);

	    MQAddress addr = Globals.getMQAddress();
            props.put("brokerAddress", addr.toString());
            props.put("brokerHost", addr.getHostName());
            props.put("brokerPort", Integer.valueOf(addr.getPort()));

            pkt.setProperties(props);
            pkt.setPacketType(PacketType.MAP_MESSAGE);
            pkt.setDestination(destination);
            pkt.setPriority(5);
            pkt.setIP(InetAddress.getLocalHost().getAddress());
            pkt.updateSequenceNumber();
            pkt.updateTimestamp();
            pkt.generateSequenceNumber(false);
            pkt.generateTimestamp(false);

            // should also set port, but I'm not sure how
            pkt.setIsQueue(false);
            pkt.setTransactionID(0);
            pkt.setSendAcknowledge(false);
            pkt.setPersistent(BrokerMonitor.PERSIST);
            pkt.setExpiration(BrokerMonitor.TTL == 0 ? (long)0
                   : (curTime.longValue()
                      + BrokerMonitor.TTL));
                  

            ByteArrayOutputStream byteArrayOutputStream = 
                    new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = 
                    new ObjectOutputStream(byteArrayOutputStream);

            objectOutputStream.writeObject(entries);
            objectOutputStream.flush();
            byteArrayOutputStream.flush();

            byte[] messageBody = byteArrayOutputStream.toByteArray();

            objectOutputStream.close();
            byteArrayOutputStream.close();
            pkt.setMessageBody(messageBody);

            return true;
        } catch (Exception e) {
            Globals.getLogger().log(Logger.ERROR,
               "Error sending metrics data",e);
            return false;
        }
    }

}

class JVMMonitor extends Monitor
{
    public JVMMonitor(Destination d) {
        super(d);
    }

    protected Hashtable getMonitorData() {

        Hashtable mapMessage = new Hashtable();

        MetricManager mm = Globals.getMetricManager();
        MetricData md = mm.getMetrics();
        mapMessage.put("freeMemory", 
               Long.valueOf(md.freeMemory));
        mapMessage.put("maxMemory", 
               Long.valueOf(Runtime.getRuntime().maxMemory()));
        mapMessage.put("totalMemory", 
               Long.valueOf(md.totalMemory));

        return mapMessage;
    }
}

class DestListMonitor extends Monitor
{
    public DestListMonitor(Destination d) {
         super(d);
    }

    protected Hashtable getMonitorData() {

        Hashtable mapMessages = new Hashtable();

	Iterator[] itrs = Globals.getDestinationList().getAllDestinations(
                              d.getPartitionedStore());
        Iterator itr = itrs[0]; //PART
	while (itr.hasNext()) {
	    Destination oneDest = (Destination)itr.next();
	    Hashtable values;
	    String key;

	    if (oneDest.isInternal() || 
		oneDest.isAdmin() ||
	        (oneDest.getDestinationName().equals(MessageType.JMQ_ADMIN_DEST)) ||
            (oneDest.getDestinationName().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)))  {
		continue;
	    }

	    values = new Hashtable();

	    if (oneDest.isQueue())  {
	        key = "mq.metrics.destination.queue." + oneDest.getDestinationName();
	        values.put("type", "queue");
	    } else  {
	        key = "mq.metrics.destination.topic." + oneDest.getDestinationName();
	        values.put("type", "topic");
	    }

	    values.put("name", oneDest.getDestinationName());
	    values.put("isTemporary", Boolean.valueOf(oneDest.isTemporary()));
	    
	    mapMessages.put(key, values);
	}

        return mapMessages;
    }
}

class DestMonitor extends Monitor
{
    DestinationUID target = null;

    public DestMonitor(Destination d,
         DestinationUID target) {
         super(d);
         this.target = target;
    }

    protected Hashtable getMonitorData() {

	if (target == null)  {
	    return (null);
	}

        Destination[] ds = Globals.getDestinationList().getDestination(
                              d.getPartitionedStore(), target);
        Destination td = ds[0];

	if (td == null)  {
	    return (null);
	}

        Hashtable values = new Hashtable(td.getMetrics());
        return values;
    }
        

}

class BrokerMetricsMonitor extends Monitor
{

    public BrokerMetricsMonitor(Destination d) {
        super(d);
    }

    protected Hashtable getMonitorData() {

        Hashtable mapMessage = new Hashtable();

        MetricManager mm = Globals.getMetricManager();
        MetricData md = mm.getMetrics();
        mapMessage.put("numConnections", 
               Long.valueOf((long)md.nConnections));
        mapMessage.put("numMsgsIn", 
               Long.valueOf((long)md.totals.messagesIn));
        mapMessage.put("numMsgsOut", 
               Long.valueOf((long)md.totals.messagesOut));
        mapMessage.put("numMsgs", 
               Long.valueOf((long)Globals.getDestinationList().totalCount()));

        mapMessage.put("msgBytesIn", 
               Long.valueOf((long)md.totals.messageBytesIn));
        mapMessage.put("msgBytesOut", 
               Long.valueOf((long)md.totals.messageBytesOut));
        mapMessage.put("numPktsIn", 
               Long.valueOf((long)md.totals.packetsIn));
        mapMessage.put("numPktsOut", 
               Long.valueOf((long)md.totals.packetsOut));
        mapMessage.put("pktBytesIn", 
               Long.valueOf((long)md.totals.packetBytesIn));
        mapMessage.put("pktBytesOut", 
               Long.valueOf((long)md.totals.packetBytesOut));

        mapMessage.put("totalMsgBytes", 
               Long.valueOf(Globals.getDestinationList().totalBytes()));

	/*
	 * Calculate number of destinations.
	 * We cannot use Destination.destinationsSize() here because
	 * that includes all destinations. We need to filter out
	 * internal/admin destinations - basically what is returned
	 * by DestListMonitor.
	 */
	Iterator[] itrs = Globals.getDestinationList().getAllDestinations(null);
        Iterator itr = itrs[0];
	long numDests = 0;
	while (itr.hasNext()) {
	    Destination oneDest = (Destination)itr.next();

	    if (oneDest.isInternal() || 
		oneDest.isAdmin() ||
	        (oneDest.getDestinationName().equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)) ||
	        (oneDest.getDestinationName().equals(MessageType.JMQ_ADMIN_DEST)))  {
		continue;
	    }
	    numDests++;
	}

        mapMessage.put("numDestinations", Long.valueOf(numDests));

        return mapMessage;
    }
}
