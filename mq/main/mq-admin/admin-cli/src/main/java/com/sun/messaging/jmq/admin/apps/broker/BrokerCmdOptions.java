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

package com.sun.messaging.jmq.admin.apps.broker;

import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;

/**
 * Interface containing constants for command line options, property names and values for the JMS Object Administration
 * utility.
 */
public interface BrokerCmdOptions {

    /*
     * BEGIN OPTION NAMES
     */

    /**
     * Strings defining what the sub command names are
     */
    String CMD_LIST = "list";
    String CMD_PAUSE = "pause";
    String CMD_RESUME = "resume";
    String CMD_SHUTDOWN = "shutdown";
    String CMD_RESTART = "restart";
    String CMD_CREATE = "create";
    String CMD_DESTROY = "destroy";
    String CMD_PURGE = "purge";
    String CMD_UPDATE = "update";
    String CMD_QUERY = "query";
    String CMD_METRICS = "metrics";
    String CMD_RELOAD = "reload";
    String CMD_CHANGEMASTER = "changemaster";
    String CMD_COMMIT = "commit";
    String CMD_ROLLBACK = "rollback";
    String CMD_COMPACT = "compact";
    String CMD_QUIESCE = "quiesce";
    String CMD_TAKEOVER = "takeover";
    String CMD_MIGRATESTORE = "migratestore";
    String CMD_UNQUIESCE = "unquiesce";
    String CMD_EXISTS = ".exists";
    String CMD_GETATTR = ".getattr";
    String CMD_UNGRACEFUL_KILL = "._kill";
    String CMD_PURGEALL = ".purgeall";
    String CMD_DESTROYALL = ".destroyall";
    String CMD_DUMP = "dump";
    String CMD_SEND = "send";
    String CMD_KILL = "kill";
    String CMD_DEBUG = "debug";
    String CMD_RESET = "reset";
    String CMD_CHECKPOINT = "checkpoint";

    /*
     * Arguments to sub commands
     */
    String CMDARG_SERVICE = "svc";
    String CMDARG_BROKER = "bkr";
    String CMDARG_DESTINATION = "dst";
    String CMDARG_DURABLE = "dur";
    String CMDARG_CONNECTION = "cxn";
    String CMDARG_CLUSTER = "cls";
    String CMDARG_TRANSACTION = "txn";
    String CMDARG_JMX_CONNECTOR = "jmx";
    String CMDARG_MSG = "msg";

