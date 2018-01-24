/*
 * Copyright (c) 2001, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.data.migration.thrasher2;

import java.io.*;
import java.util.Hashtable;
import javax.transaction.xa.XAResource;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;


/**
 * The state of a transaction, plus additional information that may
 * useful to know about the transaction.
 *
 * Object format from 3.7 ur2 filestore, use for migration purpose only.
 * @see com.sun.messaging.jmq.jmsserver.data.TransactionState
 */
public class TransactionState implements Externalizable {

    static final long serialVersionUID = 4438769866522991889L;

    public static final int CREATED    = 0;
    public static final int STARTED    = 1;
    public static final int FAILED     = 2;
    public static final int INCOMPLETE = 3;
    public static final int COMPLETE   = 4;
    public static final int PREPARED   = 5;
    public static final int COMMITTED  = 6;
    public static final int ROLLEDBACK = 7;
    public static final int LAST = 7;

    private static final String names[] = {
        "CREATED",
        "STARTED",
        "FAILED",
        "INCOMPLETE",
        "COMPLETE",
        "PREPARED",
        "COMMITED",
        "ROLLEDBACK"
        };

    private JMQXid  xid = null;

    // State of the transaction
    private int state = CREATED;

    // User that created the transaction
    private String user = null;

    // Client ID of client that created transaction
    private String clientID = null;

    // A readable string that describes the connection this transaction
    // was created on
    private String connectionString = null;
    private transient ConnectionUID connectionUID = null;

    private static transient JMQXid EMPTY_JMQXID = new JMQXid();

    public TransactionState() {
        this.state = CREATED;
    }

    // Copy constructor;
    public TransactionState(TransactionState ts) {
        // Unfortunately JMQXid is mutable, so we must make a copy
        this.xid = new JMQXid(ts.xid);

        // Strings are immutable, so we just assign them
        this.state = ts.state;
        this.user = ts.user;
        this.clientID = ts.clientID;
        this.connectionString = ts.connectionString;
        this.connectionUID = ts.connectionUID;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("xid", (xid == null ? "none" : xid.toString()));
        ht.put("state",names[state]);
        ht.put("user", (user == null ? "none" : user));
        ht.put("connectionString", (connectionString == null ? "none" : connectionString));
        ht.put("connectionUID", (connectionUID == null ? "null" : connectionUID.toString()));
        ht.put("clientID", (clientID == null ? "none" : clientID));
        return ht;
    }

    public void setState(int state)
        throws BrokerException {
        if (state < CREATED || state > LAST) {
            // Internal error
            throw new BrokerException("Illegal state " +
                state + ". Should be between " + CREATED + " and " + LAST +
                    " inclusive.");
        } else {
            this.state = state;
        }
    }

    public int getState() {
        return state;
    }

    public void setXid(JMQXid xid) {
        this.xid = xid;
    }

    public JMQXid getXid() {
        return this.xid;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return this.user;
    }

    public void setClientID(String id) {
        this.clientID = id;
    }

    public String getClientID() {
        return this.clientID;
    }

    public void setConnectionString(String s) {
        this.connectionString = s;
    }

    public String getConnectionString() {
        return this.connectionString;
    }

    public void setConnectionUID(ConnectionUID cuid) {
        this.connectionUID = cuid;
    }

    public ConnectionUID getConnectionUID() {
        return this.connectionUID;
    }

    /**
     * Returns the next state for this object, given an operation
     * and XAResource flag. For example if the current state is
     * STARTED and nextState is called with operation=END_TRANSACTION,
     * and xaFlag=XAResource.TMSUCCESS, then we return the state
     * COMPLETE.
     *
     * Throws an IllegalStateException if the state transition is not
     * allowed.
     *
     * Note that nextState does NOT alter the state of this object.
     * setState() must be called to do that.
     */
    public int nextState(int pktType, Integer xaFlag)
        throws BrokerException {

        switch (pktType) {

        case PacketType.START_TRANSACTION:
            if (isFlagSet(XAResource.TMNOFLAGS, xaFlag)) {
                if (this.state == CREATED ||
                    this.state == COMPLETE ||
                    this.state == STARTED) {
                    return STARTED;
                }
                break;
            } else if (isFlagSet(XAResource.TMJOIN, xaFlag)) {
                if (this.state == STARTED ||
                    this.state == COMPLETE) {
                    return STARTED;
                }
            } else if (isFlagSet(XAResource.TMRESUME, xaFlag)) {
                if (this.state == INCOMPLETE ||
                    this.state == STARTED) {
                    return STARTED;
                }
            }
            break;
        case PacketType.END_TRANSACTION:
            if (isFlagSet(XAResource.TMSUSPEND, xaFlag)) {
                if (this.state == STARTED ||
                    this.state == INCOMPLETE) {
                    return INCOMPLETE;
                }
            } else if (isFlagSet(XAResource.TMFAIL, xaFlag)) {
                if (this.state == STARTED ||
                    this.state == INCOMPLETE ||
                    this.state == FAILED) {
                    return FAILED;
                }
            } else if (isFlagSet(XAResource.TMSUCCESS, xaFlag) ||
                       isFlagSet(XAResource.TMONEPHASE, xaFlag)) {
                // XXX REVISIT 12/17/2001 dipol allow ONEPHASE since RI
                // appears to use it.
                if (this.state == STARTED ||
                    this.state == INCOMPLETE ||
                    this.state == COMPLETE) {
                    return COMPLETE;
                }
            }
            break;
        case PacketType.PREPARE_TRANSACTION:
            if (this.state == COMPLETE ||
                this.state == PREPARED) {
                return PREPARED;
            }
            break;
        case PacketType.COMMIT_TRANSACTION:
            if (isFlagSet(XAResource.TMONEPHASE, xaFlag)) {
                if (this.state == COMPLETE ||
                    this.state == COMMITTED) {
                    return COMMITTED;
                }
            } else {
                if (this.state == PREPARED ||
                    this.state == COMMITTED) {
                    return COMMITTED;
                }
            }
            break;
        case PacketType.ROLLBACK_TRANSACTION:
            if (this.state == COMPLETE || this.state == INCOMPLETE) {
                return ROLLEDBACK;
            } else if (this.state == PREPARED) {
                return ROLLEDBACK;
            } else if (this.state == FAILED || this.state == ROLLEDBACK) {
                return ROLLEDBACK;
            }
            break;
        }

        Object[] args = {PacketType.getString(pktType),
                         xaFlagToString(xaFlag),
                         this.toString(this.state)};

        throw new BrokerException(Globals.getBrokerResources().getString(
            BrokerResources.X_BAD_TXN_TRANSITION, args));
    }

