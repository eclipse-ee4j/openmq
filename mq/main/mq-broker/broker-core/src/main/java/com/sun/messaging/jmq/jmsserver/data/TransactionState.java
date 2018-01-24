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
 * @(#)TransactionState.java	1.38 07/23/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.*;
import java.util.Hashtable;
import java.util.Date;
import javax.transaction.xa.XAResource;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;


/*
 * XXX - LKS - 3/22/2005
 * 
 * transaction behavior w/ timeout is not implemented
 */

/**
 * The state of a transaction, plus additional information that may
 * useful to know about the transaction.
 */
public class TransactionState implements Externalizable, Serializable {

    static final long serialVersionUID = 8746365555417644726L;

    public static final int CREATED    = 0;
    public static final int STARTED    = 1;
    public static final int FAILED     = 2;
    public static final int INCOMPLETE = 3;
    public static final int COMPLETE   = 4;
    public static final int PREPARED   = 5;
    public static final int COMMITTED  = 6;
    public static final int ROLLEDBACK = 7;
    public static final int TIMED_OUT = 8;
    public static final int LAST = 8;

    public static final int NULL = -1;

    private static final String names[] = {
        "CREATED",
        "STARTED",
        "FAILED",
        "INCOMPLETE",
        "COMPLETE",
        "PREPARED",
        "COMMITED",
        "ROLLEDBACK",
        "TIMED_OUT"
        };

    private JMQXid  xid = null;

    // time the transaction was created
    private long createTime = 0;

    // lifetime (in ms) for the session (or 0 if forever)
    private long lifetime = 0;

    // calcualted expiration time (in ms) for the session (or 0 if never)
    private long expireTime = 0;

    // last time the transaction was accessed
    private long lastAccessTime = 0;

    // rollback behavior
    AutoRollbackType type = AutoRollbackType.ALL;

    // should txn rollback when session closes
    boolean sessionLess = false;

    private transient String creator = null;

    // State of the transaction
    private int state = CREATED;

    // User that created the transaction
    private String user = null;

    // Client ID of client that created transaction
    private String clientID = null;

    // A readable string that describes the connection this transaction
    // was created on
    private String connectionString = null;
    private boolean onephasePrepare = false;

    // The actual connection this transaction was created on
    private transient ConnectionUID connectionUID = null;
    private transient boolean detached = false;
    private transient long detachedTime = 0; 

    private transient int failToState = -1; 
    private transient int failFromState = -1; 

    private static final JMQXid EMPTY_JMQXID = new JMQXid();

    public TransactionState() {
        // As required for Externalize interface
    }

    public TransactionState(AutoRollbackType type, long lifetime,
            boolean sessionLess) 
    {
        this.state = CREATED;
        this.type = (type == null) ? AutoRollbackType.ALL : type;
        this.createTime = System.currentTimeMillis();
        this.lifetime = lifetime;
        this.expireTime = createTime + lifetime;
        this.lastAccessTime = createTime;
        this.sessionLess = sessionLess;
    }

    // Copy constructor;
    public TransactionState(TransactionState ts) {
        // Unfortunately JMQXid is mutable, so we must make a copy
        this.xid = new JMQXid(ts.xid);

        // Strings are immutable, so we just assign them
        this.state = ts.state;
        this.user = ts.user;
        this.clientID = ts.clientID;
        this.createTime = ts.createTime;
        this.lifetime = ts.lifetime;
        this.expireTime = ts.expireTime;
        this.lastAccessTime = ts.lastAccessTime;
        this.connectionString = ts.connectionString;
        this.sessionLess = ts.sessionLess;

        if (ts.type == null) {
            type = (ts.xid == null ? AutoRollbackType.ALL :
                  AutoRollbackType.NOT_PREPARED);
        } else {
            this.type = ts.type;
        }
        this.connectionUID = ts.connectionUID;
        this.detached = ts.detached; 
        this.detachedTime = ts.detachedTime;
        this.onephasePrepare = ts.onephasePrepare;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("xid", (xid == null ? "none" : xid.toString()));
        ht.put("state",names[state]);
        ht.put("failToState", toString(failToState));
        ht.put("failFromState", toString(failFromState));
        ht.put("user", (user == null ? "none" : user));
        ht.put("connectionString", (connectionString == null ? "none" : connectionString));
        ht.put("clientID", (clientID == null ? "none" : clientID));
        ht.put("type", getType().toString());
        ht.put("createTime", String.valueOf(createTime));
        ht.put("lifetime", Long.valueOf(lifetime).toString());
        ht.put("expireTime", Long.valueOf(expireTime).toString());
        ht.put("lastAccessTime", Long.valueOf(lastAccessTime).toString());
        ht.put("sessionLess", Boolean.valueOf(sessionLess).toString());
        ht.put("detached", String.valueOf(detached));
        ht.put("detachedTime", String.valueOf(detachedTime));
        if (state >= PREPARED) {
        ht.put("onephasePrepare", Boolean.valueOf(onephasePrepare));
        }
        return ht;
    }


    public AutoRollbackType getType() {
        return type;
    }

    public long getCreationTime() {
        return createTime;
    }

    public long getLifetime() {
        return lifetime;
    }

    public long getExpirationTime() {
        return expireTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void updateAccessTime() {
        // update database
        lastAccessTime = System.currentTimeMillis();
    }
    public boolean isSessionLess() {
        return sessionLess;
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

    public boolean isXA() {
        return this.xid != null && !this.xid.isNullXid();
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
        return (this.connectionString == null ? "none" : this.connectionString);
    }

    public void setConnectionUID(ConnectionUID cuid) {
        this.connectionUID = cuid;
    }

    public ConnectionUID getConnectionUID() {
        return this.connectionUID;
    }

    public void detachedFromConnection() {
        detached = true;
        detachedTime = System.currentTimeMillis();
    }

    public boolean isDetachedFromConnection() {
        return detached;
    }

    public long getDetachedTime() {
        return detachedTime;
    }

    public void setOnephasePrepare(boolean b) {
        onephasePrepare = b;
    }

    public void setFailToState(int fs) {
        failToState = fs;
    }

    public void setFailFromState(int fs) {
        failFromState = fs;
    }

    public boolean getOnephasePrepare() {
        return onephasePrepare;
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

        if (this.state == TransactionState.FAILED && failToState != -1) {
            Object[] args = {PacketType.getString(pktType),
                             xaFlagToString(xaFlag), (xid == null ? "null":xid.toString()),
                             toString(failToState), toString(failFromState)};
            
            throw new BrokerException(Globals.getBrokerResources().getString(
            BrokerResources.X_FAILSTATE_TXN_TRANSITION_1, args));
        }

        if (this.state == TransactionState.FAILED) {
            Object[] args = {PacketType.getString(pktType),
                             xaFlagToString(xaFlag), (xid == null ? "null":xid.toString()),
                             toString(failFromState)};
            int status = Status.ERROR;
            if (failFromState == STARTED &&
                pktType == PacketType.END_TRANSACTION) {
                status = Status.NOT_MODIFIED;
                throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_END_ON_FAILED_STATE, args), status);
            }
            throw new BrokerException(Globals.getBrokerResources().getString(
            BrokerResources.X_FAILSTATE_TXN_TRANSITION_2, args), status);
        }

        Object[] args = {PacketType.getString(pktType),
                         xaFlagToString(xaFlag),
                         toString(this.state)};

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

    public String toString() {
        if (xid == null) {
            return user + "@" + clientID + ":" + toString(state)+
                (onephasePrepare ? "[onephase=true]":"");
        } else {
            return user + "@" + clientID + ":" + toString(state)+
                ":xid=" + xid.toString()+(onephasePrepare ? "[onephase=true]":"");
        }
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String id) {
        creator = id;
    }

    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        connectionUID = null;
        creator = null;
        detached = false;
        detachedTime = 0; 
    }

     public void readExternal(ObjectInput in)
         throws IOException, ClassNotFoundException {

         xid = JMQXid.read(in);
         if (xid.isNullXid()) {
            xid = null;
         }

         state = in.readInt();
         user = (String)in.readObject();
         clientID = (String)in.readObject();
         connectionString = (String)in.readObject();
         connectionUID = null;
         onephasePrepare = false;

         try {
            if (in.available() > 0) {
                sessionLess = in.readBoolean();
                type = AutoRollbackType.getType( in.readInt() );
                createTime = in.readLong();
                lifetime = in.readLong();
                lastAccessTime = in.readLong();
                expireTime = createTime + lifetime;
            }
            if (in.available() > 0) {
                onephasePrepare = in.readBoolean();
            }
         } catch (Exception e) {
            // deal w/ missing field prior to 400
         }
         detached = false;
         detachedTime = 0;
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

         // Attributes introduce in 400
         out.writeBoolean(sessionLess);
         out.writeInt(type.intValue());
         out.writeLong(createTime);
         out.writeLong(lifetime);
         out.writeLong(lastAccessTime);

         // Attributes introduce in 410
         out.writeBoolean(onephasePrepare);
     }
}
