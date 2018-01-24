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
 * @(#)HABrokerInfo.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;

import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.io.*;

/**
 * This immutable object encapsulates general information about
 * a broker in an HA cluster.
 */
public final class HABrokerInfo implements Externalizable {

    static final long serialVersionUID = -6833553314062089908L;

    // broker info update types
    public static final int UPDATE_VERSION = 0;
    public static final int UPDATE_URL = 1;
    public static final int RESET_TAKEOVER_BROKER_READY_OPERATING = 2;
    public static final int RESTORE_HEARTBEAT_ON_TAKEOVER_FAIL = 3;
    public static final int RESTORE_ON_TAKEOVER_FAIL = 4;

    private String id;
    private String takeoverBrokerID;
    private String url;
    private int version;
    private int state;
    private long sessionID;     // Current session ID
    private long heartbeat;
    private List sessionList;   // All sessions IDs own by this broker

    private long takeoverTimestamp = 0;

    /**
     * Constructor for Externalizable interface
     */
    public HABrokerInfo() {
    }

    /**
     * Constructor
     * @param id Broker ID
     * @param takeoverBrokerID Broker ID taken over the store
     * @param url the broker's URL
     * @param version the broker's version
     * @param state the broker's state
     * @param sessionID the broker's session ID
     * @param heartbeat broker's last heartbeat
     */
    public HABrokerInfo( String id, String takeoverBrokerID, String url, int version,
        int state, long sessionID, long heartbeat ) {

        this.id = id;
        this.takeoverBrokerID = ( takeoverBrokerID == null ) ? "" : takeoverBrokerID;
        this.url = url;
        this.version = version;
        this.state = state;
        this.sessionID = sessionID;
        this.heartbeat = heartbeat;

        this.sessionList = Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getTakeoverBrokerID() {
        return takeoverBrokerID;
    }

    public String getUrl() {
        return url;
    }

    public int getVersion() {
        return version;
    }

    public int getState() {
        return state;
    }

    public long getSessionID() {
        return sessionID;
    }

    public List getAllSessions() {
        return sessionList;
    }

    public long getHeartbeat() {
        return heartbeat;
    }

    public void setSessionID( long id ) {
        sessionID = id;
    }

    public void setSessionList( List list ) {
        sessionList = list;
    }

    public void setTakeoverTimestamp(long ts) {
        takeoverTimestamp = ts;
    }

    public long getTakeoverTimestamp() {
        return takeoverTimestamp;
    }

    public String toString() {

        StringBuffer strBuf = new StringBuffer( 128 )
            .append( "(")
            .append( "brokerID=" ).append( id )
            .append( ", URL=" ).append( url )
            .append( ", version=" ).append( version )
            .append( ", state=" ).append( state ).append( " [" )
            .append( BrokerState.getState( state ).toString() ).append( "]" )
            .append( ", sessionID=" ).append( sessionID )
            .append( ", heartbeatTS=").append( heartbeat )
            .append( (heartbeat > 0) ? " [" + new Date( heartbeat ) + "]" : "" )
            .append( ", takeoverBrokerID=" ).append( takeoverBrokerID )
            .append( ")");

        return strBuf.toString();
    }

    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

        id = (String)in.readObject();
        takeoverBrokerID = (String)in.readObject();
        url = (String)in.readObject();
        version = in.readInt();
        state = in.readInt();
        sessionID = in.readLong();
        heartbeat = in.readLong();
        sessionList = (List)in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeObject(id);
        out.writeObject(takeoverBrokerID);
        out.writeObject(url);
        out.writeInt(version);
        out.writeInt(state);
        out.writeLong(sessionID);
        out.writeLong(heartbeat);
        out.writeObject(sessionList);
    }

    public static class StoreSession implements Externalizable {

        static final long serialVersionUID = -1619140799512705251L;

        private long id;
        private String brokerID;
        private int isCurrent;
        private String createdBy;
        private long createdTS;

        /**
         * Constructor for Externalizable interface
         */
        public StoreSession() {
        }

        public StoreSession( long id, String brokerID, int isCurrent,
            String createdBy, long createdTS ) {

            this.id = id;
            this.brokerID = brokerID;
            this.isCurrent = isCurrent;
            this.createdBy = createdBy;
            this.createdTS = createdTS;
        }

        public long getID() {
            return id;
        }

        public String getBrokerID() {
            return brokerID;
        }

        public int getIsCurrent() {
            return isCurrent;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public long getCreatedTS() {
            return createdTS;
        }

        public void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException {

            id = in.readLong();
            brokerID = (String)in.readObject();
            isCurrent = in.readInt();
            createdBy = (String)in.readObject();
            createdTS = in.readLong();
        }

        public void writeExternal(ObjectOutput out) throws IOException {

            out.writeLong(id);
            out.writeObject(brokerID);
            out.writeInt(isCurrent);
            out.writeObject(createdBy);
            out.writeLong(createdTS);
        }
    }
}

