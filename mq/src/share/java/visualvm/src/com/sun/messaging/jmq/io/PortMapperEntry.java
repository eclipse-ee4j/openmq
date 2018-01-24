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

package com.sun.messaging.jmq.io;

import java.util.StringTokenizer;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

/**
 * Encapsulates information about a service. For use with the PortMapper
 */
public class PortMapperEntry {

    private int    port = 0;
    private String protocol = null;;
    private String type = null;;
    private String name = null;
    private HashMap properties = null;

    private static boolean DEBUG = false;

    public final static String NEWLINE = "\n";
    public final static String SPACE = " ";

    /**
     */
    public PortMapperEntry() {
    }


    public void addProperty(String name, String value)
    {
        synchronized (this) {
        if (properties == null)
             properties = new HashMap();
        }
        synchronized (properties) {
            properties.put(name, value);
        }
    }

    public void addProperties(HashMap props)
    {
        synchronized (this) {
        if (properties == null)
             properties = new HashMap();
        }
        synchronized (properties) {
            properties.putAll(props);
        }
    }

    /*
     * This method returns the value of a property specified in one
     * portmapper row/entry. For example, a portmapper entry can 
     * look like:
     *   rmi rmi JMX 0 [foo=bar, url=service:jmx:rmi://myhost/jndi/rmi://myhost:9999/server]
     *
     * This method allows  one to get the value of the 'url' property above.
     */
    public String getProperty(String name)
    {
        synchronized (this) {
            if (properties == null)
                return (null);
        }
        synchronized (properties) {
            return ((String)properties.get(name));
        }
    }

    /**
     * Set the service's port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return this.port;
    }

    /**
     * Set the service's protocol (i.e. "tcp" or "ssl")
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getProtocol() {
        return this.protocol;
    }

    /**
     * Set the service's type (ie "NORMAL", "ADMIN", etc)
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

    /**
     * Set the service's name
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /** 
     * Convert a PortMapperEntry into a string of the form that matches
     * that expected by parse(). For example
     *  jms tcp NORMAL 5951
     */
    public String toString() {
        String base =  name + SPACE + protocol + SPACE + type + SPACE + port;
        if (properties != null) {
            base += " [" ;
            synchronized(properties) {
                Set keyset = properties.keySet();
                Iterator itr = keyset.iterator();
                while (itr.hasNext()) {
                    String key = (String)itr.next();
                    String value = (String)properties.get(key);
                    base += (key + "=" + value);
                    if (itr.hasNext())
                        base += ",";
                }
            }
            base += "]";
        }
        return base;
    }

    /**
     * Parse a string into a PortMapperEntry. The format of the
     * string should be:
     *  <service name><SP><port><SP><protocol><SP><type><SP>[a=b, c=d]
     *
     * For example:
     *  jms tcp NORMAL 5951 [foo=bar, url=service:jmx:rmi://myhost/jndi/rmi://myhost:9999/server]
     *
     */
    static public PortMapperEntry parse(String s)
        throws IllegalArgumentException {

        PortMapperEntry pme = new PortMapperEntry();
        StringTokenizer st = new StringTokenizer(s);

        pme.name = st.nextToken();
        pme.protocol = st.nextToken();
        pme.type = st.nextToken();
        pme.port = Integer.parseInt(st.nextToken());

        //OK we want to read in properties
        int propIndx = s.indexOf("[");
        if (propIndx != -1) {
            int endPropIndx = s.indexOf("]");
            String sub = s.substring(propIndx+1, endPropIndx);
            StringTokenizer sst = new StringTokenizer(sub,",");
            while (sst.hasMoreTokens()) {
                String pair = sst.nextToken();
                int indx=pair.indexOf("=");
                if (indx == -1) {
                    continue;
                }
                String name= pair.substring(0,indx);
                String value= pair.substring(indx+1);
                pme.addProperty(name,value);
            }
        }   

        return pme;
    }
}
