/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import java.util.*;
import java.io.*;
import java.net.*;

abstract class Monitor {
    Destination d = null;

    Monitor(Destination d) {
        this.d = d;
    }

    protected abstract Hashtable getMonitorData();

    public void run() {
        Packet p = new Packet(false);
        Hashtable entries = getMonitorData();

        if (entries == null) {
            return;
        }

        if (writeMap(p, entries, d.getDestinationName())) {

            try {
                PacketReference ref = PacketReference.createReference(d.getPartitionedStore(), p, null);
                d.queueMessage(ref, false);
                Set s = d.routeNewMessage(ref);
                d.forwardMessage(s, ref);
            } catch (BrokerException ex) {
                Globals.getLogger().log(Logger.DEBUG, "Unable to writeMap for " + " metrics" + d, ex);
            } catch (SelectorFormatException ex) {
                Globals.getLogger().logStack(Logger.DEBUG, "Internal Error ", ex);
            }
        } else {
            Globals.getLogger().log(Logger.DEBUG, "Unable to writeMap for " + " metrics" + d);
        }
    }

    public void writeToSpecificMonitorConsumer(Consumer c) {
        Packet p = new Packet(true);
        Hashtable entries = getMonitorData();

        if (entries == null) {
            return;
        }
        if (c == null) {
            return;
        }

        if (writeMap(p, entries, d.getDestinationName())) {
            try {
                PacketReference ref = PacketReference.createReference(d.getPartitionedStore(), p, null);
                d.queueMessage(ref, false);
                ArrayList arl = new ArrayList(1);
                arl.add(c);
                ref.store(arl);
                c.routeMessage(ref, false);
            } catch (BrokerException ex) {
                Globals.getLogger().log(Logger.DEBUG, "Unable to writeMap for " + " metrics " + d + " : targeted for " + c.getConsumerUID(), ex);
            }
        } else {
            Globals.getLogger().log(Logger.DEBUG, "Unable to writeMap for " + " metrics" + d + " : targeted for " + c.getConsumerUID());
        }
    }

    private static boolean writeMap(Packet pkt, Hashtable entries, String destination) {
        try {

            // write header information
            Hashtable props = new Hashtable();
            Long curTime = Long.valueOf(System.currentTimeMillis());

            props.put("type", destination);
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
            pkt.setExpiration(BrokerMonitor.TTL == 0 ? (long) 0 : (curTime.longValue() + BrokerMonitor.TTL));

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

            objectOutputStream.writeObject(entries);
            objectOutputStream.flush();
            byteArrayOutputStream.flush();

            byte[] messageBody = byteArrayOutputStream.toByteArray();

            objectOutputStream.close();
            byteArrayOutputStream.close();
            pkt.setMessageBody(messageBody);

            return true;
        } catch (Exception e) {
            Globals.getLogger().log(Logger.ERROR, "Error sending metrics data", e);
            return false;
        }
    }

}

