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
 * @(#)ClusterTxnInquiryInfo.java	1.4 06/28/07
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
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterTxnInquiryInfo 
{
    protected Logger logger = Globals.getLogger();

    private Long transactionID = null;
    private BrokerAddress txnhome = null;
    private Long replyXid = null;

    private GPacket pkt = null;

    private ClusterTxnInquiryInfo(Long txnID, BrokerAddress txnhome, Long replyXid) {
        this.transactionID = txnID;
        this.txnhome = txnhome;
        this.replyXid = replyXid;
    }

    private ClusterTxnInquiryInfo(GPacket pkt) {
        this.pkt = pkt;
    }

    public static ClusterTxnInquiryInfo newInstance(Long txnID, BrokerAddress txnhome, Long replyXid) {
        return new ClusterTxnInquiryInfo(txnID, txnhome, null); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTxnInquiryInfo newInstance(GPacket pkt) {
        return new ClusterTxnInquiryInfo(pkt);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSACTION_INQUIRY);
        gp.putProp("transactionID", transactionID);
        gp.setBit(gp.A_BIT, true);
        if (replyXid != null) gp.putProp("X", replyXid);
        if (txnhome != null) gp.putProp("transactionHome", txnhome.toProtocolString());

        return gp;
    }

    public Long getTransactionID() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("transactionID");
    }

    public BrokerAddress getTransactionHome() {
        assert ( pkt != null );
        String home = (String)pkt.getProp("transactionHome");
        if (home == null) return null;
        try {
        return Globals.getMyAddress().fromProtocolString(home);
        } catch (Exception e) {
        Globals.getLogger().log(Globals.getLogger().WARNING,  
        "Unable to get transaction home broker address for TID="+getTransactionID()+":"+e.getMessage());
        }
        return null;
    }

    public Long getXid() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("X");
    }

   /**
    * To be called by sender
    */
    public String toString() {

        if (pkt == null) {
        StringBuffer buf = new StringBuffer();

        buf.append("\n\tTransactionID = ").append(transactionID);

        if (txnhome != null) {
           buf.append("\n\tTransactionHome = ").append(txnhome.toProtocolString());
        }

        if (replyXid != null) {
           buf.append("\n\tXID = ").append(replyXid);
        }

        return buf.toString();
        }

        StringBuffer buf = new StringBuffer();

        if (getTransactionID() != null) {
            buf.append("\n\tTransactionID = ").append(getTransactionID());
        }
        if (pkt.getProp("transactionHome") != null) {
            buf.append("\n\tTransactionHome = ").append((String)pkt.getProp("transactionHome"));
        }

        if (pkt.getProp("X") != null) {
            buf.append("\n\tXID = ").append(pkt.getProp("X"));
        }

        return buf.toString();
    }

}
