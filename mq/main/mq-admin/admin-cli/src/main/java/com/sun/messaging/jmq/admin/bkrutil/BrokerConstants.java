/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
 * @(#)BrokerConstants.java	1.32 07/11/07
 */

package com.sun.messaging.jmq.admin.bkrutil;

import java.util.List;

/**
 * Interface containing constants related to broker administration.
 *
 * This currently holds property names that are shared/common across all broker admin tools.
 */
public interface BrokerConstants {

    /*
     * Property names for broker attributes
     *
     */
    String PROP_NAME_BKR_INSTANCE_NAME = "imq.instancename";
    String PROP_NAME_BKR_PRIMARY_PORT = "imq.portmapper.port";
    String PROP_NAME_BKR_AUTOCREATE_TOPIC = "imq.autocreate.topic";
    String PROP_NAME_BKR_AUTOCREATE_QUEUE = "imq.autocreate.queue";
    String PROP_NAME_BKR_QUEUE_DELIVERY_POLICY = "imq.queue.deliverypolicy";
    String PROP_NAME_BKR_LOG_LEVEL = ".level";
    String PROP_NAME_BKR_LOG_ROLL_SIZE = "java.util.logging.FileHandler.limit";
    String PROP_NAME_BKR_LOG_ROLL_INTERVAL = "imq.log.file.rolloversecs";
    /*
     * public static String PROP_NAME_BKR_METRIC_INTERVAL = "imq.metrics.interval";
     */
    String PROP_NAME_BKR_MAX_MSG = "imq.system.max_count";
    String PROP_NAME_BKR_MAX_TTL_MSG_BYTES = "imq.system.max_size";
    String PROP_NAME_BKR_MAX_MSG_BYTES = "imq.message.max_size";

    String PROP_NAME_BKR_CUR_MSG = "imq.system.current_count";
    String PROP_NAME_BKR_CUR_TTL_MSG_BYTES = "imq.system.current_size";

    String PROP_NAME_BKR_CLS_BKRLIST = "imq.cluster.brokerlist";
    String PROP_NAME_BKR_CLS_BKRLIST_ACTIVE = "imq.cluster.brokerlist.active";
    String PROP_NAME_BKR_CLS_CFG_SVR = "imq.cluster.masterbroker";
    String PROP_NAME_BKR_CLS_URL = "imq.cluster.url";
    String PROP_NAME_BKR_CLS_CLUSTER_ID = "imq.cluster.clusterid";
    String PROP_NAME_BKR_CLS_HA = "imq.cluster.ha";
    String PROP_NAME_BKR_STORE_MIGRATABLE = "imq.storemigratable";
    String PROP_NAME_BKR_PARTITION_MIGRATABLE = "imq.partitionmigratable";
    String PROP_NAME_BKR_CLS_BROKER_ID = "imq.brokerid";
    String PROP_NAME_BKR_PRODUCT_VERSION = "imq.product.version";
    String PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS = "imq.autocreate.queue.maxNumActiveConsumers";
    String PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS = "imq.autocreate.queue.maxNumBackupConsumers";
    String PROP_NAME_BKR_LOG_DEAD_MSGS = "imq.destination.logDeadMsgs";
    String PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY = "imq.destination.DMQ.truncateBody";
    String PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ = "imq.autocreate.destination.useDMQ";
    String PROP_NAME_BKR_IS_EMBEDDED = "imq.embedded";
    String PROP_NAME_BKR_VARHOME = "imq.varhome";
    String PROP_NAME_BKR_LICENSE_DESC = "imq.license.description";
    String PROP_NAME_DMQ_CUR_MSG = "imq.dmq.current_count";
    String PROP_NAME_DMQ_CUR_TTL_MSG_BYTES = "imq.dmq.current_size";

    /*
     * Property names returned in Hashtables for GET_TRANSACTION admin message
     */
    String PROP_NAME_TXN_ID = "txnid";
    String PROP_NAME_TXN_XID = "xid";
    String PROP_NAME_TXN_NUM_MSGS = "nmsgs";
    String PROP_NAME_TXN_NUM_ACKS = "nacks";
    String PROP_NAME_TXN_USER = "user";
    String PROP_NAME_TXN_CLIENTID = "clientid";
    String PROP_NAME_TXN_TIMESTAMP = "timestamp";
    String PROP_NAME_TXN_CONNECTION = "connection";
    String PROP_NAME_TXN_CONNECTION_ID = "connectionid";
    String PROP_NAME_TXN_STATE = "state";

    /*
     * Property names returned in Hashtables in GET_CONNECTIONS admin message
     */
    String PROP_NAME_CXN_CXN_ID = "cxnid";
    String PROP_NAME_CXN_CLIENT_ID = "clientid";
    String PROP_NAME_CXN_HOST = "host";
    String PROP_NAME_CXN_PORT = "port";
    String PROP_NAME_CXN_USER = "user";
    String PROP_NAME_CXN_NUM_PRODUCER = "nproducers";
    String PROP_NAME_CXN_NUM_CONSUMER = "nconsumers";
    String PROP_NAME_CXN_CLIENT_PLATFORM = "clientplatform";
    String PROP_NAME_CXN_SERVICE = "service";

    /*
     * Property names returned in Hashtables in GET_JMX admin message
     */
    String PROP_NAME_JMX_NAME = "name";
    String PROP_NAME_JMX_ACTIVE = "active";
    String PROP_NAME_JMX_URL = "url";

    /*
     * Valid values for broker log level.
     */
    List<String> BKR_LOG_LEVEL_VALID_VALUES = List.of("NONE", "ERROR", "WARNING", "INFO");

    /*
     * Queue flavour property names, as expected by the broker
     */
    String PROP_NAME_QUEUE_FLAVOUR_SINGLE = "single";
    String PROP_NAME_QUEUE_FLAVOUR_FAILOVER = "failover";
    String PROP_NAME_QUEUE_FLAVOUR_ROUNDROBIN = "round-robin";

    /*
     * Valid values for broker log level. Note: Indices for the strings below need to match the array contents.
     */
    List<String> BKR_LIMIT_BEHAV_VALID_VALUES = List.of("FLOW_CONTROL", "REMOVE_OLDEST", "REJECT_NEWEST", "REMOVE_LOW_PRIORITY");
    String LIMIT_BEHAV_FLOW_CONTROL = BKR_LIMIT_BEHAV_VALID_VALUES.get(0);
    String LIMIT_BEHAV_RM_OLDEST = BKR_LIMIT_BEHAV_VALID_VALUES.get(1);
    String LIMIT_BEHAV_REJECT_NEWEST = BKR_LIMIT_BEHAV_VALID_VALUES.get(2);
    String LIMIT_BEHAV_RM_LOW_PRIORITY = BKR_LIMIT_BEHAV_VALID_VALUES.get(3);

    /*
     * Transaction types
     */
    int TXN_LOCAL = 0;
    int TXN_CLUSTER = 1;
    int TXN_REMOTE = 2;
    int TXN_UNKNOWN = -1;

}
