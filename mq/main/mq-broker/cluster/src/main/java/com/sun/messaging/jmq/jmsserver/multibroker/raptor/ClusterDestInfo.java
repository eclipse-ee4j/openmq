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
 * @(#)ClusterDestInfo.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.PacketProperties;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 * An instance of this class is intended to be used one direction only
 * either Destination -> GPacket or GPacket -> Destination (see assertions)
 */

public class ClusterDestInfo 
{
    private Destination d = null;

    private GPacket pkt = null;
    private String destName = null;
    private int destType = -1;
    private ChangeRecordInfo shareccInfo = null;

    private ClusterDestInfo(Destination d) {
        this.d = d;
    }

    private ClusterDestInfo(GPacket pkt) {
        assert (pkt.getType() == ProtocolGlobals.G_REM_DESTINATION || 
                pkt.getType() == ProtocolGlobals.G_UPDATE_DESTINATION);

        this.pkt = pkt;
        destName = (String) pkt.getProp("N");
        destType = ((Integer) pkt.getProp("DT")).intValue();
        if (pkt.getProp("shareccSeq") != null) {
            shareccInfo =  new ChangeRecordInfo();
            shareccInfo.setSeq((Long)pkt.getProp("shareccSeq"));
            shareccInfo.setUUID((String)pkt.getProp("shareccUUID"));
            shareccInfo.setResetUUID((String)pkt.getProp("shareccResetUUID"));
            shareccInfo.setType(pkt.getType());
        }
    }

    /**
     * Destination to GPacket
     *
     * @param d The Destination to be marshaled to GPacket
     */
    public static ClusterDestInfo newInstance(Destination d) {
        return new ClusterDestInfo(d);
    }

    /**
     * GPacket to Destination 
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterDestInfo newInstance(GPacket pkt) {
        return new ClusterDestInfo(pkt);
    }

    public GPacket getGPacket(short protocol, boolean changeRecord) { 
        assert (d !=  null);
        assert (protocol == ProtocolGlobals.G_REM_DESTINATION || 
                protocol == ProtocolGlobals.G_UPDATE_DESTINATION);
        GPacket gp = GPacket.getInstance();
        gp.setType(protocol);
        gp.putProp("N", d.getDestinationName());
        gp.putProp("DT", Integer.valueOf(d.getType()));

        switch (protocol) {
           case ProtocolGlobals.G_REM_DESTINATION:
           ChangeRecordInfo cri = d.getCurrentChangeRecordInfo(
                                  ProtocolGlobals.G_REM_DESTINATION);
           if (cri != null) {
               gp.putProp("shareccSeq", cri.getSeq());
               gp.putProp("shareccUUID", cri.getUUID());
               gp.putProp("shareccResetUUID", cri.getResetUUID());
           }
           
           break;

           case ProtocolGlobals.G_UPDATE_DESTINATION:

           cri = d.getCurrentChangeRecordInfo(
                      ProtocolGlobals.G_UPDATE_DESTINATION);
           if (cri != null) {
               gp.putProp("shareccSeq", cri.getSeq());
               gp.putProp("shareccUUID", cri.getUUID());
               gp.putProp("shareccResetUUID", cri.getResetUUID());
           }

           if (DestType.isTemporary(d.getType())) {
               ConnectionUID cuid = d.getConnectionUID();
               if (cuid != null) {
                   gp.putProp("connectionUID", Long.valueOf(cuid.longValue()));
               }
           }

           HashMap props = d.getDestinationProperties();
           if (props == null) props = new HashMap();
           ByteArrayOutputStream bos = new ByteArrayOutputStream();
           try {
               PacketProperties.write(props, bos);
               bos.flush();
           }
           catch (IOException e) { /* Ignore */ }

           byte[] buf = bos.toByteArray();
           gp.setPayload(ByteBuffer.wrap(buf));
           break;

        }
        if (changeRecord) gp.putProp("M", Boolean.valueOf(true));

        return gp;
    }

    public DestinationUID getDestUID() throws BrokerException {
        assert (destName != null);
        return DestinationUID.getUID(destName, DestType.isQueue(destType));
    }

    public int getDestType() {
        assert (pkt != null);
        return destType;
    }

    public String getDestName() {
        assert (pkt != null);
        return destName;
    }

    public ChangeRecordInfo getShareccInfo() {
        return shareccInfo;
    }

    public Hashtable getDestProps() throws IOException, ClassNotFoundException {
        assert (pkt != null); 
        ByteArrayInputStream bis = new ByteArrayInputStream(pkt.getPayload().array());
        return PacketProperties.parseProperties(bis);
    }

}
