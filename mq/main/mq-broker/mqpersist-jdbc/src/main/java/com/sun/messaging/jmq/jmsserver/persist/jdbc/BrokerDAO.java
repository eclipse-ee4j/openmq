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

import java.util.List;
import java.util.HashMap;
import java.sql.Connection;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;

/**
 * This class is an interface for the Broker table which will be implemented by database specific code.
 */
public interface BrokerDAO extends BaseDAO {

    /**
     * Broker table: Holds all the broker info in a HA cluster.
     *
     * CREATE TABLE MQBKR<schemaVersion>[C<clusterID>|S<brokerID>] ( ID VARCHAR(100) NOT NULL, URL VARCHAR(100) NOT NULL,
     * VERSION INTEGER NOT NULL, STATE INTEGER NOT NULL, TAKEOVER_BROKER VARCHAR(100), HEARTBEAT_TS BIGINT, PRIMARY KEY(ID)
     * );
     *
     * ID - Unique ID of the broker URL - The URL of the broker (i.e. includes hostname and port) VERSION - Current version
     * of the borker STATE - State of the broker TAKEOVER_BROKER - Name of broker that has taken over the store HEARTBEAT_TS
     * - Timestamp periodically updated by a running borker
     */
    String TABLE = "MQBKR";
    String TABLE_NAME_PREFIX = TABLE + DBConstants.SCHEMA_VERSION;
    String ID_COLUMN = "ID";
    String URL_COLUMN = "URL";
    String VERSION_COLUMN = "VERSION";
    String STATE_COLUMN = "STATE";
    String TAKEOVER_BROKER_COLUMN = "TAKEOVER_BROKER";
    String HEARTBEAT_TS_COLUMN = "HEARTBEAT_TS";

    void insert(Connection conn, String id, String takeoverID, String url, int version, int state, Long sessionID, long heartbeat, List<UID> additionalSessions)
            throws BrokerException;

    UID update(Connection conn, String id, int updateType, Object oldValue, Object newValue) throws BrokerException;

    Long updateHeartbeat(Connection conn, String id) throws BrokerException;

    Long updateHeartbeat(Connection conn, String id, long lastHeartbeat) throws BrokerException;

    boolean updateState(Connection conn, String id, BrokerState newState, BrokerState expectedState, boolean local) throws BrokerException;

    void delete(Connection conn, String id) throws BrokerException;

    HABrokerInfo takeover(Connection conn, String brokerID, String targetBrokerID, long lastHeartbeat, BrokerState expectedState, long newHeartbeat,
            BrokerState newState) throws BrokerException;

    long getHeartbeat(Connection conn, String id) throws BrokerException;

    HashMap getAllHeartbeats(Connection conn) throws BrokerException;

    BrokerState getState(Connection conn, String id) throws BrokerException;

    Object[] getAllStates(Connection conn) throws BrokerException;

    HABrokerInfo getBrokerInfo(Connection conn, String id) throws BrokerException;

    HashMap getAllBrokerInfos(Connection conn, boolean loadSession) throws BrokerException;

    HashMap getAllBrokerInfosByState(Connection conn, BrokerState state) throws BrokerException;

    boolean isBeingTakenOver(Connection conn, String id) throws BrokerException;
}
