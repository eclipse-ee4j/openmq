/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

/**
 * Constants, default values etc. Constants of database table names and column names. Documentation of schema.
 */
public interface DBConstants {

    String SCHEMA_VERSION = "41";
    String SCHEMA_VERSION_40 = "40";

    /**
     * JDBC table name prefixes and column names for version 370 & 350 tables
     */

    /**
     * Store version: Holds the version of the broker's persistence store.
     *
     * CREATE TABLE IMQSV[37|35]<brokerid> ( STOREVERSION INTEGER, BROKERID VARCHAR(100) <- since 350
     */
    String VERSION_TBL_37 = "IMQSV37";
    String VERSION_TBL_35 = "IMQSV35";
    String TVERSION_CVERSION = "STOREVERSION";
    String TVERSION_CBROKERID = "BROKERID";

    /**
     * Configuration change record table: In master broker only. Holds change records for destinations and durables that are
     * propagated around the cluster. One row per record.
     *
     * CREATE TABLE IMQCCREC[37|35]<brokerid> ( RECORDTIME BIGINT, RECORD LONGVARBINARY)
     *
     * RECORDTIME - timestamp when the record was created RECORD - configuration record in serialized form (byte[])
     */
    String CONFIGRECORD_TBL_37 = "IMQCCREC37";
    String CONFIGRECORD_TBL_35 = "IMQCCREC35";
    String TCONFIG_CTIME = "RECORDTIME";
    String TCONFIG_CRECORD = "RECORD";

    /**
     * Destination table: Holds destinations configured on this broker. One row per Destination.
     *
     * CREATE TABLE IMQDEST[37|35]<brokerid> ( DID VARCHAR(100), DEST LONGVARBINARY, PRIMARY KEY(DID));
     *
     * DID - Unique name of the Destination object DEST - Serialized Destination object
     */
    String DESTINATION_TBL_37 = "IMQDEST37";
    String DESTINATION_TBL_35 = "IMQDEST35";
    String TDEST_CDID = "DID";
    String TDEST_CDEST = "DEST";

    /**
     * Interest table: Holds durable subscriptions. One row per Interest.
     *
     * CREATE TABLE IMQINT[37|35]<brokerid> ( CUID BIGINT, INTEREST LONGVARBINARY, PRIMARY KEY(CUID));
     *
     * CUID - Long value of the ConsumerUID of the Interest object INTEREST - serialized Interest object
     */
    String INTEREST_TBL_37 = "IMQINT37";
    String INTEREST_TBL_35 = "IMQINT35";
    String TINT_CCUID = "CUID";
    String TINT_CINTEREST = "INTEREST";

    /**
     * Message table: Holds persisted messages. One row per message.
     *
     * CREATE TABLE IMQMSG[37|35]<brokerid> ( MID VARCHAR(100), DID VARCHAR(100), <- since 350 MSGSIZE BIGINT, <- since 350
     * MSG LONGVARBINARY, PRIMARY KEY(MID));
     *
     * MID - Unique name of the SysMessageID of the message packet DID - Unique name of the Destination of this message
     * MSGSIZE - byte count of the message MSG - wire format of the Packet object (the message packet)
     */
    String MESSAGE_TBL_37 = "IMQMSG37";
    String MESSAGE_TBL_35 = "IMQMSG35";
    String TMSG_CMID = "MID";
    String TMSG_CDID = "DID";
    String TMSG_CMSGSIZE = "MSGSIZE";
    String TMSG_CMSG = "MSG";

    /**
     * Property table: General purpose name/value pair. One row per property name/value pair. Used to hold cluster related
     * properties.
     *
     * CREATE TABLE IMQPROPS[37|35]<brokerid> ( PROPNAME VARCHAR(100), PROPVALUE LONGVARBINARY, PRIMARY KEY(PROPNAME));
     *
     * PROPNAME - property name PROPVALUE - property value; serialized object
     */
    String PROPERTY_TBL_37 = "IMQPROPS37";
    String PROPERTY_TBL_35 = "IMQPROPS35";
    String TPROP_CNAME = "PROPNAME";
    String TPROP_CVALUE = "PROPVALUE";

    /**
     * Interest state table: Associates a message to each interest it was sent to and tracks the acknowledgement state. One
     * row per message/interest pair.
     *
     * CREATE TABLE IMQILIST[37|35]<brokerid> ( MID VARCHAR(100), CUID BIGINT, DID VARCHAR(100), <- since 350 STATE INTEGER,
     * PRIMARY KEY(MID, CUID));
     *
     * MID - unique name of the SysMessageID of the message CUID - long value of the ConsumerUID of the interest DID -
     * Unique name of the Destination of the message STATE - state of the interest w.r.t. the message
     */
    String INTEREST_STATE_TBL_37 = "IMQILIST37";
    String INTEREST_STATE_TBL_35 = "IMQILIST35";
    String TINTSTATE_CMID = "MID";
    String TINTSTATE_CCUID = "CUID";
    String TINTSTATE_CDID = "DID";
    String TINTSTATE_CSTATE = "STATE";

    /**
     * Transaction table: Holds all in-progress transactions. One row per transaction.
     *
     * CREATE TABLE IMQTXN[37|35]<brokerid> ( TUID BIGINT, STATE INTEGER, TSTATEOBJ LONGVARBINARY, PRIMARY KEY(TUID));
     *
     * TUID - long value of the UID of the transaction TSTATEOBJ - serialized transaction state object STATE - State of the
     * transaction. This duplicates and is an optimization of the state filed in the TransactionState object. This overrides
     * the state field in the TransactionState object. If its value is -1, then the transaction has been marked for
     * deletion.
     */
    String TXN_TBL_37 = "IMQTXN37";
    String TXN_TBL_35 = "IMQTXN35";
    String TTXN_CTUID = "TUID";
    String TTXN_CSTATE = "STATE";
    String TTXN_CSTATEOBJ = "TSTATEOBJ";
    int TXN_DELETED = -1;

    /**
     * Transaction Acknowledgement table: Maps Transaction to its pending acknowledgements. One row per transaction UID and
     * acknowledgement pair.
     *
     * CREATE TABLE IMQTACK[37|35]<brokerid> ( TUID BIGINT, TXNACK LONGVARBINARY);
     *
     * TUID - long value of the UID of the transaction TXNACK - serialized TransactionAcknowledgement object
     */
    String TXNACK_TBL_37 = "IMQTACK37";
    String TXNACK_TBL_35 = "IMQTACK35";
    String TTXNACK_CTUID = "TUID";
    String TTXNACK_CACK = "TXNACK";
}

