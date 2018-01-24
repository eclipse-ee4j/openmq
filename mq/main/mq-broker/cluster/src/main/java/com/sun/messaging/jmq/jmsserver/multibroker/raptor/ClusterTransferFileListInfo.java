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

public class ClusterTransferFileListInfo 
{
    private static boolean DEBUG = false;

    private GPacket pkt = null;

    private String uuid = null;
    private String brokerID = null;
    private Integer numfiles = null;
    private String module = null;

    private ClusterTransferFileListInfo(String uuid, String brokerID,
                                        Integer numfiles, String module) {
        this.uuid = uuid;
        this.brokerID = brokerID;
        this.numfiles = numfiles;
        this.module = module;
    }

    private ClusterTransferFileListInfo(GPacket pkt) {
        assert ( pkt.getType() == ProtocolGlobals.G_TRANSFER_FILE_LIST );
        this.pkt = pkt;
    }

    /**
     */
    public static ClusterTransferFileListInfo newInstance(String uuid, String brokerID,
                                                          Integer numfiles, String module) {
        return new ClusterTransferFileListInfo(uuid, brokerID, numfiles, module);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTransferFileListInfo newInstance(GPacket pkt) {
        return new ClusterTransferFileListInfo(pkt);
    }

    public GPacket getGPacket() throws BrokerException { 
        if (pkt != null) {
           return pkt;
        }

        GPacket gp = GPacket.getInstance();
        gp.putProp("uuid", uuid);
        gp.putProp("brokerID", brokerID);
        gp.putProp("numfiles", numfiles);
        gp.putProp("module", module);
        gp.setType(ProtocolGlobals.G_TRANSFER_FILE_LIST);
        gp.setBit(gp.A_BIT, false);
        return gp;
    }

    public String getUUID() {
        assert ( pkt != null );
        return (String)pkt.getProp("uuid");
    }

    public String getBrokerID() {
        assert ( pkt != null );
        return (String)pkt.getProp("brokerID");
    }

    public int getNumFiles() {
        assert ( pkt != null );
        return ((Integer)pkt.getProp("numfiles")).intValue();
    }

    public String getModule() {
        assert ( pkt != null );
        return (String)pkt.getProp("module");
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean verbose) {
        if (pkt != null) {
            return "[brokerID="+getBrokerID()+", numfiles="+getNumFiles()+"]"+
                    getUUID()+(verbose ? "("+getModule()+")":"");
        }
        return "[brokerID="+brokerID+", numfiles="+numfiles+"]"+uuid+(verbose ? "("+module+")":"");
    }

}
