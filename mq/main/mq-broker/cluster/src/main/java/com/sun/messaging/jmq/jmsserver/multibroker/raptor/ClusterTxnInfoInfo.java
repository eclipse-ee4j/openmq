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
 * @(#)ClusterTxnInfoInfo.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterTxnInfoInfo 
{
    protected Logger logger = Globals.getLogger();

    private Long transactionID = null;
    private int transactionState;
    private BrokerAddress[] brokers;
    private BrokerAddress[] waitfor;
    private BrokerAddress txnHome;
    private UID msgStoreSession = null;
    private boolean owner = false;
    private Cluster c = null;
    private Long xid = null;

    private GPacket pkt = null;

    private ClusterTxnInfoInfo(Long txnID, int txnState,
                               BrokerAddress[] brokers, 
                               BrokerAddress[] waitfor, 
                               BrokerAddress txnHome, 
                               boolean owner, UID msgStoreSession,
                               Cluster c, Long xid ) {
        this.transactionID = txnID;
        this.transactionState = txnState;
        this.brokers = brokers;
        this.waitfor = waitfor;
        this.txnHome = txnHome;
        this.msgStoreSession = msgStoreSession;
        this.owner = owner;
        this.c = c;
        this.xid = xid;
    }

    private ClusterTxnInfoInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterTxnInfoInfo newInstance(Long txnID, int txnState,
                                            BrokerAddress[] brokers,
                                            BrokerAddress[] waitfor,
                                            BrokerAddress txnHome, boolean owner,
                                            UID msgStoreSession, Cluster c, Long xid) {
        return new ClusterTxnInfoInfo(txnID, txnState, brokers, 
                                      waitfor, txnHome, owner, msgStoreSession, c, xid); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTxnInfoInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterTxnInfoInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSACTION_INFO);
        gp.putProp("transactionID", transactionID);
        gp.putProp("transactionState", Integer.valueOf(transactionState));
        if (owner) {
            gp.putProp("owner", Boolean.valueOf(true));
            c.marshalBrokerAddress(c.getSelfAddress(), gp); 
        }
        if (msgStoreSession != null) {
            gp.putProp("messageStoreSession", 
                Long.valueOf(msgStoreSession.longValue()));
        }
        if (brokers != null) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) buf.append(",");
                buf.append(brokers[i].toProtocolString());
            }
            gp.putProp("brokers", buf.toString());
        }
        if (waitfor != null) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < waitfor.length; i++) {
                if (i > 0) buf.append(",");
                buf.append(waitfor[i].toProtocolString());
            }
            gp.putProp("waitfor", buf.toString());
        }
        if (txnHome != null) { 
            gp.putProp("transactionHome", txnHome.toProtocolString());
        }
        if (xid != null) gp.putProp("X", xid);
        gp.setBit(gp.A_BIT, false);

        return gp;
    }

    public boolean isOwner() {
        assert ( pkt != null );
        Boolean b = (Boolean)pkt.getProp("owner");
        if (b == null) return false;
        return b.booleanValue();
    }

    public BrokerAddress getOwnerBrokerAddress() throws Exception {
        assert ( pkt != null );
        if (!isOwner()) return null;
        return c.unmarshalBrokerAddress(pkt);
    }

    public int getTransactionState() {
        assert ( pkt != null );
        return ((Integer) pkt.getProp("transactionState")).intValue();
    }

    public Long getTransactionID() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("transactionID");
    }

    public UID getMessageStoreSessionUID() {
        assert ( pkt != null );
        Long ssid = (Long)pkt.getProp("messageStoreSession");
        if (ssid == null) {
            return null;
        }
        return new UID(ssid.longValue());
    }

    public List getWaitfor() throws Exception {
        assert ( pkt != null );
        String w = (String)pkt.getProp("waitfor");
        if (w == null) return null;
        StringTokenizer tokens = new StringTokenizer(w, ",", false); 
        List bas = new ArrayList();
        String b = null;
        while (tokens.hasMoreElements()) {
            b=(String)tokens.nextElement();
            bas.add(Globals.getMyAddress().fromProtocolString(b));
        }
        return bas;
    }

    public boolean isWaitedfor(BrokerAddress me) throws Exception {
        List waitfor = getWaitfor();
        if (waitfor == null) return false;
        BrokerAddress b = null;
        TransactionBroker tb = null;
        Iterator itr = waitfor.iterator(); 
        while (itr.hasNext()) {
            tb = new TransactionBroker((BrokerAddress)itr.next());
            if (tb.getCurrentBrokerAddress().equals(me)) {
                return true;
            }
        }
        return false;
    }

    public BrokerAddress[] getBrokers() throws Exception {
        assert ( pkt != null );
        String p = (String)pkt.getProp("brokers");
        if (p == null) return null;
        StringTokenizer tokens = new StringTokenizer(p, ",", false); 
        List bas = new ArrayList();
        String b = null;
        while (tokens.hasMoreElements()) {
            b=(String)tokens.nextElement();
            bas.add(Globals.getMyAddress().fromProtocolString(b));
        }
        return (BrokerAddress[])bas.toArray(new BrokerAddress[bas.size()]);
    }

    public BrokerAddress getTransactionHome() {
        assert ( pkt != null );
        String b = (String)pkt.getProp("transactionHome");
        if (b == null) return null;
        try {
	        return Globals.getMyAddress().fromProtocolString(b);
	    } catch (Exception e) {
	        Globals.getLogger().log(Globals.getLogger().WARNING,
	        "Unable to get transaction home broker address for TID="+getTransactionID()+":"+e.getMessage());
	    }

        return null;
    }

    /**
     * To be called by sender
     */
    public String toString() {

        if (pkt == null) {
        StringBuffer buf = new StringBuffer();

        buf.append("\n\tTransactionID = ").append(transactionID);
        buf.append("\n\tTransactionState = ").append(TransactionState.toString(transactionState));

        if (txnHome != null) {
            buf.append("\n\tTransactionHome = ").append(txnHome);
        }
        if (brokers != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(brokers[i].toProtocolString());
            }
            buf.append("\n\tBrokers = ").append(bf.toString());
        }
        if (waitfor != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < waitfor.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(waitfor[i].toProtocolString());
            }
            buf.append("\n\tWaitfor = ").append(bf.toString());
        }
        if (xid != null) {
           buf.append("\n\tXID = ").append(xid);
        }

        buf.append("\n");
        return buf.toString();

        } //pkt == null

        StringBuffer buf = new StringBuffer();
        buf.append("\n\tTransactionID = ").append(getTransactionID());
        buf.append("\n\tTransactionState = ").append(TransactionState.toString(getTransactionState()));

        try {
        BrokerAddress b = getTransactionHome(); 
        if (b != null) buf.append("\n\tTransactionHome = ").append(b);
        } catch (Exception e) {
        buf.append("\n\tTransactionHome = ERROR:").append(e.toString());
        }

        BrokerAddress[] bas = null;
        try {
        bas = getBrokers(); 
        if (bas != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < bas.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(bas[i].toProtocolString());
            }
            buf.append("\n\tBrokers = ").append(bf.toString());
        }
        } catch (Exception e) {
        buf.append("\n\tBrokers = ERROR:").append(e.toString());
        }

        try {
        List wbas = getWaitfor(); 
        if (wbas != null) {
            Iterator itr = wbas.iterator();
            StringBuffer bf = new StringBuffer();
            int i = 0;
            while (itr.hasNext()) {
                if (i > 0) bf.append(",");
                bf.append(((BrokerAddress)itr.next()).toProtocolString());
                i++;
            }
            buf.append("\n\tWaitfor = ").append(bf.toString());
        }
        } catch (Exception e) {
        buf.append("\n\tWaitfor = ERROR:").append(e.toString());
        }

        if (pkt.getProp("X") != null) {
           buf.append("\n\tXID = ").append(pkt.getProp("X"));
        }

        buf.append("\n");

        return buf.toString();
    }
}
