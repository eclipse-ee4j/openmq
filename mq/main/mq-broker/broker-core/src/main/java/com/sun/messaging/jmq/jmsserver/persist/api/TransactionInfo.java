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
 * @(#)TransactionInfo.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Arrays;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * TransactionInfo keeps track of a txn and it's state.
 * Has methods to parse and persist them.
 */
public class TransactionInfo implements Cloneable, Externalizable {

    static final long serialVersionUID = 5642215309770752611L;

    private static boolean DEBUG = false;

    public static final int TXN_NOFLAG = 0;
    public static final int TXN_LOCAL = 1;
    public static final int TXN_CLUSTER = 2;
    public static final int TXN_REMOTE = 3;

    private int type = TXN_NOFLAG;

    private TransactionState state = null;

    private TransactionBroker[] txnBkrs = null;
    private BrokerAddress txnHomeBroker = null;

    // Maps BrokerAddress -> TransactionBroker
    private transient HashMap bkrMap = new HashMap();

    public TransactionInfo() {
        // Don't use... required for Externalizable interface
    }

    public TransactionInfo(TransactionInfo tif) {

        type = tif.getType();
        state = new TransactionState(tif.getTransactionState());
        txnHomeBroker = tif.getTransactionHomeBroker();
        setTransactionBrokers(tif.getTransactionBrokers()); 
    }

    public TransactionInfo(TransactionState ts) {
        this(ts, null, null, TXN_LOCAL);
    }

    public TransactionInfo(TransactionState ts, BrokerAddress homeBroker,
        TransactionBroker[] bkrs, int txnType) {

        if (ts == null) {
            throw new IllegalArgumentException("Null TransactionState specified");
        }

        type = txnType;
        state = ts;
        txnHomeBroker = homeBroker;

        setTransactionBrokers(bkrs);
    }

    public boolean isLocal() {
        return (type == TXN_LOCAL);
    }

    public boolean isRemote() {
        return (type == TXN_REMOTE);
    }

    public boolean isCluster() {
        return ( type == TXN_CLUSTER);
    }

    public void setType(int txnType) {
        type = txnType;
    }

    public int getType() {
        return type;
    }

    public TransactionState getTransactionState() {
        return state;
    }

    public int getTransactionStateValue() {
        return state.getState();
    }

    public void setTransactionBrokers(TransactionBroker[] bkrs) {

        if (!bkrMap.isEmpty()) {
            bkrMap.clear();
        }

        if (bkrs == null) {
            txnBkrs = null;
        } else {
            // TransactionBroker is mutable, so we must store a copy
            int size = bkrs.length;
            txnBkrs = new TransactionBroker[size];
            StringBuffer debugBuf = null;
            for (int i = 0; i < size; i++) {
                TransactionBroker txnBkr = (TransactionBroker)bkrs[i].clone();
                txnBkrs[i] = txnBkr;
                if (DEBUG) {
                    if (debugBuf == null) debugBuf = new StringBuffer(); 
                    if (i > 0) debugBuf.append(",");
                    debugBuf.append(txnBkr);
                }

                // Create a lookup map (BrokerAddress -> TransactionBroker)
                bkrMap.put(txnBkr.getBrokerAddress(), txnBkr);
            }

            // Verify that there are no duplicate
            if (bkrMap.size() != size) {
                String emsg = "Unexpected Error: duplicate TransactionBroker object found "+
                               size+" size in database, mapped to size "+bkrMap.size()+ 
                               (debugBuf == null ? "":", bkrs="+debugBuf.toString()+", bkrMap="+bkrMap);
                throw new IllegalArgumentException(emsg);
            }
        }
    }

    public TransactionBroker[] getTransactionBrokers() {
        return txnBkrs;
    }

    public BrokerAddress getTransactionHomeBroker() {
        return txnHomeBroker;
    }

    public void updateTransactionState(int ts) throws BrokerException {
        state.setState(ts);
    }

    public void updateBrokerState(TransactionBroker bkr)
        throws BrokerException {

        TransactionBroker txnBkr =
            (TransactionBroker)bkrMap.get(bkr.getBrokerAddress());
        if (txnBkr == null) {
            throw new BrokerException("TransactionBroker " + bkr +
                " could not be found in the store", Status.NOT_FOUND);
        }

        // Just update the state
        txnBkr.copyState(bkr);
    }

    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

        type = in.readInt();
        state = (TransactionState)in.readObject();
        txnHomeBroker = (BrokerAddress)in.readObject();
        txnBkrs = (TransactionBroker[])in.readObject();
        bkrMap = new HashMap();
  
        
        // populate bkrMap
        // fix for CR 6858156
        if (txnBkrs != null) {
			for (int i = 0; i < txnBkrs.length; i++) {
				TransactionBroker txnBkr = txnBkrs[i];
				bkrMap.put(txnBkr.getBrokerAddress(), txnBkr);
			}
			// Verify that there are no duplicate
			if (bkrMap.size() != txnBkrs.length) {
				Globals.getLogger().log(Logger.WARNING,
						"Internal Error: duplicate TransactionBroker object found");
			}
		}
		
    }

    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        bkrMap = new HashMap();
    }


    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(type);
        out.writeObject(state);
        out.writeObject(txnHomeBroker);
        out.writeObject(txnBkrs);
    }

    public Object clone() {
        try {
            // Make a shallow copy first
            TransactionInfo newTxnInfo = (TransactionInfo)super.clone();

            // Now do deep copy of mutable objects
            if (state != null) {
                newTxnInfo.state = new TransactionState(state);
            }

            if (txnBkrs != null) {
                newTxnInfo.txnBkrs = (TransactionBroker[])txnBkrs.clone();
            }

            return newTxnInfo;
        } catch (CloneNotSupportedException e) {
            throw new Error ("This should never happen!");
        }
    }

    public String toString() {
        return(
            new StringBuffer(128)
                .append( "TransactionInfo[type=" ).append( toString(type) )
                .append( ", state=" ).append( state )
                .append( ", home broker=" ).append( txnHomeBroker )
                .append( ", brokers=" ).append( Arrays.toString(txnBkrs) )
                .append( "]" )
                .toString());
    }

    public static String toString(int type) {
        switch (type) {
            case TXN_NOFLAG:
                return "TXN_NOFLAG";

            case TXN_LOCAL:
                return "TXN_LOCAL";

            case TXN_CLUSTER:
                return "TXN_CLUSTER";

             case TXN_REMOTE:
                return "TXN_REMOTE";

            default:
               return "INVALID TYPE " + type;
        }
    }
}

