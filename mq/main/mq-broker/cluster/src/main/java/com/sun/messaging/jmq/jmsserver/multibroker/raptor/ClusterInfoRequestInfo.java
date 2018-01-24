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

import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * A general cluster info request protocol 
 */

public class ClusterInfoRequestInfo 
{
    public static final int STORE_SESSION_OWNER_TYPE = 0x00000001; 
    public static final int PARTITION_ADDED_TYPE     = 0x00000002;

    //properties for STORE_SESSION_OWNER request
    public static final String STORE_SESSION_PROP  = "storeSession"; 
    public static final String STORE_SESSION_OWNER_PROP  = "storeSessionOwner"; 

    //properties for PARTITION_ADDED
    public static final String PARTITION_PROP  = "partition";

    private GPacket gp = null;  //out going
    private GPacket pkt = null; //in coming

    private Long xid = null;

    private ClusterInfoRequestInfo(Long xid) {
        this.xid = xid;
    }


    private ClusterInfoRequestInfo(GPacket pkt) {
        this.pkt = pkt;
    }

    public static ClusterInfoRequestInfo newInstance(Long xid) {
        return new ClusterInfoRequestInfo(xid); 
    }

    public void storeSessionOwnerRequest(long storeSession) 
    throws BrokerException { 
        if (gp == null) {
            gp = GPacket.getInstance();
            gp.setType(ProtocolGlobals.G_INFO_REQUEST);
            gp.putProp("X", xid);
        }
        Integer v = (Integer)gp.getProp("T");
	if (v == null) {
            gp.putProp("T", Integer.valueOf(STORE_SESSION_OWNER_TYPE));
	} else {
            if ((v.intValue() & STORE_SESSION_OWNER_TYPE) == 
                STORE_SESSION_OWNER_TYPE) {
                throw new BrokerException(
                "Internal Error: only 1 "+STORE_SESSION_OWNER_TYPE+
                " type info request is allowed");
                
            }
            int t = (v.intValue() | STORE_SESSION_OWNER_TYPE);
            gp.putProp("T", Integer.valueOf(t));
	}
        gp.putProp(STORE_SESSION_PROP, Long.valueOf(storeSession));
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterInfoRequestInfo newInstance(GPacket pkt) {
        return new ClusterInfoRequestInfo(pkt);
    }

    public GPacket getGPacket() { 
        return gp;
    }

    public ClusterInfoInfo getReply(int status, String reason, Object info) {
        assert ( pkt != null );
        ClusterInfoInfo cii = ClusterInfoInfo.newInstance();
        if (ClusterInfoInfo.isStoreSessionOwnerInfo(pkt)) {
            cii.storeSessionOwnerRequestReply(this, status, reason, (String)info);
        }
        return cii;
    }

    public long getStoreSession() {
        assert ( pkt != null );
        return ((Long)pkt.getProp(STORE_SESSION_PROP)).longValue();
    }

    public Long getXid() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("X");
    }

    public String toString() {
        GPacket p = (pkt == null ? gp:pkt);
        if (p == null) {
            return "[]";
        }
        return "[requestType="+p.getProp("T")+", "+p.propsEntrySet()+"]";
    }
}
