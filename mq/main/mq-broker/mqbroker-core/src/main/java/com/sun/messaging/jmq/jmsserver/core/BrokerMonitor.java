/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import java.util.*;

public class BrokerMonitor {
    private static final long DEFAULT_INTERVAL = 60;
    private static final boolean DEFAULT_PERSIST = false;
    private static final long DEFAULT_TTL = 5 * DEFAULT_INTERVAL;
    private static final boolean DEFAULT_ENABLED = true;

    /*
     * Broker monitoring property names
     */
    private static String METRICS_PROP_PREFIX = Globals.IMQ + ".metrics.topic.";

    private static String METRICS_TIME_PROP = METRICS_PROP_PREFIX + "interval";

    private static String PERSIST_PROP = METRICS_PROP_PREFIX + "persist";

    private static String TTL_PROP = METRICS_PROP_PREFIX + "timetolive";

    private static String ENABLED_PROP = METRICS_PROP_PREFIX + "enabled";

    /*
     * Broker monitoring property values
     */
    private static long METRICS_TIME = Globals.getConfig().getLongProperty(METRICS_TIME_PROP, DEFAULT_INTERVAL) * 1000;

    static boolean PERSIST = Globals.getConfig().getBooleanProperty(PERSIST_PROP, DEFAULT_PERSIST);

    static long TTL = Globals.getConfig().getLongProperty(TTL_PROP, DEFAULT_TTL) * 1000;

    private static boolean ENABLED = Globals.getConfig().getBooleanProperty(ENABLED_PROP, DEFAULT_ENABLED);

    private static MQTimer timer = Globals.getTimer();

    Logger logger = Globals.getLogger();

    Monitor monitor = null;

    private static HashSet active = new HashSet();

    private static TimerTask task = null;

    boolean valid = true;
    boolean started = false;

    public static void shutdownMonitor() {
        if (task != null) {
            task.cancel();
        }
        active.clear();
        BrokerConfig cfg = Globals.getConfig();
        cfg.removeListener(METRICS_TIME_PROP, cl);
        cfg.removeListener(PERSIST_PROP, cl);
        cfg.removeListener(TTL_PROP, cl);
        cl = null;
    }

    public static boolean isENABLED() {
        return ENABLED;
    }

    private static ConfigListener cl = new ConfigListener() {
        @Override
        public void validate(String name, String value) {
        }

        @Override
        public boolean update(String name, String value) {
            // BrokerConfig cfg = Globals.getConfig();

            if (name.equals(METRICS_TIME_PROP)) {
                METRICS_TIME = Globals.getConfig().getLongProperty(METRICS_TIME_PROP, DEFAULT_INTERVAL) * 1000;

                synchronized (active) {
                    if (task != null) {
                        task.cancel();
                        task = new NotificationTask();
                        try {
                            timer.schedule(task, METRICS_TIME, METRICS_TIME);
                        } catch (IllegalStateException ex) {
                            Globals.getLogger().log(Logger.WARNING, "Update metrics timer schedule: " + ex, ex);
                        }
                    }
                }

            } else if (name.equals(PERSIST_PROP)) {
                PERSIST = Globals.getConfig().getBooleanProperty(PERSIST_PROP, DEFAULT_PERSIST);
            } else if (name.equals(TTL_PROP)) {
                TTL = Globals.getConfig().getLongProperty(TTL_PROP, DEFAULT_TTL) * 1000;
            } else if (name.equals(ENABLED_PROP)) {
                ENABLED = Globals.getConfig().getBooleanProperty(ENABLED_PROP, DEFAULT_ENABLED);
            }

            return true;
        }
    };

    static class NotificationTask extends TimerTask {
        @Override
        public void run() {
            Iterator itr = null;
            synchronized (active) {
                itr = (new HashSet(active)).iterator();
            }
            while (itr.hasNext()) {
                Monitor m = (Monitor) itr.next();
                m.run();
            }
        }
    }

    public static void init() {
        /*
         * The static listener 'cl' updates the static variables METRICS_TIME, PERSIST, and TTL when their corresponding
         * properties are updated.
         */
        BrokerConfig cfg = Globals.getConfig();
        cfg.addListener(METRICS_TIME_PROP, cl);
        cfg.addListener(PERSIST_PROP, cl);
        cfg.addListener(TTL_PROP, cl);
        cfg.addListener(ENABLED_PROP, cl);
    }

