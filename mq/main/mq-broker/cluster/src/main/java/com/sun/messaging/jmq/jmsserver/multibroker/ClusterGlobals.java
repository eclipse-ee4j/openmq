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
 * @(#)ClusterGlobals.java	1.12 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;

public class ClusterGlobals 
{
    public static final String TOPOLOGY_PROPERTY = Globals.IMQ + ".topology";

    //
    // Acknowledgement types for the MB_MESSAGE_ACK packet.
    //

    /** Message sent acknowledgement. For recording statistics??? */
    public static final int MB_MSG_SENT = 0;

    /** Client is no longer interested in this message. */
    public static final int MB_MSG_IGNORED = 1;

    /** Message has been delivered to the client */
    public static final int MB_MSG_DELIVERED = 2;

    /** Message consumed by the client */
    public static final int MB_MSG_CONSUMED = 3;
    public static final int MB_MSG_UNDELIVERABLE = 4;
    public static final int MB_MSG_DEAD = 5;
    public static final int MB_MSG_TXN_PREPARE = 9;
    public static final int MB_MSG_TXN_ROLLEDBACK = 10;
    public static final int MB_MSG_TXN_ACK_RN = 6; /* reserved */
    public static final int MB_MSG_TXN_PREPARE_RN = 7; /* reserved */
    public static final int MB_MSG_TXN_ROLLEDBACK_RN = 8; /* reserved */

    public static String getAckTypeString(int ackType) {
        switch (ackType) {
            case MB_MSG_SENT: return "MSG_SENT";
            case MB_MSG_IGNORED: return "MSG_IGNORED";
            case MB_MSG_DELIVERED: return "MSG_DELIVERED";
            case MB_MSG_CONSUMED: return "MSG_CONSUMED";
            case MB_MSG_UNDELIVERABLE: return "MSG_UNDELIVERABLE";
            case MB_MSG_DEAD: return "MSG_DEAD";
            case MB_MSG_TXN_PREPARE: return "MSG_TXN_PREPARE";
            case MB_MSG_TXN_ROLLEDBACK: return "MSG_TXN_ROLLEDBACK";
            default: return "UNKNOWN";
        }
    }

    //
    // Interest update types for MB_INTEREST_UPDATE messages.
    //

    /** New interest OR durable attach */
    public static final int MB_NEW_INTEREST = 1;

    /** Interest removed */
    public static final int MB_REM_INTEREST = 2;

    /** Durable interest detached */
    public static final int MB_DURABLE_DETACH = 3;

    /** New primary interest for failover queue */
    public static final int MB_NEW_PRIMARY_INTEREST = 4;

    /** New primary interest for failover queue */
    public static final int MB_REM_DURABLE_INTEREST = 5;

    //
    // Cluster configuration event log stuff -
    // 

    /** Waiting for the central broker's response */
    public static final int MB_EVENT_LOG_WAITING = 0;

    /** Event logged successfully */
    public static final int MB_EVENT_LOG_SUCCESS = 1;

    /** Event could not be logged */
    public static final int MB_EVENT_LOG_FAILURE = 2;

    //
    // Destination update types for MB_DESTINATION_UPDATE messages.
    //

    /** New destination */
    public static final int MB_NEW_DESTINATION = 1;

    /** Destination destroyed */
    public static final int MB_REM_DESTINATION = 2;

    public static final int MB_UPD_DESTINATION = 3;


    public static final int MB_LOCK_MAX_ATTEMPTS = 10;
    public static final int MB_RESOURCE_LOCKING = 0;
    public static final int MB_RESOURCE_LOCKED = 1;
    public static final int MB_EVENT_LOG_CLOCK_SKEW_TOLERANCE =
        120 * 1000; // 2 Minutes clock skew tolerance...

    public static final String CFGSRV_BACKUP_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.backup";

    public static final String CFGSRV_RESTORE_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.restore";


    public static final String STORE_PROPERTY_LASTCONFIGSERVER =
                                      "MessageBus.lastConfigServer";
    public static final String STORE_PROPERTY_LASTREFRESHTIME =
                                      "MessageBus.lastRefreshTime";

    public static final String STORE_PROPERTY_LASTSEQ =
                             "ShareConfigRecord.lastSequenceNumber";
    public static final String STORE_PROPERTY_LAST_RESETUUID =
                             "ShareConfigRecord.lastResetUUID";
}
