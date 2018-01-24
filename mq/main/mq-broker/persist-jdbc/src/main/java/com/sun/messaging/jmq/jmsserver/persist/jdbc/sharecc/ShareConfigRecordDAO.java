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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import java.sql.Connection;
import java.util.List;

/**
 * This class is an interface for the Shared Configuration Change Record
 * table which will be implemented by database specific code.
 */
public interface ShareConfigRecordDAO extends BaseDAO {

    /**
     * Shared Configuration Change Record table:
     * Holds change records for 
     * . durable subscriptions
     * . administratively created destinations
     * used in a conventional cluster
     *
     * CREATE TABLE MQSHARECC<schemaVersion>C<clusterID> (
     *        SEQ BIGINT NOT NULL AUTO_INCREMENT,
     *        UID VARCHAR(100) NOT NULL,
     *        RECORD MEDIUMBLOB NOT NULL,
     *        TYPE INT NOT NULL,
     *        CREATED_TS BIGINT NOT NULL,
     *        FLAG INT
     * );
     *
     * ID - auto-increment, uniqued
     * UID - global unique identifier for this record
     * RECORD - configuration Record
     * CREATED_TS - local timestamp when the entry was created
     * FLAG - a reserved flag field (current only last 2 bits used - for durable sub share type)  
     */
    public static final String TABLE = "MQSHARECC";
    public static final String TABLE_NAME_PREFIX = TABLE +
                        JDBCShareConfigChangeStore.SCHEMA_VERSION;
    public static final String SEQ_COLUMN = "SEQ";
    public static final String UUID_COLUMN = "UUID";
    public static final String RECORD_COLUMN = "RECORD";
    public static final String TYPE_COLUMN = "TYPE";
    public static final String UKEY_COLUMN = "UKEY";
    public static final String CREATED_TS_COLUMN = "CREATED_TS";
    public static final String FLAG_COLUMN = "FLAG";
    public static final String LOCK_ID_COLUMN = "LOCK_ID";

    ChangeRecordInfo insert( Connection conn, ChangeRecordInfo rec)
                 throws BrokerException;

    void insertResetRecord( Connection conn, ChangeRecordInfo rec, String lockID)
                            throws BrokerException;

    List<ChangeRecordInfo> getRecords( Connection conn, Long seq, String resetUUID, boolean canReset )
    throws BrokerException;

    List<ChangeRecordInfo> getAllRecords( Connection conn, String sql ) 
    throws BrokerException;

    String getLockID( Connection conn )
    throws BrokerException;

    void updateLockID( Connection conn, String newLockID, String oldLockID )
    throws BrokerException;

    void insertAll( List<ChangeRecordInfo> recs, String oldTableName ) 
    throws BrokerException;

}
