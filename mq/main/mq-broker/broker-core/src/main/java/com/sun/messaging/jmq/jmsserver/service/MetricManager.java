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
 * @(#)MetricManager.java	1.28 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.util.MetricData;
import com.sun.messaging.jmq.util.timer.MQTimer;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;

/**
 * MetricManager manages configuration and running of metric reports
 */

public class MetricManager implements ConfigListener
{

    // Hashtable of MetricCounters. Key is service name.
    // Holds totals for that service's connections that have gone away.
    private Hashtable deadTotalsByService = new Hashtable();

    private MetricTask      task = null;

    private long           lastSampleTime = 0;
    private MetricCounters lastSample;

    private static String  INTERVAL_PROPERTY = Globals.IMQ +
        ".metrics.interval";

    private static String  ENABLED_PROPERTY = Globals.IMQ +
        ".metrics.enabled";

    private BrokerResources rb = Globals.getBrokerResources();
    private MQTimer timer = Globals.getTimer();
    private Logger logger  = Globals.getLogger();

    public static boolean isEnabled() {
        return Globals.getConfig().getBooleanProperty(ENABLED_PROPERTY);
    }

    public MetricManager() {
        lastSample = new MetricCounters();
    }

    /**
     * Used by connections that are going away to deposit their
     * totals, so they won't be lost.
     */
    public synchronized void depositTotals(String service, MetricCounters counters) {
	MetricCounters mc = (MetricCounters)deadTotalsByService.get(service);
	if (mc == null) {
	    mc = new MetricCounters();
	    deadTotalsByService.put(service, mc);
	}
        mc.update(counters);
    }

    public synchronized void reset() {
        deadTotalsByService.clear();
        lastSample = new MetricCounters();
    }

    public synchronized void setInterval(long interval) {
        if (interval > 0 && isEnabled()) {
            // Reschedule task at new interval. Unfortunately the timer
            // class doesn't let us reschedule the old task, so we must
            // create a new task.
            if (task != null) {
                task.cancel();
            }
            task = new MetricTask();
            timer.schedule(task, interval, interval * 1000);
        } else {
            // Cancel the task
            if (task != null) {
                task.cancel();
            }
        }
    }

    public void validate(String name, String value)
        throws PropertyUpdateException {

        if (!name.equals(INTERVAL_PROPERTY)) {
            throw new PropertyUpdateException(
                rb.getString(rb.X_BAD_PROPERTY, name));
        }

        getLongProperty(name, value);
    }

    public boolean update(String name, String value) {

        if (name.equals(INTERVAL_PROPERTY)) {
            try {
                setInterval(getLongProperty(name, value));
                return true;
            } catch (PropertyUpdateException e) {
            }
        }
        return false;
    }

    public long getLongProperty(String name, String value)
        throws PropertyUpdateException {

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PropertyUpdateException(
                rb.getString(rb.X_BAD_PROPERTY_VALUE, name + "=" + value));
        }
    }

    public void setParameters(Hashtable params) {
        String value;

        try {
            value = (String)params.get(INTERVAL_PROPERTY);
            validate(INTERVAL_PROPERTY, value);
            update(INTERVAL_PROPERTY, value);
        } catch (PropertyUpdateException e) {
            logger.logStack(Logger.WARNING,
                            BrokerResources.W_METRIC_BAD_CONFIG, e);
        }
    }

    /**
     * Get the metric counters for the specified service. If serviceName
     * is null then do it for all services
     */
    public synchronized MetricCounters getMetricCounters(String serviceName) {

        ConnectionManager cm = Globals.getConnectionManager();

        MetricCounters totals = new MetricCounters();

        // Add counters for connections that no longer exist
        if (serviceName == null) {
            // Sum values for all services
            Enumeration e;
            for (e = deadTotalsByService.elements(); e.hasMoreElements(); ) {
                totals.update((MetricCounters)e.nextElement());
            }
        } else {
            // Sum values for just the specified service
	    MetricCounters deadTotals =
                    (MetricCounters)deadTotalsByService.get(serviceName);
	    if (deadTotals != null) {
                totals.update(
                    (MetricCounters)deadTotalsByService.get(serviceName));
            }
        }

        // Sum totals for all active connections for this service
        // We synchronize since connections may be comming and going
        int n = 0;
        synchronized (cm) {
            Collection connections = cm.values();
            Iterator itr = connections.iterator();
            while (itr.hasNext()) {
                Connection con = (Connection)itr.next();
	        Service svc = con.getService();
                // See if connection belongs to the service
	        if (serviceName == null || serviceName.equals(svc.getName())) {
                    if (con instanceof IMQConnection) {
                        totals.update(((IMQConnection)con).getMetricCounters());
                    } else {
                        // XXX handle other counters
                    }
                    n++;
	        }
            }
        }

	// Get thread information
	ServiceManager sm = Globals.getServiceManager();
	Service svc = null;
	Iterator iter = null;
	if (serviceName == null) {
            Set s = sm.getAllActiveServices();
            if (s != null) {
	        iter = s.iterator();
            }
        } else {
	    Vector v = new Vector(1);
	    v.add(serviceName);
	    iter = v.iterator();
        }
	    
	while (iter != null && iter.hasNext()) {
	    svc = sm.getService((String)iter.next());
	    if (svc instanceof IMQService) {
	        totals.threadsActive +=
		    ((IMQService)svc).getActiveThreadpool();
	        totals.threadsHighWater +=
		    ((IMQService)svc).getMaxThreadpool();
	        totals.threadsLowWater +=
		    ((IMQService)svc).getMinThreadpool();
	    }
        }

        Runtime rt = Runtime.getRuntime();
        totals.totalMemory = rt.totalMemory();
        totals.freeMemory  = rt.freeMemory();
        totals.nConnections = n;

        totals.timeStamp = System.currentTimeMillis();
        return totals;
    }

    /**
     * Gather performance data for all connections in a broker,
     * compute dervied numbers, and return in one convenience class
     */
    public synchronized MetricData getMetrics() {
        MetricData md = new MetricData();

        Runtime rt = Runtime.getRuntime();

        long currentTime = System.currentTimeMillis();
        // long elapsedSecs = (currentTime - lastSampleTime) / 1000;
        float elapsedSecs = (currentTime - lastSampleTime) / (float)1000;

        MetricCounters totals = getMetricCounters(null);

        // Copy values into MetricData
        md.timestamp = currentTime;
        md.totalMemory = rt.totalMemory();
        md.freeMemory  = rt.freeMemory();
        md.nConnections = totals.nConnections;

        // Totals are straight copies
        md.setTotals(totals);

        // Rates must be computed
        md.rates.messagesIn = (long)
            ((totals.messagesIn - lastSample.messagesIn) / elapsedSecs);
        md.rates.messageBytesIn = (long)
            ((totals.messageBytesIn - lastSample.messageBytesIn) / elapsedSecs);
        md.rates.packetsIn = (long)
            ((totals.packetsIn - lastSample.packetsIn) / elapsedSecs);
        md.rates.packetBytesIn = (long)
            ((totals.packetBytesIn - lastSample.packetBytesIn) / elapsedSecs);
        md.rates.messagesOut = (long)
            ((totals.messagesOut - lastSample.messagesOut) / elapsedSecs);
        md.rates.messageBytesOut = (long)
            ((totals.messageBytesOut - lastSample.messageBytesOut) /
                                                                elapsedSecs);
        md.rates.packetsOut = (long)
            ((totals.packetsOut - lastSample.packetsOut) / elapsedSecs);
        md.rates.packetBytesOut = (long)
            ((totals.packetBytesOut - lastSample.packetBytesOut) / elapsedSecs);

        lastSampleTime = currentTime;
        lastSample = totals;

        return md;
    }

    /**
     * The task scheduled on the timer
     */
    public class MetricTask extends TimerTask {
        /**
         * Run!
         */
        public void run() {
            MetricData md = getMetrics();
            logger.log(Logger.INFO, "\n" + md.toString());
        }
    }
}
