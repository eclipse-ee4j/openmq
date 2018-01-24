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

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

/**
 * A general cluster G_INFO 
 */

public class ClusterInfoInfo 
{

    private ChangeRecordInfo lastStoredChangeRecord = null;

    private GPacket gp = null;  //out going
    private GPacket pkt = null; //in coming

    private ClusterInfoInfo() {
    }

    private ClusterInfoInfo(GPacket pkt) {
        this.pkt = pkt;
    }

    public static ClusterInfoInfo newInstance() {
        return new ClusterInfoInfo(); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterInfoInfo newInstance(GPacket pkt) {
        return new ClusterInfoInfo(pkt);
    }

    public void partitionAdded(UID partitionID) {
        if (gp == null) {
            gp = GPacket.getInstance();
            gp.setType(ProtocolGlobals.G_INFO);
        }
        Integer v = (Integer)gp.getProp("T");
        if (v == null) {
	    gp.putProp("T", Integer.valueOf(ClusterInfoRequestInfo.PARTITION_ADDED_TYPE));
        } else { 
            int t = (v.intValue() | ClusterInfoRequestInfo.PARTITION_ADDED_TYPE);
	    gp.putProp("T", Integer.valueOf(t));
        }
        gp.putProp(ClusterInfoRequestInfo.PARTITION_PROP,
                   Long.valueOf(partitionID.longValue()));
    } 

    public void storeSessionOwnerRequestReply(ClusterInfoRequestInfo cir, 
        int status, String reason, String owner) {

        if (gp == null) {
            gp = GPacket.getInstance();
            gp.setType(ProtocolGlobals.G_INFO);
	}
        Integer v = (Integer)gp.getProp("T");
        if (v == null) {
	    gp.putProp("T", Integer.valueOf(ClusterInfoRequestInfo.STORE_SESSION_OWNER_TYPE));
        } else { 
            int t = (v.intValue() | ClusterInfoRequestInfo.STORE_SESSION_OWNER_TYPE);
	    gp.putProp("T", Integer.valueOf(t));
        }
	long ss = cir.getStoreSession();
	gp.putProp(ClusterInfoRequestInfo.STORE_SESSION_PROP, Long.valueOf(ss));
	gp.putProp("X", cir.getXid());
	gp.putProp("S", Integer.valueOf(status));
	if (reason != null) {
            gp.putProp("reason", reason);
        }
        if (owner != null) {
            gp.putProp(ClusterInfoRequestInfo.STORE_SESSION_OWNER_PROP, owner);
        }
    }

    public Long getXid() {
        assert ( gp != null );
        return (Long)gp.getProp("X");
    }

    public void setBroadcast(boolean b) {
        assert ( gp != null );
        if (b) { 
            gp.setBit(gp.B_BIT, true); 
        }
    }

    public GPacket getGPacket() { 
        assert ( gp != null );
        gp.setBit(gp.A_BIT, false);
        return gp;
    }

    public static boolean isStoreSessionOwnerInfo(GPacket pkt) {
        Integer t = (Integer)pkt.getProp("T");
        if (t == null) {
            return false;
        }
        return (t.intValue() & ClusterInfoRequestInfo.STORE_SESSION_OWNER_TYPE)
                == ClusterInfoRequestInfo.STORE_SESSION_OWNER_TYPE;
    }

    public static boolean isPartitionAddedInfo(GPacket pkt) {
        Integer t = (Integer)pkt.getProp("T");
        if (t == null) {
            return false;
        }
        return (t.intValue() & ClusterInfoRequestInfo.PARTITION_ADDED_TYPE)
                == ClusterInfoRequestInfo.PARTITION_ADDED_TYPE;
    }

   public String getStoreSessionOwner() {
        if (pkt != null) {
            return (String)pkt.getProp(
                ClusterInfoRequestInfo.STORE_SESSION_OWNER_PROP);
        }
        return null;
    }

    public UID getPartition() {
        assert ( pkt != null );
        Long v = (Long)pkt.getProp(
                 ClusterInfoRequestInfo.PARTITION_PROP);
        if (v == null) {
            return null;
        }
        return new UID(v.longValue());
    }

    public String toString() {
        GPacket p = (pkt == null ? gp:pkt);
        if (p == null) {
            return "[]";
        }
        return "[infoType="+p.getProp("T")+", "+p.propsEntrySet()+"]";
    }
}