    public static int remoteTransactionNextState(TransactionState ts, int nextState)
                      throws BrokerException {
        int currState = ts.getState();

        switch(nextState) {

        case COMMITTED:
            if (currState == PREPARED || currState == COMMITTED) {
                    return COMMITTED;
            }
            break;
        case ROLLEDBACK:
            if (currState == PREPARED || currState == ROLLEDBACK) {
                return ROLLEDBACK;
            }
            break;
        }

        throw new BrokerException("Transaction state "+toString(currState)+
        " can not transit to state "+toString(nextState));
    }


    /**
     * Returns "true" if the specified flag is set in xaFlags, else
     * returns "false".
     */
    public static boolean isFlagSet(int flag, Integer xaFlags) {

        if (xaFlags == null) {
            return (flag == XAResource.TMNOFLAGS);
        } else if (flag == XAResource.TMNOFLAGS ||
                   xaFlags.intValue() == XAResource.TMNOFLAGS) {
            return (flag == xaFlags.intValue());
        } else {
            return ((xaFlags.intValue() & flag) == flag);
        }
    }

    public static String toString(int state) {
        if (state < CREATED || state > ROLLEDBACK) {
            return "UNKNOWN(" + state + ")";
        } else {
            return names[state] + "(" + state + ")";
        }
    }

    /**
     * Converts an XAFlag into a easily readable form
     */
    public static String xaFlagToString(Integer flags) {
        StringBuffer sb = new StringBuffer("");
        boolean found = false;

        if (flags == null) {
            return "null";
        }

        sb.append("0x" + Integer.toHexString(flags.intValue()) + ":");

        if (isFlagSet(XAResource.TMNOFLAGS, flags)) {
            sb.append("TMNOFLAGS");
            return sb.toString();
        }

        if (isFlagSet(XAResource.TMENDRSCAN, flags)) {
            sb.append("TMENDRSCAN");
            found = true;
        }
        if (isFlagSet(XAResource.TMFAIL, flags)) {
            if (found) sb.append("|");
            sb.append("TMFAIL");
            found = true;
        }
        if (isFlagSet(XAResource.TMJOIN, flags)) {
            if (found) sb.append("|");
            sb.append("TMJOIN");
            found = true;
        }
        if (isFlagSet(XAResource.TMONEPHASE, flags)) {
            if (found) sb.append("|");
            sb.append("TMONEPHASE");
            found = true;
        }
        if (isFlagSet(XAResource.TMRESUME, flags)) {
            if (found) sb.append("|");
            sb.append("TMRESUME");
            found = true;
        }
        if (isFlagSet(XAResource.TMSTARTRSCAN, flags)) {
            if (found) sb.append("|");
            sb.append("TMSTARTSCAN");
            found = true;
        }
        if (isFlagSet(XAResource.TMSUCCESS, flags)) {
            if (found) sb.append("|");
            sb.append("TMSUCCESS");
            found = true;
        }
        if (isFlagSet(XAResource.TMSUSPEND, flags)) {
            if (found) sb.append("|");
            sb.append("TMSUSPEND");
            found = true;
        }

        // Hmmm...we found no flags we know
        if (!found) {
            sb.append("???");
        }

        return sb.toString();
    }

    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

        xid = JMQXid.read(in);
        state = in.readInt();
        user = (String)in.readObject();
        clientID = (String)in.readObject();
        connectionString = (String)in.readObject();
        connectionUID = null;
    }

    public void writeExternal(ObjectOutput out) throws IOException {

        if (xid == null) {
            EMPTY_JMQXID.write(out);
        } else {
            xid.write(out);
        }

        out.writeInt(state);
        out.writeObject(user);
        out.writeObject(clientID);
        out.writeObject(connectionString);
    }

    public String toString() {
        if (xid == null) {
            return user + "@" + clientID + ":" + toString(state);
        } else {
            return user + "@" + clientID + ":" + toString(state) +
                ":xid=" + xid.toString();
        }
    }

    public Object readResolve() throws ObjectStreamException {
        try {
            // Replace w/ the new object
            com.sun.messaging.jmq.jmsserver.data.TransactionState obj =
                new com.sun.messaging.jmq.jmsserver.data.TransactionState(
                    AutoRollbackType.NOT_PREPARED, 0, true);
            obj.setXid(xid);
            obj.setState(state);
            obj.setUser(user);
            obj.setClientID(clientID);
            obj.setConnectionString(connectionString);
            return obj;
        } catch (BrokerException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