    /** @throws IllegalArgumentException */
    public BrokerMonitor(Destination d) throws BrokerException {
        monitor = createMonitor(d);
    }

    public static boolean isInternal(String dest) {
        return DestType.destNameIsInternal(dest);
    }

    public void start() {
        synchronized (this) {
            if (!valid) {
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
                    logger.log(Logger.INFO, "InternalError: Shutting down metrics, timer has been canceled", ex);
                }
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (!valid) {
                return;
            }
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
        synchronized (this) {
            valid = false;
            started = false;
            monitor = null;
        }
    }

    /** @throws IllegalArgumentException */
    private Monitor createMonitor(Destination d) throws BrokerException {
        String destination = d.getDestinationName();

        // parse it
        if (!DestType.destNameIsInternal(destination)) {
            throw new IllegalArgumentException("Illegal Internal Name" + destination);
        }
        String substring = destination.substring(DestType.INTERNAL_DEST_PREFIX.length());

        StringTokenizer tk = new StringTokenizer(substring, ".");

        if (!tk.hasMoreElements()) {
            throw new IllegalArgumentException("Missing type " + " for monitoring " + destination);
        }

        String type = (String) tk.nextElement();

        if (!type.equals("metrics")) {
            throw new IllegalArgumentException("Illegal type " + type + " for monitoring. Only Metrics is valid [" + destination + "]");
        }
        if (!tk.hasMoreElements()) {
            throw new IllegalArgumentException("Missing area " + " for monitoring " + destination);
        }
        String area = (String) tk.nextElement();

        // parse
        if (area.equals("broker")) {
            if (tk.hasMoreElements()) {
                throw new IllegalArgumentException(
                        "Bad name " + " for broker monitoring " + destination + " should be " + DestType.INTERNAL_DEST_PREFIX + "broker");
            }

            monitor = new BrokerMetricsMonitor(d);

        } else if (area.equals("jvm")) {
            if (tk.hasMoreElements()) {
                throw new IllegalArgumentException(
                        "Bad name " + " for broker monitoring " + destination + " should be " + DestType.INTERNAL_DEST_PREFIX + "jvm");
            }
            monitor = new JVMMonitor(d);

        } else if (area.equals("destination")) {
            if (!tk.hasMoreElements()) {
                throw new IllegalArgumentException("Missing destination type or list for broker destination monitoring " + destination);
            }

            String destArea = (String) tk.nextElement();

            if (destArea.equals("queue")) {
                if (!tk.hasMoreElements()) {
                    throw new IllegalArgumentException("Missing name " + " for broker queue monitoring " + destination);
                }
                String prefix = "metrics.destination.queue.";
                String name = substring.substring(prefix.length());
                DestinationUID duid = DestinationUID.getUID(name, true);
                Destination[] ds = Globals.getDestinationList().getDestination(d.getPartitionedStore(), duid);
                Destination dest = ds[0];
                if (!Globals.getDestinationList().canAutoCreate(true) && dest == null) {
                    throw new BrokerException(
                            Globals.getBrokerResources().getKString(BrokerResources.E_MONITOR_DEST_DISALLOWED, duid.getName(), duid.getDestType()),
                            Status.FORBIDDEN);
                }

                monitor = new DestMonitor(d, duid);

            } else if (destArea.equals("topic")) {
                if (!tk.hasMoreElements()) {
                    throw new IllegalArgumentException("Missing name " + " for broker topic monitoring " + destination);
                }
                String prefix = "metrics.destination.topic.";
                String name = substring.substring(prefix.length());
                DestinationUID duid = DestinationUID.getUID(name, false);
                Destination[] ds = Globals.getDestinationList().getDestination(d.getPartitionedStore(), duid);
                Destination dest = ds[0];
                if (!Globals.getDestinationList().canAutoCreate(false) && dest == null) {
                    throw new BrokerException(
                            Globals.getBrokerResources().getKString(BrokerResources.E_MONITOR_DEST_DISALLOWED, duid.getName(), duid.getDestType()),
                            Status.FORBIDDEN);
                }
                monitor = new DestMonitor(d, duid);
            }
        } else if (area.equals("destination_list")) {
            monitor = new DestListMonitor(d);
        } else {
            throw new IllegalArgumentException("Illegal area " + area + " for monitoring " + destination);
        }
        return monitor;

    }

    public void updateNewConsumer(Consumer c) {
        monitor.writeToSpecificMonitorConsumer(c);
    }

}

