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
 * @(#)ProtocolGlobals.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;

/**
 * This class defines all of the new cluster protocol packet
 * types and constants.
 */
public class ProtocolGlobals 
{
    public static final int VERSION_500 = ClusterBroadcast.VERSION_500;
    public static final int VERSION_460 = ClusterBroadcast.VERSION_460;
    public static final int VERSION_410 = ClusterBroadcast.VERSION_410;
    public static final int VERSION_400 = ClusterBroadcast.VERSION_400;
    public static final int VERSION_350 = ClusterBroadcast.VERSION_350;


    /*
     * Cluster protocol GPacket Types
     */
    public static final short G_MESSAGE_DATA                = 1;
    public static final short G_MESSAGE_DATA_REPLY          = 2;

    public static final short G_MESSAGE_ACK                 = 3;
    public static final short G_MESSAGE_ACK_REPLY           = 4;

    public static final short G_NEW_INTEREST                = 5;
    public static final short G_NEW_INTEREST_REPLY          = 6;

    public static final short G_REM_DURABLE_INTEREST        = 7;
    public static final short G_REM_DURABLE_INTEREST_REPLY  = 8;

    public static final short G_INTEREST_UPDATE             = 9;
    public static final short G_INTEREST_UPDATE_REPLY       = 10;

    public static final short G_LOCK                        = 11;
    public static final short G_LOCK_REPLY                  = 12;

    public static final short G_UPDATE_DESTINATION          = 13;
    public static final short G_UPDATE_DESTINATION_REPLY    = 14;

    public static final short G_REM_DESTINATION             = 15;
    public static final short G_REM_DESTINATION_REPLY       = 16;

    public static final short G_CONFIG_CHANGE_EVENT         = 17;
    public static final short G_CONFIG_CHANGE_EVENT_REPLY   = 18;

    public static final short G_GET_CONFIG_CHANGES_REQUEST  = 19;
    public static final short G_GET_CONFIG_CHANGES_REPLY    = 20;

    public static final short G_CLIENT_CLOSED               = 21;
    public static final short G_CLIENT_CLOSED_REPLY         = 22;

    public static final short G_STOP_MESSAGE_FLOW           = 23;
    public static final short G_STOP_MESSAGE_FLOW_REPLY     = 24;

    public static final short G_RESUME_MESSAGE_FLOW         = 25;
    public static final short G_RESUME_MESSAGE_FLOW_REPLY   = 26;

    public static final short G_RELOAD_CLUSTER              = 27;
    public static final short G_RELOAD_CLUSTER_REPLY        = 28;

    public static final short G_GET_INTEREST_UPDATE         = 29;
    public static final short G_GET_INTEREST_UPDATE_REPLY   = 30;

    public static final short G_RESET_PERSISTENCE           = ClusterBroadcast.TYPE_RESET_PERSISTENCE;
    public static final short G_RESET_PERSISTENCE_REPLY     = 32;

    public static final short G_PING                        = 33;
    public static final short G_PING_REPLY                  = 34;

    public static final short G_DURABLE_ATTACH              = 35;
    public static final short G_DURABLE_ATTACH_REPLY        = 36;

    public static final short G_GOODBYE                     = 37;
    public static final short G_GOODBYE_REPLY               = 38;

    public static final short G_TAKEOVER_PENDING            = 39;
    public static final short G_TAKEOVER_PENDING_REPLY      = 40;

    public static final short G_TAKEOVER_COMPLETE           = 41;

    public static final short G_TAKEOVER_ABORT              = 43;

    public static final short G_BROKER_INFO_REPLY           = 46;

    public static final short G_TRANSACTION_INQUIRY         = 47;
    public static final short G_TRANSACTION_INFO            = 48;

    public static final short G_FIRST_INFO                  = 49;

    public static final short G_NEW_MASTER_BROKER_PREPARE           = 51;
    public static final short G_NEW_MASTER_BROKER_PREPARE_REPLY     = 52;

    public static final short G_NEW_MASTER_BROKER           = 53;
    public static final short G_NEW_MASTER_BROKER_REPLY     = 54;

    public static final short G_REPLICATION_GROUP_INFO     = 55;

    public static final short G_TAKEOVER_ME_PREPARE     = 57;
    public static final short G_TAKEOVER_ME_PREPARE_REPLY     = 58;

    public static final short G_TAKEOVER_ME           = 59;
    public static final short G_TAKEOVER_ME_REPLY     = 60;

    public static final short G_TAKEOVER_ME_REPLY_ACK     = 61;

    public static final short G_TRANSFER_FILE_REQUEST       = 63;
    public static final short G_TRANSFER_FILE_REQUEST_REPLY = 64;

    public static final short G_TRANSFER_FILE_LIST        = 65;

    public static final short G_TRANSFER_FILE_START       = 67;

    public static final short G_TRANSFER_FILE_END         = 69;
    public static final short G_TRANSFER_FILE_END_ACK     = 70;

    public static final short G_TRANSFER_FILE_END_ACK_ACK   = 71;

    public static final short G_INFO_REQUEST   = 73;
    public static final short G_INFO   = 74;

    public static final short G_NOTIFY_PARTITION_ARRIVAL   = 75;
    public static final short G_NOTIFY_PARTITION_ARRIVAL_REPLY   = 76;

    public static final short G_MAX_PACKET_TYPE       = 76;


    private static final String[] packetTypeNames = {
    "NULL",  
    "G_MESSAGE_DATA",                 /* = 1 */
    "G_MESSAGE_DATA_REPLY",           /* = 2 */
    "G_MESSAGE_ACK",                  /* = 3 */
    "G_MESSAGE_ACK_REPLY",            /* = 4 */
    "G_NEW_INTEREST",                 /* = 5 */
    "G_NEW_INTEREST_REPLY",           /* = 6 */
    "G_REM_DURABLE_INTEREST",         /* = 7 */
    "G_REM_DURABLE_INTEREST_REPLY",   /* = 8 */
    "G_INTEREST_UPDATE",              /* = 9 */
    "G_INTEREST_UPDATE_REPLY",       /* = 10 */
    "G_LOCK",                        /* = 11 */
    "G_LOCK_REPLY",                  /* = 12 */
    "G_UPDATE_DESTINATION",          /* = 13 */
    "G_UPDATE_DESTINATION_REPLY",    /* = 14 */
    "G_REM_DESTINATION",             /* = 15 */
    "G_REM_DESTINATION_REPLY",       /* = 16 */
    "G_CONFIG_CHANGE_EVENT",         /* = 17 */
    "G_CONFIG_CHANGE_EVENT_REPLY",   /* = 18 */
    "G_GET_CONFIG_CHANGES_REQUEST",  /* = 19 */
    "G_GET_CONFIG_CHANGES_REPLY",    /* = 20 */
    "G_CLIENT_CLOSED",               /* = 21 */
    "G_CLIENT_CLOSED_REPLY",         /* = 22 */
    "G_STOP_MESSAGE_FLOW",           /* = 23 */
    "G_STOP_MESSAGE_FLOW_REPLY",     /* = 24 */
    "G_RESUME_MESSAGE_FLOW",         /* = 25 */
    "G_RESUME_MESSAGE_FLOW_REPLY",   /* = 26 */
    "G_RELOAD_CLUSTER",              /* = 27 */
    "G_RELOAD_CLUSTER_REPLY",        /* = 28 */
    "G_GET_INTEREST_UPDATE",         /* = 29 */
    "G_GET_INTEREST_UPDATE_REPLY",   /* = 30 */
    "G_RESET_PERSISTENCE",           /* = 31 */
    "G_RESET_PERSISTENCE_REPLY",     /* = 32 */
    "G_PING",                        /* = 33 */
    "G_PING_REPLY",                  /* = 34 */
    "G_DURABLE_ATTACH",              /* = 35 */
    "G_DURABLE_ATTACH_REPLY",        /* = 36 */

    "G_GOODBYE",                     /* = 37 */
    "G_GOODBYE_REPLY",               /* = 38 */

    "G_TAKEOVER_PENDING",            /* = 39 */
    "G_TAKEOVER_PENDING_REPLY",      /* = 40 */

    "G_TAKEOVER_COMPLETE",           /* = 41 */
    "UNKNOWN",

    "G_TAKEOVER_ABORT",              /* = 43 */
    "UNKNOWN",

    "UNKNOWN",
    "G_BROKER_INFO_REPLY",           /* = 46 */

    "G_TRANSACTION_INQUIRY",         /* = 47 */
    "G_TRANSACTION_INFO",            /* = 48 */

    "G_FIRST_INFO",                  /* = 49 */
    "UNKNOWN",                       /* = 50 */

    "G_NEW_MASTER_BROKER_PREPARE",         /* = 51 */
    "G_NEW_MASTER_BROKER_PREPARE_REPLY",   /* = 52 */

    "G_NEW_MASTER_BROKER",                 /* = 53 */
    "G_NEW_MASTER_BROKER_REPLY",           /* = 54 */

    "G_REPLICATION_GROUP_INFO",           /* = 55 */
    "UNKNOWN",                            /* = 56 */

    "G_TAKEOVER_ME_PREPARE",         /* = 57 */
    "G_TAKEOVER_ME_PREPARE_REPLY",   /* = 58 */

    "G_TAKEOVER_ME",         /* = 59 */
    "G_TAKEOVER_ME_REPLY",   /* = 60 */
    "G_TAKEOVER_ME_REPLY_ACK",   /* = 61 */
    "UNKNOWN",                   /* = 62 */

    "G_TRANSFER_FILE_REQUEST",       /* = 63 */
    "G_TRANSFER_FILE_REQUEST_REPLY", /* = 64 */

    "G_TRANSFER_FILE_LIST",       /* = 65 */
    "UNKNOWN",                   /* = 66 */

    "G_TRANSFER_FILE_START", /* = 67 */
    "UNKNOWN",               /* = 68 */

    "G_TRANSFER_FILE_END",         /* = 69 */
    "G_TRANSFER_FILE_END_ACK",     /* = 70 */
    "G_TRANSFER_FILE_END_ACK_ACK", /* = 71 */
    "UNKNOWN",                     /* = 72 */

    "G_INFO_REQUEST",             /* = 73 */
    "G_INFO",                     /* = 74 */

    "G_NOTIFY_PARTITION_ARRIVAL",       /* = 75 */
    "G_NOTIFY_PARTITION_ARRIVAL_REPLY"  /* = 76 */

    };


    /**
     * Return a string description of the specified packet type
     *
     * @param    n    Type to return description for
     */
    public static String getPacketTypeString(int n) {
    if (n < 0 || n > G_MAX_PACKET_TYPE) {
        return "UNKNOWN(" + n + ")";
    }
    return packetTypeNames[n] + "(" + n + ")";
    }

    public static String getPacketTypeDisplayString(int n) {
    if (n < 0 || n > G_MAX_PACKET_TYPE) {
        return "UNKNOWN";
    }
    String name = packetTypeNames[n]; 
    if (name.startsWith("G_")) return name.substring(2);
    return name;
    }

    //
    // Interest update types for G_INTEREST_UPDATE messages.
    //
    /** Interest removed */
    public static final int G_REM_INTEREST = 2;

    /** Durable interest detached */
    public static final int G_DURABLE_DETACH = 3;

    /** New primary interest for failover queue */
    public static final int G_NEW_PRIMARY_INTEREST = 4;

    public static String getInterestUpdateSubTypeString(int n) {
        if (n == G_REM_INTEREST) return "REM_INTEREST";
        if (n == G_DURABLE_DETACH) return "DURABLE_DETACH";
        if (n == G_NEW_PRIMARY_INTEREST) return "NEW_PRIMARY_INTEREST";
        return "UNKNOWN";
    }

    //
    // Election protocol constants for G_LOCK_REQUEST and
    // G_LOCK_RESPONSE packets.
    //
    /** Lock request timed out */
    public static final int G_LOCK_TIMEOUT = -1;

    /** No conflict, permission granted */
    public static final int G_LOCK_SUCCESS = 0;

    /** Resource already locked, permission denied */
    public static final int G_LOCK_FAILURE = 1;

    /** Locking conflict, use binary exponential backoff */
    public static final int G_LOCK_BACKOFF = 2;

    /** Abort and retry resource lock operation */
    public static final int G_LOCK_TRY_AGAIN = 3;

    // lockResponseStrings is indexed by the above constants.
    static final String[] lockResponseStrings = {
        "LOCK_SUCCESS",
        "LOCK_FAILURE",
        "LOCK_BACKOFF",
    };

    public static final String getLockStatusString(int status) {
        switch (status) {
            case G_LOCK_TIMEOUT: return "LOCK_TIMEOUT";
            case G_LOCK_SUCCESS: return "LOCK_SUCCESS";
            case G_LOCK_FAILURE: return "LOCK_FAILURE";
            case G_LOCK_BACKOFF: return "LOCK_BACKOFF";
            case G_LOCK_TRY_AGAIN: return "LOCK_TRY_AGAIN";
            default: return "UNKNOWN("+status+")";
       }
    };

    public static final int G_LOCK_MAX_ATTEMPTS = 10;
    public static final int G_RESOURCE_LOCKING = 0;
    public static final int G_RESOURCE_LOCKED = 1;


    public static final int G_BROKER_INFO_OK = 0;
    public static final int G_BROKER_INFO_TAKINGOVER = 1;
    //
    // Cluster configuration event log stuff -
    // 
    /** Waiting for the central broker's response */
    public static final int G_EVENT_LOG_WAITING = 0;

    /** Event logged successfully */
    public static final int G_EVENT_LOG_SUCCESS = 1;

    /** Event could not be logged */
    public static final int G_EVENT_LOG_FAILURE = 2;

    public static final int G_EVENT_LOG_CLOCK_SKEW_TOLERANCE =
        120 * 1000; // 2 Minutes clock skew tolerance...

    public static final int G_SUCCESS = 200;

    public static final String CFGSRV_BACKUP_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.backup";

    public static final String CFGSRV_RESTORE_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.restore";

    public static int getCurrentVersion() {
        return VERSION_500;
    }

    protected static final int DEFAULT_WAIT_REPLY_TIMEOUT = 180; //secs
    private static int waitReplyTimeout = Globals.getConfig().getIntProperty(
                Globals.IMQ + ".cluster.waitReplyTimeout", DEFAULT_WAIT_REPLY_TIMEOUT); 
    public static int getWaitReplyTimeout() {
        if (waitReplyTimeout <= 0) {
            return DEFAULT_WAIT_REPLY_TIMEOUT;
        }
        return waitReplyTimeout;
    }

    protected static final int DEFAULT_WAIT_INFO_REPLY_TIMEOUT = 60; //secs
    private static int waitInfoReplyTimeout = Globals.getConfig().getIntProperty(
        Globals.IMQ + ".cluster.waitInfoReplyTimeout", DEFAULT_WAIT_INFO_REPLY_TIMEOUT); 
    public static int getWaitInfoReplyTimeout() {
        if (waitInfoReplyTimeout <= 0) {
            return DEFAULT_WAIT_INFO_REPLY_TIMEOUT;
        }
        return waitInfoReplyTimeout;
    }

    private static int ackTimeout = Globals.getConfig().getIntProperty(
        Globals.IMQ + ".cluster.acktimeout", getWaitReplyTimeout());
    public static int getAckTimeout() {
        if (ackTimeout <= 0) {
            return DEFAULT_WAIT_REPLY_TIMEOUT;
        }
        return ackTimeout;
    }

    private static final int DEFAULT_LOCKTIMEOUT = 60; //secs
    private static int lockTimeout = Globals.getConfig().getIntProperty(
              Globals.IMQ + ".cluster.locktimeout", DEFAULT_LOCKTIMEOUT);
    public static int getLockTimeout() {
        if (lockTimeout <= 0) {
            return DEFAULT_LOCKTIMEOUT;
        }
        return lockTimeout;
    }
}

/*
 * EOF
 */
