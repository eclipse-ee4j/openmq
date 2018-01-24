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
 * @(#)BrokerAddressImpl.java	1.40 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.LoopbackAddressException;
import com.sun.messaging.jmq.jmsserver.util.VerifyAddressException;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStoreUtil;


/**
 * This class implements the <code>BrokerAddress</code> for the
 * standard fully connected topology.
 */
public class BrokerAddressImpl extends BrokerAddress {

    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();

    // For compatibility with iMQ 2.0
    static final long serialVersionUID = 2088198635383118304L;

    private String hostName = null; //always IP address String 
    private String instName = null;

    private boolean HAEnabled = false;
    private String brokerID = null;

    private UID brokerSessionUID = null;
    private UID storeSessionUID = null;


    private int port = -1; // Portmapper port.

    private transient int clusterVersion = 0;

    public static final int VERSION = 100;

   
    public BrokerAddressImpl(BrokerMQAddress ba, String instName, boolean ha, String brokerID)
           throws Exception {
        this.hostName = ba.getHost().getHostAddress();
        this.port = ba.getPort();
        this.instName = (instName == null) ? "???":instName;
        this.HAEnabled = ha;
        if (ha) this.brokerID = brokerID; 
        this.brokerSessionUID = null;
        this.storeSessionUID = null;
        initialize(ba); 
        verifyAddress();
    }

    public BrokerAddressImpl(String hostName, String instName, int port, 
                             boolean ha, String brokerID,
                             UID brokerSession, UID storeSession)
        throws Exception {
        initialize(hostName, port); 

        this.hostName = getMQAddress().getHost().getHostAddress();
        this.instName = instName;
        this.port = port;
        this.HAEnabled = ha;
        this.brokerID = brokerID; 
        this.brokerSessionUID = brokerSession;
        this.storeSessionUID = storeSession;
        verifyAddress();
    }

    /**
     * Generate this broker's address.
     */
    public BrokerAddressImpl() throws Exception {
        this(Globals.getPortMapper().getHostname(),
             Globals.getConfigName(), 
             Globals.getPortMapper().getPort(),  
             Globals.getHAEnabled(),
             Globals.getBrokerID(),
             Globals.getClusterManager().getBrokerSessionUID(),
             Globals.getClusterManager().getStoreSessionUID());
    }


    public int getClusterVersion() {
        return clusterVersion;
    }

    public void setClusterVersion(int clusterVersion) {
        this.clusterVersion = clusterVersion;
    }

    /**
     * Perform simple sanity checks on a broker address received
     * from somewhere else. During one of the "test-o-thon" sessions
     * one of the linux system was sending a LINK_INIT packet
     * advertizing itself as "localhost". This method detects
     * such bogus addresses..
     */
    private void verifyAddress() throws Exception {
        if (getMQAddress().getHost().isLoopbackAddress()) {
            throw new LoopbackAddressException(Globals.getBrokerResources().getString(
                                   BrokerResources.X_LOOPBACKADDRESS, this.toString()));
        }

        if (Globals.getHAEnabled() != getHAEnabled()) {
            throw new VerifyAddressException(Globals.getBrokerResources().getString(
                            BrokerResources.X_ADDRESS_HAMODE_NOTMATCH, this.toString()));
        }

        if (getHAEnabled() && (brokerID == null))  {
            throw new VerifyAddressException(Globals.getBrokerResources().getString(
                            BrokerResources.X_ADDRESS_NO_BROKERID, this.toString()));
        }
    }

    public String getHostName() {
        return getMQAddress().getHost().getHostAddress();
    }

    public String getInstanceName() {
        return instName;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getHost() {
        return getMQAddress().getHost();
    }

    public boolean getHAEnabled() {
        return HAEnabled;
    }

    public String getBrokerID() {
        if (Globals.isBDBStore() && !Globals.getSFSHAEnabled()) {
            try {
                return MigratableStoreUtil.makeEffectiveBrokerID(instName, storeSessionUID);
            } catch (Exception e) {
                return null;
            }
        }
        return brokerID;
    }

    public UID getBrokerSessionUID() {
        return brokerSessionUID;
    }

    public UID getStoreSessionUID() {
        return storeSessionUID;
    }

    public void setStoreSessionUID(UID uid) {
        storeSessionUID = uid;
    }

    //only to be used with readBrokerAddress
    public final Object clone() {
        BrokerAddressImpl copy;
        try {
            copy = (BrokerAddressImpl) super.getObjectClone();
        } catch (CloneNotSupportedException e) {
            // Should never get this, but don't fail silently
            System.out.println("BrokerAddressImpl: Could not clone: " + e);
            return null;
        }

        return copy;
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof BrokerAddressImpl)) {
            return false;
        }

        BrokerAddressImpl addr = (BrokerAddressImpl) obj;

        if (getHAEnabled() != addr.getHAEnabled()) return false;

        if (getHAEnabled()) {
            if (this.brokerID == null || addr.getBrokerID() == null) return false;
            return this.brokerID.equals(addr.getBrokerID());
        }

        if (! this.instName.equals(addr.instName))
            return false;

        if (getMQAddress().getHost() == null ||
            addr.getMQAddress().getHost() == null)
            return false;

        return getMQAddress().getHost().equals(addr.getMQAddress().getHost());
    }

    public int hashCode() {

        return (31 * getMQAddress().getHost().hashCode()) + instName.hashCode();
    }

    /**
     * @return what's used for equals
     */
    public String toShortString() {
        getMQAddress().getHost().getHostName();

        StringBuffer buf = new StringBuffer();
        InetAddress addr = getMQAddress().getHost();
        buf.append((addr == null ? "null":addr.getHostAddress()));
        buf.append("?");
        if (getHAEnabled()) {
            buf.append("brokerID=");
            buf.append(getBrokerID());
            buf.append("&");
            buf.append("ha=true");
        } else {
            buf.append("instName="+getInstanceName());
            buf.append("&");
            buf.append("ha=false");
        }
        return buf.toString();
    }


   /**
     * Returns a cluster protocol formated string
     * XXX remaining work for HA!!!
     */
    public String toProtocolString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getMQAddress().toString());
        buf.append("?");
        buf.append("instName="+getInstanceName());
        if (getBrokerID() != null) {
            buf.append("&");
            buf.append("brokerID=");
            buf.append(getBrokerID());
        }
        buf.append("&");
        buf.append("brokerSessionUID=");
        buf.append(getBrokerSessionUID());
        buf.append("&");
        buf.append("ha="+getHAEnabled());
        if (getStoreSessionUID() != null) {
            buf.append("&");
            buf.append("storeSessionUID=");
            buf.append(getStoreSessionUID());
        }
        return buf.toString();
    }

    public BrokerAddress fromProtocolString(String s) throws Exception {
        BrokerMQAddress a = BrokerMQAddress.createAddress(s);
        String ha = (String)a.getProperty("ha");
        boolean isha = Boolean.valueOf(ha);
        return new BrokerAddressImpl(a.getHostName(), 
                     a.getProperty("instName"),
                     a.getPort(),
                     isha,
                     a.getProperty("brokerID"),
                     new UID(Long.parseLong(a.getProperty("brokerSessionUID"))),
                     (a.getProperty("storeSessionUID") == null ? null:
                      new UID(Long.parseLong(a.getProperty("storeSessionUID")))));
    }

    /**
     * The 2 methods of marshal/unmarshal BrokerAddressImpl in GPacket props is
     * to replace the old protocol write/read BrokerAddressImpl in GPacket body 
     */
    public void writeBrokerAddress(GPacket gp) {
        gp.putProp("HA", Boolean.valueOf(getHAEnabled()));
        if (brokerID != null) gp.putProp("brokerID", getBrokerID());
        if (brokerSessionUID != null) gp.putProp("brokerSession", getBrokerSessionUID().longValue());
        if (storeSessionUID != null) gp.putProp("storeSession", getStoreSessionUID().longValue());
        gp.putProp("instanceName", getInstanceName());
        gp.putProp("host", getHostName());
        gp.putProp("port", Integer.valueOf(getPort()));
    }

    public static BrokerAddressImpl readBrokerAddress(GPacket gp) throws Exception {
        if (gp.getProp("HA") == null) return null; //old protocol < 400
        boolean ha = ((Boolean)gp.getProp("HA")).booleanValue();  
        String brokerID = (String)gp.getProp("brokerID");
        String instName = (String)gp.getProp("instanceName"); 
        String host = (String)gp.getProp("host"); 
        int port = ((Integer)gp.getProp("port")).intValue(); 
        Long brokerSession = (Long)gp.getProp("brokerSession");
        Long storeSession = (Long)gp.getProp("storeSession");
        UID buid = null;  
        UID suid = null;  
        if (brokerSession != null) buid = new UID(brokerSession.longValue());
        if (storeSession != null) suid = new UID(storeSession.longValue());
        BrokerAddressImpl ba = new BrokerAddressImpl(host, instName, port, ha, brokerID, buid, suid);
        return ba;
    }

    public void writeBrokerAddress(DataOutputStream dos) throws IOException {
        dos.writeInt(VERSION);
        dos.writeUTF(hostName);
        dos.writeUTF(instName);
        dos.writeInt(port);
        dos.flush();
    }

    public void readBrokerAddress(DataInputStream dis)
        throws IOException {
        int version = dis.readInt();
        if (version > VERSION)
            throw new IOException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION, "BrokerAddress version mismatch."));
        hostName = dis.readUTF();
        instName = dis.readUTF();
        port = dis.readInt();
        initialize(hostName, port);
        HAEnabled = false;
        brokerID = null;
        brokerSessionUID = null;
        storeSessionUID = null;
        clusterVersion = 0;
    }

    private void readObject(java.io.ObjectInputStream in)
         throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        initialize(hostName, port); 

    }
 
    public String toString() {
        // Force a reverse name lookup so that
        // host.toString() output looks better.
        getMQAddress().getHost().getHostName();
        return toProtocolString();
    }
}
