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
 * @(#)PacketFormatter.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.logging;

import java.util.*;
import java.util.logging.*;
import java.io.*;

import com.sun.messaging.jmq.io.ReadOnlyPacket;

/**
 * MQ packet formatter.
 *
 * This is also a utility class that may be used to format MQ packets.
 */
public class PacketFormatter extends SimpleFormatter {

    /**
     * Format the log record.  If this is a MQ packet record, it is formatted
     * to the packet format.  Otherwise, the simple formatter format is used.
     */
    public synchronized String format(LogRecord record) {

        String str = doFormat (record);

        if ( str == null ) {
            str = super.format(record);
        }

        return str;
    }

    /**
     * Check if this is a MQ packet.  If yes, calls formatPacket method to
     * format the packet.
     */
    public static String doFormat (LogRecord record) {

        String lstring = null;

        ReadOnlyPacket pkt = getPacket(record);

        if ( pkt != null ) {

            long time = record.getMillis();
            Date date = new Date();
            date.setTime(time);

            lstring = date.toString() + "  " + record.getMessage() + "\n";

            lstring = lstring + formatPkt(pkt);
        }

        return lstring;
    }

    /**
     * Get MQ packet from the log record.
     */
    public static ReadOnlyPacket getPacket(LogRecord record) {

        ReadOnlyPacket pkt = null;

        Object obj[] = record.getParameters();

        if (obj != null) {

            for (int i = 0; i < obj.length; i++) {
                if (obj[i] instanceof ReadOnlyPacket ) {
                    pkt = (ReadOnlyPacket) obj[i];
                    break;
                }
            }
        }

        return pkt;
    }

    /**
     * Format MQ packet.
     */
    public static String formatPkt(ReadOnlyPacket pkt) {

        String out = null;

        try {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            pkt.dump(ps);

            ps.flush();
            ps.close();
            baos.close();

            out = baos.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return out;
    }

}
