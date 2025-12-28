/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.io;

import java.io.*;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.StringTokenizer;

import com.sun.messaging.jmq.resources.*;

/**
 * A table of PortMapperEntries. Knows how to parse and generate the output used by the portmapper service.
 */
public class PortMapperTable {

    private static boolean DEBUG = false;

    public static final int PORTMAPPER_VERSION = 101;
    private static final String DOT = ".";
    private static final String NEWLINE = "\n";
    private static final String SPACE = " ";
    private static final byte NEWLINE_BYTE = 10;
    private static final byte DOT_BYTE = 46;

    private String brokerInstance = "???";
    private String packetVersion = "???";
    private String version = "???";

    private final Map<String, PortMapperEntry> table;

    /**
     * Construct an unititialized system message ID. It is assumed the caller will set the fields either explicitly or via
     * readID()
     */
    public PortMapperTable() {
        table = Collections.synchronizedMap(new LinkedHashMap<>());
        version = Integer.toString(PORTMAPPER_VERSION);
    }

    /**
     * Add a service
     */
    public void add(PortMapperEntry e) {
        table.remove(e.getName());
        table.put(e.getName(), e);
    }

    /**
     * get a service
     */

    public PortMapperEntry get(String name) {
        return table.get(name);
    }

    /**
     * Remove a service
     */
    public void remove(String name) {
        table.remove(name);
    }

    /**
     * Set the broker instance name
     */
    public void setBrokerInstanceName(String name) {
        brokerInstance = name;
    }

    /**
     * Set the broker version string
     */
    public void setPacketVersion(String s) {
        packetVersion = s;
    }

    /**
     * Get the broker version number
     */
    public String getPacketVersion() {
        return packetVersion;
    }

    /**
     * Get a hashtable containing the servicename/PortMapperEntry pairs
     */
    public Map<String, PortMapperEntry> getServices() {
        return table;
    }

    @Override
    public String toString() {
        return version + " " + brokerInstance + " " + packetVersion + table;
    }

    /**
     * Write the portmapper data to the specified DataOutputStream. The formate of the data is:
     *
     * <PRE>{@code
     *  <portmapper version><SP><broker instance name><SP>broker version><NL>
     *  <service name><SP><protocol><SP><type><SP><port><NL>
     *  <.><NL>
     *
     *  Where:
     *
     *  <portmapper version>Portmapper numeric version string (ie "100").
     *  <broker version>    Broker version string (ie "2.0").
     *  <NL>                Newline character (octal 012)
     *  <service name>      Alphanumeric string. No embedded whitespace.
     *  <space>             A single space character
     *  <protocol>          Transport protocol. Typically "tcp" or "ssl"
     *  <service>           Service type. Typically "NORMAL", "ADMIN" or
     *                      "PORTMAPPER"
     *  <port>              Numeric string. Service port number
     *  <.>                 The '.' (dot) character
     *
     *  An example would be:
     *
     *  101 jmqbroker 2.0
     *  portmapper tcp PORTMAPPER 7575
     *  jms tcp NORMAL 59510
     *  admin tcp ADMIN 59997
     *  ssljms ssl NORMAL 42322
     *  .
     *
     * }</PRE>
     *
     * @param out OutputStream to write ID to
     *
     */
    public void write(OutputStream out) throws IOException {

        StringBuilder data = new StringBuilder();
        String name;
        PortMapperEntry pme;

        data.append(PORTMAPPER_VERSION).append(SPACE).append(brokerInstance).append(SPACE).append(packetVersion).append(NEWLINE);

        for (Iterator<String> e = table.keySet().iterator(); e.hasNext();) {
            name = e.next();
            pme = table.get(name);
            data.append(pme).append(NEWLINE);
        }

        data.append(DOT + NEWLINE);

        out.write(data.toString().getBytes("ASCII"));
        out.flush();
    }

    /**
     * Read the data from the specified DataInputStream. The format of the data is assumed to match that generated by write.
     *
     * @param is InputStream to read from
     *
     */
    public void read(InputStream is) throws IOException {

        BufferedInputStream in = new BufferedInputStream(is);
        /*
         * IH: Increased size of buffer from 128 to 2048. There shouldn't really be a hard limit here.
         */
        byte[] buffer = new byte[2048];

        if (DEBUG) {
            System.err.println(this.getClass().getName() + ".read():");
        }

        // Read first line
        int nBytes = readLine(in, buffer);
        if (nBytes < 0) {
            throw new IOException(SharedResources.getResources().getString(SharedResources.X_PORTMAPPER_SOCKET_CLOSED_UNEXPECTEDLY));
        }

        StringTokenizer st = new StringTokenizer(new String(buffer, "ASCII"));

        int ver = -1;
        try {
            version = st.nextToken();
            ver = Integer.parseInt(version);
        } catch (Exception e) {
            throw new IOException(SharedResources.getResources().getString(SharedResources.X_BAD_PORTMAPPER_VERSION, String.valueOf(version),
                    String.valueOf(PORTMAPPER_VERSION)), e);
        }
        if (ver != PORTMAPPER_VERSION) {
            throw new IOException(SharedResources.getResources().getString(SharedResources.X_BAD_PORTMAPPER_VERSION, String.valueOf(version),
                    String.valueOf(PORTMAPPER_VERSION)));
        }

        brokerInstance = st.nextToken();

        packetVersion = st.nextToken();

        // Read service name/port number value pairs
        while (true) {
            nBytes = readLine(in, buffer);

            if (nBytes <= 0 || (nBytes == 1 && buffer[0] == DOT_BYTE)) {
                break;
            }

            PortMapperEntry pme = PortMapperEntry.parse(new String(buffer, 0, nBytes, "ASCII"));
            this.add(pme);
        }
    }

    /**
     * Read an ASCII line from an input stream into a buffer. If the input line is longer than the buffer then the bytes at
     * the end of the line are lost
     *
     * @returns Number of bytes in buffer
     */
    private static int readLine(InputStream in, byte[] buffer) throws IOException {

        int n = 0;

        int b = in.read();
        while (b != -1 && b != NEWLINE_BYTE) {
            if (n < buffer.length) {
                buffer[n] = (byte) b;
                n++;
            }
            b = in.read();
        }

        if (DEBUG) {
            try {
                System.err.println(new String(buffer, 0, n, "ASCII"));
            } catch (UnsupportedEncodingException e) {
            }
        }

        if (n == 0 && b == -1) {
            return -1;
        }
        return n;

    }
}
