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
 * @(#)MessageType.java	1.31 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

/**
 * This class enumerates all of the JMQ message types for the admin client 
 * and provides some convenience routines
 */
public class MessageType {

    public static final String JMQ_BRIDGE_ADMIN_DEST    = "__JMQBridgeAdmin";

    public static final String JMQ_ADMIN_DEST 	        = "__JMQAdmin";
    public static final String JMQ_MESSAGE_TYPE 	= "JMQMessageType";
    public static final String JMQ_DEST_TYPE 		= "JMQDestType";
    public static final String JMQ_DESTINATION 		= "JMQDestination";
    public static final String JMQ_PROTOCOL_LEVEL 	= "JMQProtocolLevel";
    public static final String JMQ_STATUS 		= "JMQStatus";
    public static final String JMQ_ERROR_STRING 	= "JMQErrorString";
    public static final String JMQ_INSTANCE_NAME 	= "JMQInstanceName";
    public static final String JMQ_RESTART 	        = "JMQRestart";
    public static final String JMQ_KILL 	        = "JMQKill";
    public static final String JMQ_SERVICE_NAME         = "JMQServiceName";
    public static final String JMQ_DURABLE_NAME         = "JMQDurableName";
    public static final String JMQ_CLIENT_ID            = "JMQClientID";
    public static final String JMQ_TRANSACTION_ID       = "JMQTransactionID";
    public static final String JMQ_QUANTITY             = "JMQQuantity";
    public static final String JMQ_CMD                  = "JMQCommand";
    public static final String JMQ_CMDARG               = "JMQCommandArg";
    public static final String JMQ_TARGET               = "JMQTarget";
    public static final String JMQ_TARGET_TYPE          = "JMQTargetType";
    public static final String JMQ_BROKER_ID            = "JMQBrokerID";
    public static final String JMQ_NO_FAILOVER          = "JMQNoFailover";
    public static final String JMQ_TIME                 = "JMQTime";
    public static final String JMQ_SHOW_PARTITION	= "JMQShowPartition";
    public static final String JMQ_LOAD_DESTINATION     = "JMQLoadDestination";
    public static final String JMQ_PROCESS_ACTIVE_CONSUMERS   = "JMQProcessActiveConsumers";

    /**
     * Pause type string (used when pausing a destination)
     * @since 3.5
     */
    public static final String JMQ_DEST_STATE           = "JMQDestState";
    /**
     * type of body for a metrics packet
     * @since 3.5
     */
    public static final String JMQ_BODY_TYPE            = "JMQBodyType";

    /**
     * pause target (JMQServiceName or JMQDestination)
     * @since 3.5
     */
    public static final String JMQ_PAUSE_TARGET         = "JMQPauseTarget";

    /**
     * Connection ID
     * @since 3.5
     */
    public static final String JMQ_CONNECTION_ID         = "JMQConnectionID";

    /**
     * Reset type
     * @since 4.0
     */
    public static final String JMQ_RESET_TYPE           = "JMQResetType";

    /**
     * Reset type - metrics
     * @since 4.0
     */
    public static final String JMQ_METRICS              = "JMQMetrics";

    /**
     * The index of the first message to retrieve for 
     * GET_MESSAGES/GET_MESSAGES_REPLY.
     * @since 3.7ur1
     */
    public static final String JMQ_START_MESSAGE_INDEX	= "JMQStartMessageIndex";

    /**
     * Max number of messages returned in GET_MESSAGES/GET_MESSAGES_REPLY.
     * @since 3.7ur1
     */
    public static final String JMQ_MAX_NUM_MSGS_RETRIEVED = "JMQMaxNumMsgsRetrieved";

    /**
     *  Message ID
     * @since 3.7ur1
     */
    public static final String JMQ_MESSAGE_ID             = "JMQMessageID";

    /**
     * Boolean to indicate if message body should be retrieved.
     * @since 3.7ur1
     */
    public static final String JMQ_GET_MSG_BODY           = "JMQGetMessageBody";

    /**
     * The new value for imq.cluster.brokerlist for UPDATE_CLUSTER_BROKERLIST
     * @since 4.5
     */
    public static final String JMQ_CLUSTER_BROKERLIST           = "JMQClusterBrokerList";

    /**
     * The new value of imq.cluster.masterbroker for CHANGE_CLUSTER_MASTER_BROKER
     * @since 4.5
     */
    public static final String JMQ_CLUSTER_NEW_MASTER_BROKER           = "JMQClusterNewMasterBroker";

    /**
     * The current value of imq.cluster.masterbroker for CHANGE_CLUSTER_MASTER_BROKER
     * @since 4.5
     */
    public static final String JMQ_CLUSTER_OLD_MASTER_BROKER           = "JMQClusterOldMasterBroker";

    /**
     * Set to true if sender is from JMSRA to its managed broker
     * @since 4.5
     */
    public static final String JMQ_JMSRA_MANAGED_BROKER                = "JMQJMSRAManagedBroker";

    /**
     * Set to true if it's a notification from JMSRA 
     * - to be used with protocol CHANGE_CLUSTER_MASTER_BROKER
     * @since 4.5
     */
    public static final String JMQ_JMSRA_NOTIFICATION_ONLY                = "JMQJMSRANotificationOnly";

    public static final String JMQ_MIGRATESTORE_SYNC_TIMEOUT      = "JMQMigrateStoreSyncTimeout";
    public static final String JMQ_MIGRATESTORE_PARTITION         = "JMQMigrateStorePartition";
    public static final String JMQ_NUM_PARTITIONS                 = "JMQNumPartitions";
    public static final String JMQ_MQ_ADDRESS                     = "JMQMQAddress";

    public static final int OK 				= 200;
    public static final int ERROR 			= 500;


    public static final int NULL             		= 0;

    public static final int CREATE_DESTINATION  	= 10;
    public static final int CREATE_DESTINATION_REPLY    = 11;

    public static final int DESTROY_DESTINATION 	= 12;
    public static final int DESTROY_DESTINATION_REPLY   = 13;

    public static final int DESTROY_DURABLE   		= 14;
    public static final int DESTROY_DURABLE_REPLY   	= 15;

    public static final int GET_CONNECTIONS   		= 16;
    public static final int GET_CONNECTIONS_REPLY   	= 17;

    public static final int GET_CONSUMERS   		= 18;
    public static final int GET_CONSUMERS_REPLY   	= 19;

    public static final int GET_DESTINATIONS   		= 20;
    public static final int GET_DESTINATIONS_REPLY   	= 21;

    public static final int GET_DURABLES   		= 22;
    public static final int GET_DURABLES_REPLY   	= 23;

    public static final int GET_LOGS   			= 24;
    public static final int GET_LOGS_REPLY   		= 25;

    public static final int GET_SERVICES   		= 26;
    public static final int GET_SERVICES_REPLY   	= 27;

    public static final int HELLO   			= 28;
    public static final int HELLO_REPLY   		= 29;

    public static final int PAUSE   			= 30;
    public static final int PAUSE_REPLY   		= 31;

    public static final int PURGE_DESTINATION  		= 32;
    public static final int PURGE_DESTINATION_REPLY   	= 33;

    public static final int RESTART   			= 34;
    public static final int RESTART_REPLY   		= 35;

    public static final int RESUME   			= 36;
    public static final int RESUME_REPLY   		= 37;

    public static final int SHUTDOWN   			= 38;
    public static final int SHUTDOWN_REPLY   		= 39;

    public static final int UPDATE_DESTINATION 		= 40;
    public static final int UPDATE_DESTINATION_REPLY   	= 41;

    public static final int UPDATE_PROPERTIES  		= 42;
    public static final int UPDATE_PROPERTIES_REPLY   	= 43;

    public static final int VIEW_LOG   			= 44;
    public static final int VIEW_LOG_REPLY   		= 45;

    public static final int UPDATE_SERVICE 		= 46;
    public static final int UPDATE_SERVICE_REPLY 	= 47;

    public static final int GET_METRICS			= 52;
    public static final int GET_METRICS_REPLY 		= 53;

    public static final int GET_BROKER_PROPS		= 54;
    public static final int GET_BROKER_PROPS_REPLY	= 55;

    public static final int UPDATE_BROKER_PROPS		= 56;
    public static final int UPDATE_BROKER_PROPS_REPLY	= 57;

    public static final int RELOAD_CLUSTER		= 58;
    public static final int RELOAD_CLUSTER_REPLY  	= 59;

    public static final int GET_TRANSACTIONS            = 60;
    public static final int GET_TRANSACTIONS_REPLY 	= 61;

    public static final int COMMIT_TRANSACTION          = 62;
    public static final int COMMIT_TRANSACTION_REPLY    = 63;

    public static final int ROLLBACK_TRANSACTION        = 64;
    public static final int ROLLBACK_TRANSACTION_REPLY  = 65;

    public static final int PURGE_DURABLE   		= 66;
    public static final int PURGE_DURABLE_REPLY   	= 67;

    public static final int COMPACT_DESTINATION		= 68;
    public static final int COMPACT_DESTINATION_REPLY  	= 69;


    public static final int DESTROY_CONNECTION   	= 70;
    public static final int DESTROY_CONNECTION_REPLY   	= 71;

    public static final int DEBUG   			= 72;
    public static final int DEBUG_REPLY   		= 73;

    public static final int QUIESCE_BROKER		= 74;
    public static final int QUIESCE_BROKER_REPLY 	= 75;

    public static final int TAKEOVER_BROKER		= 76;
    public static final int TAKEOVER_BROKER_REPLY 	= 77;

    public static final int GET_CLUSTER			= 78;
    public static final int GET_CLUSTER_REPLY 		= 79;

    public static final int GET_JMX			= 80;
    public static final int GET_JMX_REPLY 		= 81;

    public static final int UNQUIESCE_BROKER		= 82;
    public static final int UNQUIESCE_BROKER_REPLY 	= 83;

    public static final int RESET_BROKER		= 84;
    public static final int RESET_BROKER_REPLY 		= 85;

    public static final int GET_MESSAGES		= 86;
    public static final int GET_MESSAGES_REPLY		= 87;

    public static final int DELETE_MESSAGE		= 88;
    public static final int DELETE_MESSAGE_REPLY	= 89;

    public static final int REPLACE_MESSAGE		= 90;
    public static final int REPLACE_MESSAGE_REPLY	= 91;
    
    public static final int CHECKPOINT_BROKER		= 92;
    public static final int CHECKPOINT_BROKER_REPLY	= 93;

    //support from RA only
    public static final int UPDATE_CLUSTER_BROKERLIST = 94;
    public static final int UPDATE_CLUSTER_BROKERLIST_REPLY = 95;

    public static final int CHANGE_CLUSTER_MASTER_BROKER = 96;
    public static final int CHANGE_CLUSTER_MASTER_BROKER_REPLY = 97;

    public static final int MIGRATESTORE_BROKER = 98;
    public static final int MIGRATESTORE_BROKER_REPLY 	= 99;

    public static final int LAST   			= 100;

    private static final String[] names = {
    	"NULL",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"TBD",
    	"CREATE_DESTINATION",
    	"CREATE_DESTINATION_REPLY",
    	"DESTROY_DESTINATION",
    	"DESTROY_DESTINATION_REPLY",
    	"DESTROY_DURABLE",
    	"DESTROY_DURABLE_REPLY",
    	"GET_CONNECTIONS",
    	"GET_CONNECTIONS_REPLY",
    	"GET_CONSUMERS",
    	"GET_CONSUMERS_REPLY",
    	"GET_DESTINATIONS",
    	"GET_DESTINATIONS_REPLY",
    	"GET_DURABLES",
    	"GET_DURABLES_REPLY",
    	"GET_LOGS",
    	"GET_LOGS_REPLY",
    	"GET_SERVICES",
    	"GET_SERVICES_REPLY",
    	"HELLO",
    	"HELLO_REPLY",
    	"PAUSE",
    	"PAUSE_REPLY",
    	"PURGE_DESTINATION",
    	"PURGE_DESTINATION_REPLY",
    	"RESTART",
    	"RESTART_REPLY",
    	"RESUME",
    	"RESUME_REPLY",
    	"SHUTDOWN",
    	"SHUTDOWN_REPLY",
    	"UPDATE_DESTINATION",
    	"UPDATE_DESTINATION_REPLY",
    	"UPDATE_PROPERTIES",
    	"UPDATE_PROPERTIES_REPLY",
    	"VIEW_LOG",
    	"VIEW_LOG_REPLY",
    	"UPDATE_SERVICE",
    	"UPDATE_SERVICE_REPLY",
    	"TBD",
    	"TBD",
	"TBD",
	"TBD",
    	"GET_METRICS",
    	"GET_METRICS_REPLY",
        "GET_BROKER_PROPS",
        "GET_BROKER_PROPS_REPLY",
        "UPDATE_BROKER_PROPS",
        "UPDATE_BROKER_PROPS_REPLY",
        "RELOAD_CLUSTER",
        "RELOAD_CLUSTER_REPLY",
        "GET_TRANSACTIONS",
        "GET_TRANACTIONS_REPLY",
        "COMMIT_TRANSACTION",
        "COMMIT_TRANSACTION_REPLY",
        "ROLLBACK_TRANSACTION",
        "ROLLBACK_TRANSACTION_REPLY",
        "PURGE_DURABLE",
        "PURGE_DURABLE_REPLY",
        "COMPACT_DESTINATION",
        "COMPACT_DESTINATION_REPLY",
        "DESTROY_CONNECTION",
        "DESTROY_CONNECTION_REPLY",
        "DEBUG",
        "DEBUG_REPLY",
        "QUIESCE_BROKER",
        "QUIESCE_BROKER_REPLY",
        "TAKEOVER_BROKER",
        "TAKEOVER_BROKER_REPLY",
        "GET_CLUSTER",
        "GET_CLUSTER_REPLY",
        "GET_JMX",
        "GET_JMX_REPLY",
    	"UNQUIESCE_BROKER",
    	"UNQUIESCE_BROKER_REPLY",
    	"RESET_BROKER",
    	"RESET_BROKER_REPLY",
    	"GET_MESSAGES",
    	"GET_MESSAGES_REPLY",
	"DELETE_MESSAGE",
	"DELETE_MESSAGE_REPLY",
	"REPLACE_MESSAGE",
	"REPLACE_MESSAGE_REPLY",
	"CHECKPOINT_MESSAGE",
	"CHECKPOINT_MESSAGE_REPLY",
	"UPDATE_CLUSTER_BROKERLIST",
	"UPDATE_CLUSTER_BROKERLIST_REPLY",
	"CHANGE_CLUSTER_MASTER_BROKER",
	"CHANGE_CLUSTER_MASTER_BROKER_REPLY",
        "MIGRATESTORE_BROKER",
        "MIGRATESTORE_BROKER_REPLY",
    	"LAST"
    };

    /**
     * Return a string description of the specified string type
     *
     * @param    n    Type to return description for
     */
    public static String getString(int n) {
	if (n < 0 || n >= LAST) {
	    return "INVALID_STRING";
	}

	return names[n] + "(" + n + ")";
    }

    /**
     * Return the version of the protocol.
     * The returned value can be used in the JMQProtocolLevel property of
     * the HELLO message (101="1.0.1", 80="0.8.0", 218="2.1.8")
     * <P>
     * <I>Returns 350 (3.5.0) in raptor </I>
     * @return int  protocol version
     */
    public static int getProtocolVersion() {
        return 350;
    }
}
