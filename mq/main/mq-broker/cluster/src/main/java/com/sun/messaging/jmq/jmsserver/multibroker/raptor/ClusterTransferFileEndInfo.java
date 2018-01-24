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
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 */

public class ClusterTransferFileEndInfo 
{
    private static boolean DEBUG = false;

    private GPacket pkt = null;

    private String uuid = null;
    private String module = null;  
    private String brokerID = null;
    private String filename = null;
    private byte[] digest = null;
    private boolean moreFiles = false;

    private ClusterTransferFileEndInfo(String uuid, String module,
                                         String brokerID, String filename,
                                         byte[] digest, boolean morefiles) {
        this.uuid = uuid;
        this.brokerID = brokerID;
        this.module = module;
        this.filename = filename;
        this.digest = digest;
        this.moreFiles = morefiles;
    }

    private ClusterTransferFileEndInfo(GPacket pkt) {
        assert ( pkt.getType() == ProtocolGlobals.G_TRANSFER_FILE_END );
        this.pkt = pkt;
    }

    /**
     */
    public static ClusterTransferFileEndInfo newInstance(String uuid, String module,
                                                    String brokerID, String filename,
                                                    byte[] digest, boolean morefiles) {
        return new ClusterTransferFileEndInfo(uuid, module, brokerID,
                                                filename, digest, morefiles);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTransferFileEndInfo newInstance(GPacket pkt) {
        return new ClusterTransferFileEndInfo(pkt);
    }

    public GPacket getGPacket() throws BrokerException { 
        if (pkt != null) {
           return pkt;
        }

        GPacket gp = GPacket.getInstance();
        gp.putProp("uuid", uuid);
        gp.putProp("module", module);
        gp.putProp("brokerID", brokerID);
        gp.putProp("filename", filename);
        gp.putProp("morefiles", moreFiles);
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_END);
		gp.setPayload(ByteBuffer.wrap(digest));
        gp.setBit(gp.A_BIT, true);
        return gp;
    }

    public String getUUID() {
        assert ( pkt != null );
        return (String)pkt.getProp("uuid");
    }

    public String getModule() {
        assert ( pkt != null );
        return (String)pkt.getProp("module");
    }

    public String getBrokerID() {
        assert ( pkt != null );
        return (String)pkt.getProp("brokerID");
    }

    public String getFileName() {
        assert ( pkt != null );
        return (String)pkt.getProp("filename");
    }

    public boolean hasMoreFiles() {
        assert ( pkt != null );
        return ((Boolean)pkt.getProp("morefiles")).booleanValue();
    }

    public byte[] getDigest() {
        assert ( pkt != null );
        byte[] buf = null;
        if (pkt.getPayload() != null) {
            buf = pkt.getPayload().array();
        }
        return buf;
    }

    public static GPacket getReplyGPacket(int status, String reason) {
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_END_ACK);
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    public static GPacket getReplyAckGPacket(int status, String reason) {
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_END_ACK_ACK);
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    public static int getReplyStatus(GPacket gp) {
        return ((Integer)gp.getProp("S")).intValue();
    }

    public static String getReplyStatusReason(GPacket gp) {
        return (String)gp.getProp("reason");
    }

    public String toString() {
        if (pkt != null) {
            return "[brokerID="+getBrokerID()+", file="+getFileName()+"]"+getUUID()+"("+hasMoreFiles()+")";
        }
        return "[brokerID="+brokerID+", file="+filename+"]"+uuid+"("+moreFiles+")";
    }
}