    /*
     * Array of valid arguments for a given command
     */
    enum CMD_LIST_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION), DURABLE(CMDARG_DURABLE), SERVICE(CMDARG_SERVICE), TRANSACTION(CMDARG_TRANSACTION), CONNECTION(CMDARG_CONNECTION),
        BROKER(CMDARG_BROKER), JMX_CONNECTOR(CMDARG_JMX_CONNECTOR);

        private final String name;

        CMD_LIST_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum CMD_PAUSE_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION), SERVICE(CMDARG_SERVICE), BROKER(CMDARG_BROKER);

        private final String name;

        CMD_PAUSE_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    String[] CMD_RESUME_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_SERVICE, CMDARG_BROKER };

    String[] CMD_SHUTDOWN_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_RESTART_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_CREATE_VALID_CMDARGS = { CMDARG_DESTINATION };

    String[] CMD_DESTROY_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_DURABLE, CMDARG_CONNECTION };

    String[] CMD_DESTROYALL_VALID_CMDARGS = { CMDARG_DESTINATION };

    String[] CMD_PURGE_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_DURABLE };

    String[] CMD_PURGEALL_VALID_CMDARGS = { CMDARG_DESTINATION };

    String[] CMD_UPDATE_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_BROKER, CMDARG_SERVICE, };

    String[] CMD_QUERY_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_SERVICE, CMDARG_BROKER, CMDARG_TRANSACTION, CMDARG_CONNECTION };

    String[] CMD_METRICS_VALID_CMDARGS = { CMDARG_DESTINATION, CMDARG_SERVICE, CMDARG_BROKER };

    String[] CMD_RELOAD_VALID_CMDARGS = { CMDARG_CLUSTER };

    String[] CMD_CHANGEMASTER_VALID_CMDARGS = { CMDARG_CLUSTER };

    String[] CMD_COMMIT_VALID_CMDARGS = { CMDARG_TRANSACTION };

    String[] CMD_ROLLBACK_VALID_CMDARGS = { CMDARG_TRANSACTION };
    String[] CMD_COMPACT_VALID_CMDARGS = { CMDARG_DESTINATION };

    String[] CMD_QUIESCE_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_TAKEOVER_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_MIGRATESTORE_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_UNQUIESCE_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_EXISTS_VALID_CMDARGS = { CMDARG_DESTINATION };

    enum CMD_GETATTR_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION), SERVICE(CMDARG_SERVICE), BROKER(CMDARG_BROKER), TRANSACTION(CMDARG_TRANSACTION), DURABLE(CMDARG_DURABLE);

        private final String name;

        CMD_GETATTR_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    String[] CMD_UNGRACEFUL_KILL_VALID_CMDARGS = { CMDARG_BROKER };

    String[] CMD_RESET_VALID_CMDARGS = { CMDARG_BROKER };

    /*
     * Options - jmqcmd specific
     */
    String OPTION_DEST_TYPE = "-t";
    String OPTION_TARGET_NAME = "-n";
    String OPTION_DEST_NAME = "-d";
    String OPTION_METRIC_INTERVAL = "-int";
    String OPTION_METRIC_TYPE = "-m";
    String OPTION_METRIC_SAMPLES = "-msp";
    String OPTION_SVC_NAME = "-r"; // not used
    String OPTION_CLIENT_ID = "-c";
    String OPTION_BROKER_HOSTPORT = "-b";
    String OPTION_ADMIN_USERID = "-u";
    String OPTION_ADMIN_PASSWD = "-p"; // no longer supported
    String OPTION_ADMIN_PRIVATE_PASSWD = "-pw"; // not used
    String OPTION_ADMIN_PASSFILE = "-passfile";
    String OPTION_TARGET_ATTRS = "-o";
    String OPTION_SYS_PROPS = "-D";
    String OPTION_DEBUG = "-debug";
    String OPTION_ADMIN_DEBUG = "-adebug";
    String OPTION_NOCHECK = "-nocheck";
    String OPTION_DETAIL = "-detail";
    String OPTION_RECV_TIMEOUT = "-rtm";
    String OPTION_NUM_RETRIES = "-rtr";
    String OPTION_SINGLE_TARGET_ATTR = "-attr";
    String OPTION_TEMP_DEST = "-tmp";
    String OPTION_SSL = "-secure";
    String OPTION_SERVICE = "-svn";
    String OPTION_PAUSE_TYPE = "-pst";
    String OPTION_NO_FAILOVER = "-nofailover";
    String OPTION_TIME = "-time";
    String OPTION_RESET_TYPE = "-rst";
    String OPTION_START_MSG_INDEX = "-startMsgIndex";
    String OPTION_MAX_NUM_MSGS_RET = "-maxMsgsRet";
    String OPTION_MSG_ID = "-msgID";
    String OPTION_SHOW_PARTITION = "-showpartition";
    String OPTION_LOAD_DESTINATION = "-load";

    // used with -nocheck for rollback txn
    String OPTION_MSG = "-msg";

    /*
     * Options - 'Standard'
     */
    String OPTION_FORCE = "-f";
    String OPTION_SILENTMODE = "-s";
    String OPTION_INPUTFILE = "-i"; // not used
    String OPTION_SHORT_HELP1 = "-h";
    String OPTION_SHORT_HELP2 = "-help";
    String OPTION_LONG_HELP1 = "-H";
    String OPTION_LONG_HELP2 = "-Help";
    String OPTION_VERSION1 = "-v";
    String OPTION_VERSION2 = "-version";

    /*
     * This is to support the private "-adminkey" option It is used to support authentication when shutting down the broker
     * via the NT services' "Stop" command.
     */
    String OPTION_ADMINKEY = "-adminkey";

    /*
     * END OPTION NAMES
     */

    /*
     * BEGIN PROPERTY NAMES/VALUES
     */

    /**
     * Property name representing what command needs to be executed.
     */
    String PROP_NAME_CMD = "cmdtype";

    /**
     * Property name for the mandatory command argument
     */
    String PROP_NAME_CMDARG = "cmdarg";

    /*
     * Property values for command types.
     */
    String PROP_VALUE_CMD_LIST = CMD_LIST;
    String PROP_VALUE_CMD_PAUSE = CMD_PAUSE;
    String PROP_VALUE_CMD_RESUME = CMD_RESUME;
    String PROP_VALUE_CMD_SHUTDOWN = CMD_SHUTDOWN;
    String PROP_VALUE_CMD_RESTART = CMD_RESTART;
    String PROP_VALUE_CMD_CREATE = CMD_CREATE;
    String PROP_VALUE_CMD_DESTROY = CMD_DESTROY;
    String PROP_VALUE_CMD_PURGE = CMD_PURGE;
    String PROP_VALUE_CMD_UPDATE = CMD_UPDATE;
    String PROP_VALUE_CMD_QUERY = CMD_QUERY;
    String PROP_VALUE_CMD_METRICS = CMD_METRICS;
    String PROP_VALUE_CMD_RELOAD = CMD_RELOAD;
    String PROP_VALUE_CMD_CHANGEMASTER = CMD_CHANGEMASTER;
    String PROP_VALUE_CMD_COMMIT = CMD_COMMIT;
    String PROP_VALUE_CMD_ROLLBACK = CMD_ROLLBACK;
    String PROP_VALUE_CMD_COMPACT = CMD_COMPACT;
    String PROP_VALUE_CMD_QUIESCE = CMD_QUIESCE;
    String PROP_VALUE_CMD_TAKEOVER = CMD_TAKEOVER;
    String PROP_VALUE_CMD_MIGRATESTORE = CMD_MIGRATESTORE;
    String PROP_VALUE_CMD_UNQUIESCE = CMD_UNQUIESCE;
    String PROP_VALUE_CMD_EXISTS = CMD_EXISTS;
    String PROP_VALUE_CMD_GETATTR = CMD_GETATTR;
    String PROP_VALUE_CMD_UNGRACEFUL_KILL = CMD_UNGRACEFUL_KILL;
    String PROP_VALUE_CMD_PURGEALL = CMD_PURGEALL;
    String PROP_VALUE_CMD_DESTROYALL = CMD_DESTROYALL;
    String PROP_VALUE_CMD_DUMP = CMD_DUMP;
    String PROP_VALUE_CMD_SEND = CMD_SEND;
    String PROP_VALUE_CMD_KILL = CMD_KILL;
    String PROP_VALUE_CMD_DEBUG = CMD_DEBUG;
    String PROP_VALUE_CMD_RESET = CMD_RESET;
    String PROP_VALUE_CMD_CHECKPOINT = CMD_CHECKPOINT;

    /*
     * Property names for service attributes
     */
    String PROP_NAME_SVC_PORT = "port";
    String PROP_NAME_SVC_MIN_THREADS = "minThreads";
    String PROP_NAME_SVC_MAX_THREADS = "maxThreads";

    /*
     * Property name for destination type option
     */
    String PROP_NAME_OPTION_DEST_TYPE = "destType";

    /*
     * Destination types property values
     */
    String PROP_VALUE_DEST_TYPE_TOPIC = "t";
    String PROP_VALUE_DEST_TYPE_QUEUE = "q";

    /*
     * Property name for queue msg delivery model
     */
    String PROP_NAME_QUEUE_FLAVOUR = "queueDeliveryPolicy";

    /*
     * Property names for new destination attributes in MQ3.5
     */
    String PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT = "maxNumBackupConsumers";
    String PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT = "maxNumActiveConsumers";
    String PROP_NAME_IS_LOCAL_DEST = "isLocalOnly";
    String PROP_NAME_LIMIT_BEHAVIOUR = "limitBehavior";
    String PROP_NAME_LOCAL_DELIVERY_PREF = "localDeliveryPreferred";
    String PROP_NAME_CONSUMER_FLOW_LIMIT = "consumerFlowLimit";
    String PROP_NAME_MAX_PRODUCERS = "maxNumProducers";

    /*
     * Property names for new destintion attributes introduced in MQ 3.6
     */
    String PROP_NAME_USE_DMQ = "useDMQ";

    /*
     * Property names for new destintion attributes introduced in MQ 4.2
     */
    String PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED = "validateXMLSchemaEnabled";
    String PROP_NAME_XML_SCHEMA_URI_LIST = "XMLSchemaURIList";
    String PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE = "reloadXMLSchemaOnFailure";

    /*
     * Queue flavour property values
     */
    String PROP_VALUE_QUEUE_FLAVOUR_SINGLE = "s";
    String PROP_VALUE_QUEUE_FLAVOUR_FAILOVER = "f";
    String PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN = "r";

    /*
     * String values for metric type
     */
    String PROP_VALUE_METRICS_TOTALS = "ttl";
    String PROP_VALUE_METRICS_RATES = "rts";
    String PROP_VALUE_METRICS_CONNECTIONS = "cxn";
    String PROP_VALUE_METRICS_CONSUMER = "con";
    String PROP_VALUE_METRICS_DISK = "dsk";
    String PROP_VALUE_METRICS_REMOVE = "rem";

    /*
     * String values for pause type
     */
    String PROP_VALUE_PAUSETYPE_PRODUCERS = "PRODUCERS";
    String PROP_VALUE_PAUSETYPE_CONSUMERS = "CONSUMERS";
    String PROP_VALUE_PAUSETYPE_ALL = "ALL";

    /*
     * String values for reset type
     */
    String PROP_VALUE_RESETTYPE_METRICS = "METRICS";
    String PROP_VALUE_RESETTYPE_ALL = "ALL";

    /*
     * String value for migratestore partition
     */
    String PROP_NAME_OPTION_PARTITION = "partition";

    /*
     * Property names for Queue optional attributes (for create and update)
     */
    String PROP_NAME_OPTION_MAX_MESG_BYTE = "maxTotalMsgBytes";
    String PROP_NAME_OPTION_MAX_MESG = "maxNumMsgs";

    /*
     * Property name for Queue / Topic optional attribute (for create and update)
     */
    String PROP_NAME_OPTION_MAX_PER_MESG_SIZE = "maxBytesPerMsg";

    /*
     * Property names for Queue attributes (for getattr hidden subcommand)
     */
    String PROP_NAME_OPTION_CUR_MESG_BYTE = "curTotalMsgBytes";
    String PROP_NAME_OPTION_CUR_MESG = "curNumMsgs";
    String PROP_NAME_OPTION_CUR_UNACK_MESG = "curNumUnackMsgs";
    String PROP_NAME_OPTION_CUR_PRODUCERS = "curNumProducers";
    String PROP_NAME_OPTION_CUR_A_CONSUMERS = "curNumActiveConsumers";
    String PROP_NAME_OPTION_CUR_B_CONSUMERS = "curNumBackupConsumers";

    /*
     * Property names for txn attributes (for getattr hidden subcommand)
     */
    String PROP_NAME_OPTION_CUR_TXNS = "curNumTxns";
    String PROP_NAME_OPTION_ALL_TXNS = "allTxns";

    String PROP_NAME_OPTION_TARGET_NAME = "targetName";
    String PROP_NAME_OPTION_DEST_NAME = "destName";
    String PROP_NAME_OPTION_METRIC_INTERVAL = "metricInterval";
    String PROP_NAME_OPTION_METRIC_TYPE = "metricType";
    String PROP_NAME_OPTION_SVC_NAME = "serviceName";
    String PROP_NAME_OPTION_CLIENT_ID = "clientID";
    String PROP_NAME_OPTION_BROKER_HOSTPORT = "brokerHostPort";
    String PROP_NAME_OPTION_ADMIN_USERID = "adminUser";
    String PROP_NAME_OPTION_ADMIN_PASSWD = "adminPasswd";
    String PROP_NAME_OPTION_ADMIN_PASSFILE = "adminPassfile";
    String PROP_NAME_OPTION_TARGET_ATTRS = "target.attrs";
    String PROP_NAME_OPTION_SYS_PROPS = "sys.props";
    String PROP_NAME_OPTION_SINGLE_TARGET_ATTR = "single.target.attr";
    String PROP_NAME_OPTION_TEMP_DEST = "tempDest";
    String PROP_VALUE_OPTION_TEMP_DEST = "true";

    String PROP_NAME_OPTION_SHOW_PARTITION = "showPartition";
    String PROP_VALUE_OPTION_SHOW_PARTITION = "true";

    String PROP_NAME_OPTION_LOAD_DESTINATION = "loadDestination";
    String PROP_VALUE_OPTION_LOAD_DESTINATION = "true";

    String PROP_NAME_OPTION_MSG = "msg";
    String PROP_VALUE_OPTION_MSG = "true";

    String PROP_NAME_OPTION_SSL = "secure";
    String PROP_VALUE_OPTION_SSL = "true";

    String PROP_NAME_OPTION_SERVICE = "service";

    String PROP_NAME_OPTION_PAUSE_TYPE = "pauseType";

    String PROP_NAME_OPTION_NO_FAILOVER = "noFailover";
    String PROP_VALUE_OPTION_NO_FAILOVER = "true";

    String PROP_NAME_OPTION_TIME = "time";

    String PROP_NAME_OPTION_METRIC_SAMPLES = "metricSamples";

    String PROP_NAME_OPTION_DEBUG = "debug";
    String PROP_VALUE_OPTION_DEBUG = "true";

    String PROP_NAME_OPTION_ADMIN_DEBUG = "adebug";
    String PROP_VALUE_OPTION_ADMIN_DEBUG = "true";

    String PROP_NAME_OPTION_NOCHECK = "nocheck";
    String PROP_VALUE_OPTION_NOCHECK = "true";

    String PROP_NAME_OPTION_DETAIL = "detail";
    String PROP_VALUE_OPTION_DETAIL = "true";

    String PROP_NAME_OPTION_RECV_TIMEOUT = "receiveTimeout";
    String PROP_NAME_OPTION_NUM_RETRIES = "numRetries";

    String PROP_NAME_OPTION_FORCE = "force";
    String PROP_VALUE_OPTION_FORCE = "true";

    String PROP_NAME_OPTION_SILENTMODE = "silent";
    String PROP_VALUE_OPTION_SILENTMODE = "true";

    String PROP_NAME_OPTION_INPUTFILE = "inputfile";

    String PROP_NAME_OPTION_RESET_TYPE = "resetType";

    String PROP_NAME_OPTION_START_MSG_INDEX = "startMsgIndex";
    String PROP_NAME_OPTION_MAX_NUM_MSGS_RET = "maxMsgsRet";
    String PROP_NAME_OPTION_MSG_ID = "msgID";

    /*
     * Property name and value for "-adminkey" option. This is to support the private "-adminkey" option It is used to
     * support authentication when shutting down the broker via the NT services' "Stop" command.
     */
    String PROP_NAME_OPTION_ADMINKEY = "adminkey";
    String PROP_VALUE_OPTION_ADMINKEY = "true";

    /*
     * These strings are of the form name=value. They are needed because the subcommands for jmqcmd require the following
     * actions: 1. signal error if no subcommand args are specified e.g. specify jmqcmd pause without specifying 'svc' or
     * 'bkr' 2. add property name/value pair for the arg specified e.g. jmqcmd pause svc should add the property pair:
     * cmdarg=svc 3. add property name/value pair for the subcommand specified e.g. jmqcmd pause svc should add the property
     * pair: cmdtype=pause
     *
     * 1 and 2 are taken care of by the OPTION_VALUE_NEXT_ARG option type. For 3, we needed to define a field in the
     * OptionDesc class that is basically a name/value pair that you want set whenever the option is used. The strings that
     * follow define the name/value pairs for those options. They all of the form: cmdtype=<subcommand>
     */
    String PROP_NAMEVALUE_CMD_LIST = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_LIST;
    String PROP_NAMEVALUE_CMD_PAUSE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_PAUSE;
    String PROP_NAMEVALUE_CMD_RESUME = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_RESUME;
    String PROP_NAMEVALUE_CMD_SHUTDOWN = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_SHUTDOWN;
    String PROP_NAMEVALUE_CMD_RESTART = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_RESTART;
    String PROP_NAMEVALUE_CMD_CREATE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_CREATE;
    String PROP_NAMEVALUE_CMD_DESTROY = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_DESTROY;
    String PROP_NAMEVALUE_CMD_PURGE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_PURGE;
    String PROP_NAMEVALUE_CMD_UPDATE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_UPDATE;
    String PROP_NAMEVALUE_CMD_QUERY = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_QUERY;
    String PROP_NAMEVALUE_CMD_METRICS = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_METRICS;
    String PROP_NAMEVALUE_CMD_RELOAD = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_RELOAD;
    String PROP_NAMEVALUE_CMD_CHANGEMASTER = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_CHANGEMASTER;
    String PROP_NAMEVALUE_CMD_COMMIT = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_COMMIT;
    String PROP_NAMEVALUE_CMD_ROLLBACK = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_ROLLBACK;
    String PROP_NAMEVALUE_CMD_COMPACT = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_COMPACT;
    String PROP_NAMEVALUE_CMD_QUIESCE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_QUIESCE;
    String PROP_NAMEVALUE_CMD_TAKEOVER = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_TAKEOVER;
    String PROP_NAMEVALUE_CMD_MIGRATESTORE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_MIGRATESTORE;
    String PROP_NAMEVALUE_CMD_UNQUIESCE = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_UNQUIESCE;
    String PROP_NAMEVALUE_CMD_EXISTS = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_EXISTS;
    String PROP_NAMEVALUE_CMD_GETATTR = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_GETATTR;
    String PROP_NAMEVALUE_CMD_UNGRACEFUL_KILL = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_UNGRACEFUL_KILL;
    String PROP_NAMEVALUE_CMD_PURGEALL = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_PURGEALL;
    String PROP_NAMEVALUE_CMD_DESTROYALL = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_DESTROYALL;
    String PROP_NAMEVALUE_CMD_DUMP = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_DUMP;
    String PROP_NAMEVALUE_CMD_SEND = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_SEND;
    String PROP_NAMEVALUE_CMD_KILL = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_KILL;
    String PROP_NAMEVALUE_CMD_DEBUG = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_DEBUG;
    String PROP_NAMEVALUE_CMD_RESET = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_RESET;
    String PROP_NAMEVALUE_CMD_CHECKPOINT = PROP_NAME_CMD + "=" + PROP_VALUE_CMD_CHECKPOINT;

    /*
     * Arrays containing valid property names (as passed in via -o or -attr) for various create/update/getattr operations
     */
    String[] CREATE_DST_QUEUE_VALID_ATTRS = { PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG,
            PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, PROP_NAME_IS_LOCAL_DEST, PROP_NAME_LIMIT_BEHAVIOUR,
            PROP_NAME_LOCAL_DELIVERY_PREF, PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_USE_DMQ, PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
            PROP_NAME_XML_SCHEMA_URI_LIST, PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] CREATE_DST_QUEUE_DEPRECATED_ATTRS = { PROP_NAME_QUEUE_FLAVOUR };

    String[] CREATE_DST_TOPIC_VALID_ATTRS = { PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG,
            PROP_NAME_IS_LOCAL_DEST, PROP_NAME_LIMIT_BEHAVIOUR, PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_USE_DMQ,
            PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED, PROP_NAME_XML_SCHEMA_URI_LIST, PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] CREATE_ONLY_DST_ATTRS = {
            /*
             * only one attr is create only for destinations
             */
            PROP_NAME_IS_LOCAL_DEST };

    String[] CHANGEMASTER_VALID_ATTRS = { BrokerConstants.PROP_NAME_BKR_CLS_CFG_SVR, };

    String[] MIGRATESTORE_VALID_ATTRS = { PROP_NAME_OPTION_PARTITION, };

    String[] UPDATE_BKR_VALID_ATTRS = { BrokerConstants.PROP_NAME_BKR_PRIMARY_PORT, BrokerConstants.PROP_NAME_BKR_AUTOCREATE_TOPIC,
            BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE, BrokerConstants.PROP_NAME_BKR_LOG_LEVEL, BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
            BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
            /*
             * BrokerConstants.PROP_NAME_BKR_METRIC_INTERVAL,
             */
            BrokerConstants.PROP_NAME_BKR_MAX_MSG, BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES, BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES,
            BrokerConstants.PROP_NAME_BKR_CLS_URL, BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
            BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS, BrokerConstants.PROP_NAME_BKR_LOG_DEAD_MSGS,
            BrokerConstants.PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY, BrokerConstants.PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ };

    String[] UPDATE_BKR_DEPRECATED_ATTRS = { BrokerConstants.PROP_NAME_BKR_QUEUE_DELIVERY_POLICY };

    String[] UPDATE_DST_QUEUE_VALID_ATTRS = { PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG,
            PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, PROP_NAME_LIMIT_BEHAVIOUR, PROP_NAME_LOCAL_DELIVERY_PREF,
            PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_USE_DMQ, PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED, PROP_NAME_XML_SCHEMA_URI_LIST,
            PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] UPDATE_DST_TOPIC_VALID_ATTRS = { PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG,
            PROP_NAME_LIMIT_BEHAVIOUR, PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_USE_DMQ, PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
            PROP_NAME_XML_SCHEMA_URI_LIST, PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] UPDATE_SVC_VALID_ATTRS = { PROP_NAME_SVC_PORT, PROP_NAME_SVC_MIN_THREADS, PROP_NAME_SVC_MAX_THREADS };

    String[] METRIC_TYPE_VALID_VALUES = { PROP_VALUE_METRICS_TOTALS, PROP_VALUE_METRICS_RATES, PROP_VALUE_METRICS_CONNECTIONS };

    String[] METRIC_DST_TYPE_VALID_VALUES = { PROP_VALUE_METRICS_TOTALS, PROP_VALUE_METRICS_RATES, PROP_VALUE_METRICS_CONSUMER,
            PROP_VALUE_METRICS_DISK
            /*
             * PROP_VALUE_METRICS_REMOVE
             */
    };

    String[] GETATTR_DST_QUEUE_VALID_ATTRS = {
            /*
             * PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG,
             * PROP_NAME_OPTION_CUR_MESG_BYTE, PROP_NAME_OPTION_CUR_MESG, PROP_NAME_OPTION_CUR_PRODUCERS
             */

            /*
             * Complete list:
             */
            PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG, PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT,
            PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, PROP_NAME_IS_LOCAL_DEST, PROP_NAME_LIMIT_BEHAVIOUR, PROP_NAME_LOCAL_DELIVERY_PREF,
            PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_OPTION_CUR_MESG_BYTE, PROP_NAME_OPTION_CUR_MESG, PROP_NAME_OPTION_CUR_UNACK_MESG,
            PROP_NAME_OPTION_CUR_PRODUCERS, PROP_NAME_OPTION_CUR_A_CONSUMERS, PROP_NAME_OPTION_CUR_B_CONSUMERS, PROP_NAME_USE_DMQ,
            PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED, PROP_NAME_XML_SCHEMA_URI_LIST, PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] GETATTR_DST_TOPIC_VALID_ATTRS = {
            /*
             * PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_CUR_MESG_BYTE, PROP_NAME_OPTION_CUR_MESG,
             * PROP_NAME_OPTION_CUR_PRODUCERS
             */

            /*
             * Complete list:
             */
            PROP_NAME_OPTION_MAX_MESG_BYTE, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG, PROP_NAME_IS_LOCAL_DEST, PROP_NAME_LIMIT_BEHAVIOUR,
            PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_MAX_PRODUCERS, PROP_NAME_OPTION_CUR_MESG_BYTE, PROP_NAME_OPTION_CUR_MESG, PROP_NAME_OPTION_CUR_UNACK_MESG,
            PROP_NAME_OPTION_CUR_PRODUCERS, PROP_NAME_OPTION_CUR_A_CONSUMERS, PROP_NAME_USE_DMQ, PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
            PROP_NAME_XML_SCHEMA_URI_LIST, PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE };

    String[] GETATTR_SVC_VALID_ATTRS = { PROP_NAME_SVC_PORT, PROP_NAME_SVC_MIN_THREADS, PROP_NAME_SVC_MAX_THREADS };

    String[] GETATTR_TXN_VALID_ATTRS = { PROP_NAME_OPTION_CUR_TXNS, PROP_NAME_OPTION_ALL_TXNS };

    String[] GETATTR_BKR_VALID_ATTRS = { BrokerConstants.PROP_NAME_BKR_PRIMARY_PORT, BrokerConstants.PROP_NAME_BKR_AUTOCREATE_TOPIC,
            BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE, BrokerConstants.PROP_NAME_BKR_MAX_MSG, BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES,
            BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES, BrokerConstants.PROP_NAME_BKR_CUR_MSG, BrokerConstants.PROP_NAME_BKR_CUR_TTL_MSG_BYTES
            /*
             * BrokerConstants.PROP_NAME_BKR_PRODUCT_VERSION, BrokerConstants.PROP_NAME_BKR_LOG_LEVEL,
             * BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE, BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
             * BrokerConstants.PROP_NAME_BKR_METRIC_INTERVAL, BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES,
             * BrokerConstants.PROP_NAME_BKR_CLS_URL
             */
    };

    String[] PAUSE_DST_TYPE_VALID_VALUES = { PROP_VALUE_PAUSETYPE_PRODUCERS, PROP_VALUE_PAUSETYPE_CONSUMERS, PROP_VALUE_PAUSETYPE_ALL };

    String[] RESET_BKR_TYPE_VALID_VALUES = { PROP_VALUE_RESETTYPE_METRICS, PROP_VALUE_RESETTYPE_ALL };

    /*
     * Destination attributes that can take an unlimited (-1) value
     */
    String[] DEST_ATTRS_UNLIMITED = { PROP_NAME_CONSUMER_FLOW_LIMIT, PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT,
            PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, PROP_NAME_OPTION_MAX_MESG, PROP_NAME_MAX_PRODUCERS, PROP_NAME_OPTION_MAX_MESG_BYTE };

    /*
     * Destination attributes that can take an unlimited (-1) value AND need to be converted over if 0 was specified.
     */
    String[] DEST_ATTRS_UNLIMITED_CONV = { PROP_NAME_OPTION_MAX_PER_MESG_SIZE, PROP_NAME_OPTION_MAX_MESG, PROP_NAME_OPTION_MAX_MESG_BYTE

    };

    /*
     * Broker attributes that can take an unlimited (-1) value
     */
    String[] BKR_ATTRS_UNLIMITED = { BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
            BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS, BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
            BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL, BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES, BrokerConstants.PROP_NAME_BKR_MAX_MSG,
            BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES };

    /*
     * Broker attributes that can take an unlimited (-1) value AND need to be converted over if 0 was specified.
     */
    String[] BKR_ATTRS_UNLIMITED_CONV = { BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE, BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
            BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES, BrokerConstants.PROP_NAME_BKR_MAX_MSG, BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES };

    /*
     * This is the property name for the admin password that is stored in a passfile.
     */
    String PROP_NAME_PASSFILE_PASSWD = "imq.imqcmd.password";
    String PROP_NAME_KEYSTORE_PASSWD = "imq.keystore.password";

    /*
     * END PROPERTY NAMES/VALUES
     */

    long DEFAULT_METRIC_INTERVAL = 5;
    long DEFAULT_SHUTDOWN_WAIT_INTERVAL = 100;
}
