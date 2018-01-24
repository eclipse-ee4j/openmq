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
 * @(#)MQAddress.java	1.7 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * This class represents broker address URL.
 */
public class MQAddress implements Serializable 
{
    static final long serialVersionUID = -8430608988259524061L;

    public static final String isHostTrusted = "isHostTrusted";
    public static final String SCHEME_NAME_MQWS = "mqws";
    public static final String SCHEME_NAME_MQWSS = "mqwss";
    public static final String DEFAULT_WS_SERVICE = "wsjms";
    public static final String DEFAULT_WSS_SERVICE = "wssjms";

    protected static final String DEFAULT_SCHEME_NAME = "mq";
    protected static final String DEFAULT_HOSTNAME = "localhost";
    protected static final int DEFAULT_PORTMAPPER_PORT = 7676;
    protected static final String DEFAULT_SERVICENAME = "jms";

    private String addr = null;

    protected String schemeName = null;
    protected String addrHost = null;
    protected int port = -1;
    protected String serviceName = null;
    protected boolean isHTTP = false;
    protected boolean isWebSocket = false;
    protected Properties props = new Properties();

    protected transient String tostring = null;

    //This flag is used to indicate if 'isHostTrusted' prop is set in
    //the imqAddressList.  If set, it over rides 'imqSSLIsHostTrusted'
    //property.  If not set, 'imqSSLIsHostTrusted' value is used.
    protected transient boolean isSSLHostTrustedSet = false;


    protected MQAddress() {}

    protected void initialize(String addr) 
        throws MalformedURLException
    {
        this.addr = addr;
        this.init();
        this.parseAndValidate();
    }

    protected void initialize(String host, int port) 
        throws MalformedURLException
    {
        if (port < 0) {
            throw new MalformedURLException("Illegal port :"+port);
        }
        if (host == null || host.trim().length() == 0) {
            this.addr = ":"+port;
        } else {
            URL u = new URL("http", host, port, "");
            this.addr = u.getHost()+":"+port;
        }
        this.init();
        this.parseAndValidate();
    }

    private void init() {
        // Set the default value for isHostTrusted attribute.
        props.setProperty(isHostTrusted, "false"); //TCR #3 default to false
    }

    protected void parseAndValidate() throws MalformedURLException {
        //String tmp = new String(addr);
        String tmp = addr;

        // Find scheme name.
        // Possible values : mq, mqtcp, mqssl, http, https
        schemeName = DEFAULT_SCHEME_NAME;
        int i = tmp.indexOf("://");
        if (i > 0) {
            schemeName = tmp.substring(0, i);
            tmp = tmp.substring(i + 3);
        }

        if (schemeName.equalsIgnoreCase("mq") ||
            schemeName.equalsIgnoreCase("mq+ssl")) {

            /*
             * Typical example -
             * mq://jpgserv:7676/ssljms?isHostTrusted=true
             */
            i = tmp.indexOf('?');
            if (i >= 0) {
                String qs = tmp.substring(i+1);
                parseQueryString(qs);
                tmp = tmp.substring(0, i);
            }
            i = tmp.indexOf('/');
            if (i >= 0) {
                serviceName = tmp.substring(i+1);
                tmp = tmp.substring(0, i);
            }

            parseHostPort(tmp);

            if (serviceName == null || serviceName.equals(""))
                serviceName = getDefaultServiceName();
        }
        else if (schemeName.equalsIgnoreCase("mqssl") ||
                 schemeName.equalsIgnoreCase("mqtcp") ||
                 schemeName.equalsIgnoreCase(SCHEME_NAME_MQWS) ||
                 schemeName.equalsIgnoreCase(SCHEME_NAME_MQWSS)) {
           if (schemeName.equalsIgnoreCase(SCHEME_NAME_MQWS) ||
               schemeName.equalsIgnoreCase(SCHEME_NAME_MQWSS)) {
               isWebSocket = true;
           }
            /*
             * Typical example -
             * mqtcp://jpgserv:12345/jms
             * mqssl://jpgserv:23456/ssladmin
             */
            i = tmp.indexOf('?');
            if (i >= 0) {
                String qs = tmp.substring(i+1);
                parseQueryString(qs);
                tmp = tmp.substring(0, i);
            }

            i = tmp.indexOf('/');
            if (i >= 0) {
                serviceName = tmp.substring(i+1);
                tmp = tmp.substring(0, i);
            }
            parseHostPort(tmp);
        }
        else if (schemeName.equalsIgnoreCase("http") ||
            schemeName.equalsIgnoreCase("https")) {
            isHTTP = true;
            return;
        }
        else {
            throw new MalformedURLException(
                "Illegal address. Unknown address scheme : " + addr);
        }
    }

    protected void parseHostPort(String tmp) throws MalformedURLException {
         
        int i = tmp.indexOf(':');
        if (i != -1 && i == tmp.lastIndexOf(':')) {
            String half1 = tmp.substring(0, i).trim();
            String half2 = tmp.substring(i+1).trim();
            if (half1.length() == 0 || half2.length() == 0) {
                if (half1.length() == 0) {
                    addrHost = DEFAULT_HOSTNAME;
                } else {
                    addrHost = half1; 
                }
                if (half2.length() == 0) {
                    port = DEFAULT_PORTMAPPER_PORT;
                } else {
                    port = Integer.parseInt(half2);
                    if (port < 0) {
                        throw new MalformedURLException("Illegal port in :"+tmp);
                    }

                }
                return;
            }
        }

        URL hp = new URL("http://"+tmp);
        port = hp.getPort();
        if (port == -1) {
            port = DEFAULT_PORTMAPPER_PORT;
        }
        addrHost = hp.getHost();
        if (addrHost == null || addrHost.equals("")) {
            addrHost = DEFAULT_HOSTNAME;
        }
    }

    protected void parseQueryString(String qs) throws MalformedURLException {
        //String tmp = new String(qs);
        String tmp = qs;

        while (tmp.length() > 0) {
            String pair = tmp;

            int i = tmp.indexOf('&');
            if (i >= 0) {
                pair = tmp.substring(0, i);
                tmp = tmp.substring(i+1);
            }
            else {
                tmp = "";
            }

            int n = pair.indexOf('=');
            if (n <= 0)
                throw new MalformedURLException(
                    "Illegal address. Bad query string : " + addr);

            String name = pair.substring(0, n);
            String value = pair.substring(n+1);
            props.setProperty(name, value);

            if ( isHostTrusted.equals(name) ) {
                isSSLHostTrustedSet = true;
            }
        }
    }

    public boolean isServicePortFinal() {
        return (isHTTP || isWebSocket ||
                schemeName.equalsIgnoreCase("mqtcp") ||
                schemeName.equalsIgnoreCase("mqssl"));
    }

    public String getProperty(String pname) {
        return props.getProperty(pname);
    }

    public boolean getIsSSLHostTrustedSet() {
        return this.isSSLHostTrustedSet;
    }



    public String getSchemeName() {
        return schemeName;
    }

    public boolean isSSLPortMapperScheme() {
        return schemeName.equalsIgnoreCase("mq+ssl");
    }

    public String getHostName() {
        return addrHost;
    }

    public int getPort() {
        return port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean getIsHTTP() {
        return isHTTP;
    }

    public String getURL() {
        return addr;
    }

    public String toString() {

        if (tostring != null)
            return tostring;

        if (isHTTP || isWebSocket) {
            tostring = addr;
            return addr;
        }

        tostring = schemeName + "://" + addrHost + ":" + port + "/" + serviceName;
        return tostring;
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if(obj.getClass() != this.getClass()) {
           return false;
        }
        return this.toString().equals(((MQAddress)obj).toString());
    }

    public String getDefaultServiceName()  {
	return (DEFAULT_SERVICENAME);
    }


     /**
     * Parses the given MQ Message Service Address and creates an
     * MQAddress object.
     */
    public static MQAddress getMQAddress(String addr) 
        throws MalformedURLException {
        MQAddress ret = new MQAddress();
        ret.initialize(addr);
        return ret;
    }

    public static MQAddress getMQAddress(String host, int port) 
        throws MalformedURLException {
        MQAddress ret = new MQAddress();
        ret.initialize(host, port);
        return ret;
    }
       

}
