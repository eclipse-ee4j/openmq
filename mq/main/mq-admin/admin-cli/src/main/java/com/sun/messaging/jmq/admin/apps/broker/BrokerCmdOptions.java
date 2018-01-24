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
 * @(#)BrokerCmdOptions.java	1.82 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;

/**
 * Interface containing constants for command line options,
 * property names and values for the JMS Object Administration
 * utility.
 */
public interface BrokerCmdOptions  {

    /*
     * BEGIN OPTION NAMES
     */

    /**
     * Strings defining what the sub command names are
     */
    public static String CMD_LIST			= "list";
    public static String CMD_PAUSE			= "pause";
    public static String CMD_RESUME			= "resume";
    public static String CMD_SHUTDOWN			= "shutdown";
    public static String CMD_RESTART			= "restart";
    public static String CMD_CREATE			= "create";
    public static String CMD_DESTROY			= "destroy";
    public static String CMD_PURGE			= "purge";
    public static String CMD_UPDATE			= "update";
    public static String CMD_QUERY			= "query";
    public static String CMD_METRICS			= "metrics";
    public static String CMD_RELOAD			= "reload";
    public static String CMD_CHANGEMASTER  = "changemaster";
    public static String CMD_COMMIT			= "commit";
    public static String CMD_ROLLBACK			= "rollback";
    public static String CMD_COMPACT			= "compact";
    public static String CMD_QUIESCE			= "quiesce";
    public static String CMD_TAKEOVER			= "takeover";
    public static String CMD_MIGRATESTORE	    = "migratestore";
    public static String CMD_UNQUIESCE			= "unquiesce";
    public static String CMD_EXISTS			= ".exists";
    public static String CMD_GETATTR			= ".getattr";
    public static String CMD_UNGRACEFUL_KILL		= "._kill";
    public static String CMD_PURGEALL			= ".purgeall";
    public static String CMD_DESTROYALL			= ".destroyall";
    public static String CMD_DUMP			= "dump";
    public static String CMD_SEND			= "send";
    public static String CMD_KILL			= "kill";
    public static String CMD_DEBUG			= "debug";
    public static String CMD_RESET			= "reset";
    public static String CMD_CHECKPOINT	    = "checkpoint";

    /*
     * Arguments to sub commands
     */
    public static String CMDARG_SERVICE			= "svc";
    public static String CMDARG_BROKER			= "bkr";
    public static String CMDARG_DESTINATION		= "dst";
    public static String CMDARG_DURABLE			= "dur";
    public static String CMDARG_CONNECTION		= "cxn";
    public static String CMDARG_CLUSTER			= "cls";
    public static String CMDARG_TRANSACTION		= "txn";
    public static String CMDARG_JMX_CONNECTOR		= "jmx";
    public static String CMDARG_MSG			= "msg";

    /*
     * Array of valid arguments for a given command
     */
    public static enum CMD_LIST_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION), 
        DURABLE(CMDARG_DURABLE),
        SERVICE(CMDARG_SERVICE),
        TRANSACTION(CMDARG_TRANSACTION),
        CONNECTION(CMDARG_CONNECTION),
        BROKER(CMDARG_BROKER),
        JMX_CONNECTOR(CMDARG_JMX_CONNECTOR);
        
        private final String name;
        private CMD_LIST_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    };

    public static enum CMD_PAUSE_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION),
        SERVICE(CMDARG_SERVICE),
        BROKER(CMDARG_BROKER);

        private final String name;
        private CMD_PAUSE_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    };

    public static String[] CMD_RESUME_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_SERVICE,
							    CMDARG_BROKER
							  };

    public static String[] CMD_SHUTDOWN_VALID_CMDARGS	= {
							    CMDARG_BROKER
							  };

    public static String[] CMD_RESTART_VALID_CMDARGS	= {
							    CMDARG_BROKER
							  };

    public static String[] CMD_CREATE_VALID_CMDARGS	= {
							    CMDARG_DESTINATION
							  };

    public static String[] CMD_DESTROY_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_DURABLE,
							    CMDARG_CONNECTION
							  };

    public static String[] CMD_DESTROYALL_VALID_CMDARGS	= {
							    CMDARG_DESTINATION
							  };

    public static String[] CMD_PURGE_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_DURABLE
							  };

    public static String[] CMD_PURGEALL_VALID_CMDARGS	= {
							    CMDARG_DESTINATION
							  };

    public static String[] CMD_UPDATE_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_BROKER,
							    CMDARG_SERVICE,
							  };

    public static String[] CMD_QUERY_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_SERVICE,
							    CMDARG_BROKER,
							    CMDARG_TRANSACTION,
							    CMDARG_CONNECTION
							  };

    public static String[] CMD_METRICS_VALID_CMDARGS	= {
							    CMDARG_DESTINATION,
							    CMDARG_SERVICE,
							    CMDARG_BROKER
							  };

    public static String[] CMD_RELOAD_VALID_CMDARGS	= {
							    CMDARG_CLUSTER
							  };

    public static String[] CMD_CHANGEMASTER_VALID_CMDARGS	= {
							    CMDARG_CLUSTER
							  };

    public static String[] CMD_COMMIT_VALID_CMDARGS	= {
							    CMDARG_TRANSACTION
                                                        	};

    public static String[] CMD_ROLLBACK_VALID_CMDARGS	= {
							    CMDARG_TRANSACTION
                                                        	};
    public static String[] CMD_COMPACT_VALID_CMDARGS	= {
							    CMDARG_DESTINATION
                                                        	};

    public static String[] CMD_QUIESCE_VALID_CMDARGS	= {
							    CMDARG_BROKER
                                                        	};

    public static String[] CMD_TAKEOVER_VALID_CMDARGS	= {
							    CMDARG_BROKER
                                                        	};

    public static String[] CMD_MIGRATESTORE_VALID_CMDARGS	= {
							    CMDARG_BROKER
                                                        	};

    public static String[] CMD_UNQUIESCE_VALID_CMDARGS	= {
							    CMDARG_BROKER
                                                        	};

    public static String[] CMD_EXISTS_VALID_CMDARGS	= {
							    CMDARG_DESTINATION
							  };

    public static enum CMD_GETATTR_VALID_CMDARGS {
        DESTINATION(CMDARG_DESTINATION),
        SERVICE(CMDARG_SERVICE),
        BROKER(CMDARG_BROKER),
        TRANSACTION(CMDARG_TRANSACTION),
        DURABLE(CMDARG_DURABLE);

        private final String name;
        private CMD_GETATTR_VALID_CMDARGS(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    };

    public static String[] CMD_UNGRACEFUL_KILL_VALID_CMDARGS  = {
                                                          	  CMDARG_BROKER
                                                          	};

    public static String[] CMD_RESET_VALID_CMDARGS	= {
							    CMDARG_BROKER
                                                        	};

    /*
     * Options - jmqcmd specific
     */
    public static String OPTION_DEST_TYPE		= "-t";
    public static String OPTION_TARGET_NAME		= "-n";
    public static String OPTION_DEST_NAME		= "-d";
    public static String OPTION_METRIC_INTERVAL		= "-int";
    public static String OPTION_METRIC_TYPE		= "-m";
    public static String OPTION_METRIC_SAMPLES		= "-msp";
    public static String OPTION_SVC_NAME		= "-r"; // not used
    public static String OPTION_CLIENT_ID		= "-c";
    public static String OPTION_BROKER_HOSTPORT		= "-b";
    public static String OPTION_ADMIN_USERID		= "-u";
    public static String OPTION_ADMIN_PASSWD		= "-p";  // no longer supported
    public static String OPTION_ADMIN_PRIVATE_PASSWD	= "-pw"; // not used
    public static String OPTION_ADMIN_PASSFILE		= "-passfile";
    public static String OPTION_TARGET_ATTRS		= "-o";
    public static String OPTION_SYS_PROPS		= "-D";
    public static String OPTION_DEBUG			= "-debug";
    public static String OPTION_ADMIN_DEBUG		= "-adebug";
    public static String OPTION_NOCHECK			= "-nocheck";
    public static String OPTION_DETAIL			= "-detail";
    public static String OPTION_RECV_TIMEOUT		= "-rtm";
    public static String OPTION_NUM_RETRIES		= "-rtr";
    public static String OPTION_SINGLE_TARGET_ATTR	= "-attr";
    public static String OPTION_TEMP_DEST		= "-tmp";
    public static String OPTION_SSL			= "-secure";
    public static String OPTION_SERVICE			= "-svn";
    public static String OPTION_PAUSE_TYPE		= "-pst";
    public static String OPTION_NO_FAILOVER		= "-nofailover";
    public static String OPTION_TIME			= "-time";
    public static String OPTION_RESET_TYPE		= "-rst";
    public static String OPTION_START_MSG_INDEX		= "-startMsgIndex";
    public static String OPTION_MAX_NUM_MSGS_RET	= "-maxMsgsRet";
    public static String OPTION_MSG_ID			= "-msgID";
    public static String OPTION_SHOW_PARTITION	        = "-showpartition";
    public static String OPTION_LOAD_DESTINATION        = "-load";

    //used with -nocheck for rollback txn 
    public static String OPTION_MSG                     = "-msg";

    /*
     * Options - 'Standard'
     */
    public static String OPTION_FORCE			= "-f";
    public static String OPTION_SILENTMODE		= "-s";
    public static String OPTION_INPUTFILE		= "-i"; // not used
    public static final String OPTION_SHORT_HELP1	= "-h";
    public static final String OPTION_SHORT_HELP2	= "-help";
    public static final String OPTION_LONG_HELP1	= "-H";
    public static final String OPTION_LONG_HELP2	= "-Help";
    public static final String OPTION_VERSION1		= "-v";
    public static final String OPTION_VERSION2		= "-version";

    /*
     * This is to support the private "-adminkey" option
     * It is used to support authentication when shutting down the
     * broker via the NT services' "Stop" command.
     */
    public static final String OPTION_ADMINKEY		= "-adminkey";

    /*
     * END OPTION NAMES
     */

    /*
     * BEGIN PROPERTY NAMES/VALUES
     */

    /**
     * Property name representing what command
     * needs to be executed.
     */
    public static String PROP_NAME_CMD			= "cmdtype";

    /**
     * Property name for the mandatory command argument
     */
    public static String PROP_NAME_CMDARG		= "cmdarg";

    /*
     * Property values for command types.
     */
    public static String PROP_VALUE_CMD_LIST		= CMD_LIST;
    public static String PROP_VALUE_CMD_PAUSE		= CMD_PAUSE;
    public static String PROP_VALUE_CMD_RESUME		= CMD_RESUME;
    public static String PROP_VALUE_CMD_SHUTDOWN	= CMD_SHUTDOWN;
    public static String PROP_VALUE_CMD_RESTART		= CMD_RESTART;
    public static String PROP_VALUE_CMD_CREATE		= CMD_CREATE;
    public static String PROP_VALUE_CMD_DESTROY		= CMD_DESTROY;
    public static String PROP_VALUE_CMD_PURGE		= CMD_PURGE;
    public static String PROP_VALUE_CMD_UPDATE		= CMD_UPDATE;
    public static String PROP_VALUE_CMD_QUERY		= CMD_QUERY;
    public static String PROP_VALUE_CMD_METRICS		= CMD_METRICS;
    public static String PROP_VALUE_CMD_RELOAD		= CMD_RELOAD;
    public static String PROP_VALUE_CMD_CHANGEMASTER = CMD_CHANGEMASTER;
    public static String PROP_VALUE_CMD_COMMIT		= CMD_COMMIT;
    public static String PROP_VALUE_CMD_ROLLBACK	= CMD_ROLLBACK;
    public static String PROP_VALUE_CMD_COMPACT		= CMD_COMPACT;
    public static String PROP_VALUE_CMD_QUIESCE		= CMD_QUIESCE;
    public static String PROP_VALUE_CMD_TAKEOVER	= CMD_TAKEOVER;
    public static String PROP_VALUE_CMD_MIGRATESTORE = CMD_MIGRATESTORE;
    public static String PROP_VALUE_CMD_UNQUIESCE	= CMD_UNQUIESCE;
    public static String PROP_VALUE_CMD_EXISTS		= CMD_EXISTS;
    public static String PROP_VALUE_CMD_GETATTR		= CMD_GETATTR;
    public static String PROP_VALUE_CMD_UNGRACEFUL_KILL	= CMD_UNGRACEFUL_KILL;
    public static String PROP_VALUE_CMD_PURGEALL	= CMD_PURGEALL;
    public static String PROP_VALUE_CMD_DESTROYALL	= CMD_DESTROYALL;
    public static String PROP_VALUE_CMD_DUMP		= CMD_DUMP;
    public static String PROP_VALUE_CMD_SEND		= CMD_SEND;
    public static String PROP_VALUE_CMD_KILL		= CMD_KILL;
    public static String PROP_VALUE_CMD_DEBUG		= CMD_DEBUG;
    public static String PROP_VALUE_CMD_RESET		= CMD_RESET;
    public static String PROP_VALUE_CMD_CHECKPOINT		= CMD_CHECKPOINT;

    /*
     * Property names for service attributes
     */
    public static String PROP_NAME_SVC_PORT		= "port";
    public static String PROP_NAME_SVC_MIN_THREADS	= "minThreads";
    public static String PROP_NAME_SVC_MAX_THREADS	= "maxThreads";


    /*
     * Property name for destination type option
     */
    public static String PROP_NAME_OPTION_DEST_TYPE	= "destType";

    /*
     * Destination types property values
     */
    public static String PROP_VALUE_DEST_TYPE_TOPIC	= "t";
    public static String PROP_VALUE_DEST_TYPE_QUEUE	= "q";

    /*
     * Property name for queue msg delivery model
     */
    public static String PROP_NAME_QUEUE_FLAVOUR	= "queueDeliveryPolicy";

    /*
     * Property names for new destination attributes in MQ3.5
     */
    public static String PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT	= "maxNumBackupConsumers";
    public static String PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT	= "maxNumActiveConsumers";
    public static String PROP_NAME_IS_LOCAL_DEST		= "isLocalOnly";
    public static String PROP_NAME_LIMIT_BEHAVIOUR		= "limitBehavior";
    public static String PROP_NAME_LOCAL_DELIVERY_PREF		= "localDeliveryPreferred";
    public static String PROP_NAME_CONSUMER_FLOW_LIMIT		= "consumerFlowLimit";
    public static String PROP_NAME_MAX_PRODUCERS		= "maxNumProducers";

    /*
     * Property names for new destintion attributes introduced in MQ 3.6
     */
    public static String PROP_NAME_USE_DMQ			= "useDMQ";

    /*
     * Property names for new destintion attributes introduced in MQ 4.2
     */
    public static String PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED	= "validateXMLSchemaEnabled";
    public static String PROP_NAME_XML_SCHEMA_URI_LIST		= "XMLSchemaURIList";
    public static String PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE	= "reloadXMLSchemaOnFailure";

    /*
     * Queue flavour property values
     */
    public static String PROP_VALUE_QUEUE_FLAVOUR_SINGLE		= "s";
    public static String PROP_VALUE_QUEUE_FLAVOUR_FAILOVER		= "f";
    public static String PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN		= "r";

    /*
     * String values for metric type
     */
    public static String PROP_VALUE_METRICS_TOTALS			= "ttl";
    public static String PROP_VALUE_METRICS_RATES			= "rts";
    public static String PROP_VALUE_METRICS_CONNECTIONS			= "cxn";
    public static String PROP_VALUE_METRICS_CONSUMER			= "con";
    public static String PROP_VALUE_METRICS_DISK			= "dsk";
    public static String PROP_VALUE_METRICS_REMOVE			= "rem";

    /*
     * String values for pause type
     */
    public static String PROP_VALUE_PAUSETYPE_PRODUCERS			= "PRODUCERS";
    public static String PROP_VALUE_PAUSETYPE_CONSUMERS			= "CONSUMERS";
    public static String PROP_VALUE_PAUSETYPE_ALL			= "ALL";

    /*
     * String values for reset type
     */
    public static String PROP_VALUE_RESETTYPE_METRICS			= "METRICS";
    public static String PROP_VALUE_RESETTYPE_ALL			= "ALL";

    /*
     * String value for migratestore partition
     */
    public static String PROP_NAME_OPTION_PARTITION    = "partition";

    /*
     * Property names for Queue optional attributes (for create and update)
     */
    public static String PROP_NAME_OPTION_MAX_MESG_BYTE	    = "maxTotalMsgBytes";
    public static String PROP_NAME_OPTION_MAX_MESG	    = "maxNumMsgs";

    /*
     * Property name for Queue / Topic optional attribute (for create and update)
     */
    public static String PROP_NAME_OPTION_MAX_PER_MESG_SIZE = "maxBytesPerMsg";

    /*
     * Property names for Queue attributes (for getattr hidden subcommand)
     */
    public static String PROP_NAME_OPTION_CUR_MESG_BYTE	    = "curTotalMsgBytes";
    public static String PROP_NAME_OPTION_CUR_MESG	    = "curNumMsgs";
    public static String PROP_NAME_OPTION_CUR_UNACK_MESG    = "curNumUnackMsgs";
    public static String PROP_NAME_OPTION_CUR_PRODUCERS	    = "curNumProducers";
    public static String PROP_NAME_OPTION_CUR_A_CONSUMERS   = "curNumActiveConsumers";
    public static String PROP_NAME_OPTION_CUR_B_CONSUMERS   = "curNumBackupConsumers";

    /*
     * Property names for txn attributes (for getattr hidden subcommand)
     */
    public static String PROP_NAME_OPTION_CUR_TXNS    = "curNumTxns";
    public static String PROP_NAME_OPTION_ALL_TXNS    = "allTxns";

    public static String PROP_NAME_OPTION_TARGET_NAME	= "targetName";
    public static String PROP_NAME_OPTION_DEST_NAME	= "destName";
    public static String PROP_NAME_OPTION_METRIC_INTERVAL= "metricInterval";
    public static String PROP_NAME_OPTION_METRIC_TYPE	= "metricType";
    public static String PROP_NAME_OPTION_SVC_NAME	= "serviceName";
    public static String PROP_NAME_OPTION_CLIENT_ID	= "clientID";
    public static String PROP_NAME_OPTION_BROKER_HOSTPORT
							= "brokerHostPort";
    public static String PROP_NAME_OPTION_ADMIN_USERID	= "adminUser";
    public static String PROP_NAME_OPTION_ADMIN_PASSWD	= "adminPasswd";
    public static String PROP_NAME_OPTION_ADMIN_PASSFILE= "adminPassfile";
    public static String PROP_NAME_OPTION_TARGET_ATTRS	= "target.attrs";
    public static String PROP_NAME_OPTION_SYS_PROPS	= "sys.props";
    public static String PROP_NAME_OPTION_SINGLE_TARGET_ATTR = "single.target.attr";
    public static String PROP_NAME_OPTION_TEMP_DEST 	= "tempDest";
    public static String PROP_VALUE_OPTION_TEMP_DEST	= "true";

    public static String PROP_NAME_OPTION_SHOW_PARTITION       = "showPartition";
    public static String PROP_VALUE_OPTION_SHOW_PARTITION      = "true";

    public static String PROP_NAME_OPTION_LOAD_DESTINATION       = "loadDestination";
    public static String PROP_VALUE_OPTION_LOAD_DESTINATION      = "true";

    public static String PROP_NAME_OPTION_MSG       = "msg";
    public static String PROP_VALUE_OPTION_MSG      = "true";

    public static String PROP_NAME_OPTION_SSL 		= "secure";
    public static String PROP_VALUE_OPTION_SSL		= "true";

    public static String PROP_NAME_OPTION_SERVICE	= "service";

    public static String PROP_NAME_OPTION_PAUSE_TYPE	= "pauseType";

    public static String PROP_NAME_OPTION_NO_FAILOVER	= "noFailover";
    public static String PROP_VALUE_OPTION_NO_FAILOVER	= "true";

    public static String PROP_NAME_OPTION_TIME		= "time";

    public static String PROP_NAME_OPTION_METRIC_SAMPLES = "metricSamples";

    public static String PROP_NAME_OPTION_DEBUG		= "debug";
    public static String PROP_VALUE_OPTION_DEBUG	= "true";

    public static String PROP_NAME_OPTION_ADMIN_DEBUG	= "adebug";
    public static String PROP_VALUE_OPTION_ADMIN_DEBUG	= "true";

    public static String PROP_NAME_OPTION_NOCHECK	= "nocheck";
    public static String PROP_VALUE_OPTION_NOCHECK	= "true";

    public static String PROP_NAME_OPTION_DETAIL	= "detail";
    public static String PROP_VALUE_OPTION_DETAIL	= "true";

    public static String PROP_NAME_OPTION_RECV_TIMEOUT	= "receiveTimeout";
    public static String PROP_NAME_OPTION_NUM_RETRIES	= "numRetries";

    public static String PROP_NAME_OPTION_FORCE		= "force";
    public static String PROP_VALUE_OPTION_FORCE	= "true";

    public static String PROP_NAME_OPTION_SILENTMODE	= "silent";
    public static String PROP_VALUE_OPTION_SILENTMODE	= "true";

    public static String PROP_NAME_OPTION_INPUTFILE	= "inputfile";

    public static String PROP_NAME_OPTION_RESET_TYPE	= "resetType";

    public static String PROP_NAME_OPTION_START_MSG_INDEX= "startMsgIndex";
    public static String PROP_NAME_OPTION_MAX_NUM_MSGS_RET= "maxMsgsRet";
    public static String PROP_NAME_OPTION_MSG_ID	= "msgID";

    /*
     * Property name and value for "-adminkey" option.
     * This is to support the private "-adminkey" option
     * It is used to support authentication when shutting down the
     * broker via the NT services' "Stop" command.
     */
    public static String PROP_NAME_OPTION_ADMINKEY	= "adminkey";
    public static String PROP_VALUE_OPTION_ADMINKEY	= "true";

    /*
     * These strings are of the form name=value.
     * They are needed because the subcommands for jmqcmd require
     * the following actions:
     *	1. signal error if no subcommand args are specified e.g. specify
     *		jmqcmd pause
     *	   without specifying 'svc' or 'bkr'
     *	2. add property name/value pair for the arg specified e.g.
     *		jmqcmd pause svc
     *	   should add the property pair:
     *		cmdarg=svc
     *	3. add property name/value pair for the subcommand specified e.g.
     *		jmqcmd pause svc
     *	   should add the property pair:
     *		cmdtype=pause
     *
     * 1 and 2 are taken care of by the OPTION_VALUE_NEXT_ARG option type.
     * For 3, we needed to define a field in the OptionDesc class that
     * is basically a name/value pair that you want set whenever the option
     * is used. The strings that follow define the name/value pairs for
     * those options. They all of the form:
     *		cmdtype=<subcommand>
     */
    public static String PROP_NAMEVALUE_CMD_LIST = PROP_NAME_CMD+"="+PROP_VALUE_CMD_LIST;
    public static String PROP_NAMEVALUE_CMD_PAUSE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_PAUSE;
    public static String PROP_NAMEVALUE_CMD_RESUME = PROP_NAME_CMD+"="+PROP_VALUE_CMD_RESUME;
    public static String PROP_NAMEVALUE_CMD_SHUTDOWN = PROP_NAME_CMD+"="+PROP_VALUE_CMD_SHUTDOWN;
    public static String PROP_NAMEVALUE_CMD_RESTART = PROP_NAME_CMD+"="+PROP_VALUE_CMD_RESTART;
    public static String PROP_NAMEVALUE_CMD_CREATE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_CREATE;
    public static String PROP_NAMEVALUE_CMD_DESTROY = PROP_NAME_CMD+"="+PROP_VALUE_CMD_DESTROY;
    public static String PROP_NAMEVALUE_CMD_PURGE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_PURGE;
    public static String PROP_NAMEVALUE_CMD_UPDATE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_UPDATE;
    public static String PROP_NAMEVALUE_CMD_QUERY = PROP_NAME_CMD+"="+PROP_VALUE_CMD_QUERY;
    public static String PROP_NAMEVALUE_CMD_METRICS = PROP_NAME_CMD+"="+PROP_VALUE_CMD_METRICS;
    public static String PROP_NAMEVALUE_CMD_RELOAD = PROP_NAME_CMD+"="+PROP_VALUE_CMD_RELOAD;
    public static String PROP_NAMEVALUE_CMD_CHANGEMASTER = PROP_NAME_CMD+"="+PROP_VALUE_CMD_CHANGEMASTER;
    public static String PROP_NAMEVALUE_CMD_COMMIT = PROP_NAME_CMD+"="+PROP_VALUE_CMD_COMMIT;
    public static String PROP_NAMEVALUE_CMD_ROLLBACK = PROP_NAME_CMD+"="+PROP_VALUE_CMD_ROLLBACK;
    public static String PROP_NAMEVALUE_CMD_COMPACT = PROP_NAME_CMD+"="+PROP_VALUE_CMD_COMPACT;
    public static String PROP_NAMEVALUE_CMD_QUIESCE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_QUIESCE;
    public static String PROP_NAMEVALUE_CMD_TAKEOVER = PROP_NAME_CMD+"="+PROP_VALUE_CMD_TAKEOVER;
    public static String PROP_NAMEVALUE_CMD_MIGRATESTORE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_MIGRATESTORE;
    public static String PROP_NAMEVALUE_CMD_UNQUIESCE = PROP_NAME_CMD+"="+PROP_VALUE_CMD_UNQUIESCE;
    public static String PROP_NAMEVALUE_CMD_EXISTS = PROP_NAME_CMD+"="+PROP_VALUE_CMD_EXISTS;
    public static String PROP_NAMEVALUE_CMD_GETATTR = PROP_NAME_CMD+"="+PROP_VALUE_CMD_GETATTR;
    public static String PROP_NAMEVALUE_CMD_UNGRACEFUL_KILL = PROP_NAME_CMD+"="+PROP_VALUE_CMD_UNGRACEFUL_KILL;
    public static String PROP_NAMEVALUE_CMD_PURGEALL = PROP_NAME_CMD+"="+PROP_VALUE_CMD_PURGEALL;
    public static String PROP_NAMEVALUE_CMD_DESTROYALL = PROP_NAME_CMD+"="+PROP_VALUE_CMD_DESTROYALL;
    public static String PROP_NAMEVALUE_CMD_DUMP = PROP_NAME_CMD+"="+PROP_VALUE_CMD_DUMP;
    public static String PROP_NAMEVALUE_CMD_SEND = PROP_NAME_CMD+"="+PROP_VALUE_CMD_SEND;
    public static String PROP_NAMEVALUE_CMD_KILL = PROP_NAME_CMD+"="+PROP_VALUE_CMD_KILL;
    public static String PROP_NAMEVALUE_CMD_DEBUG = PROP_NAME_CMD+"="+PROP_VALUE_CMD_DEBUG;
    public static String PROP_NAMEVALUE_CMD_RESET = PROP_NAME_CMD+"="+PROP_VALUE_CMD_RESET;
    public static String PROP_NAMEVALUE_CMD_CHECKPOINT = PROP_NAME_CMD+"="+PROP_VALUE_CMD_CHECKPOINT;

    /*
     * Arrays containing valid property names (as passed in via -o or -attr) 
     * for various create/update/getattr operations
     */
    public static String[] CREATE_DST_QUEUE_VALID_ATTRS	= {
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT,
    						PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT,
    						PROP_NAME_IS_LOCAL_DEST,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_LOCAL_DELIVERY_PREF,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
							};

    public static String[] CREATE_DST_QUEUE_DEPRECATED_ATTRS	= {
						PROP_NAME_QUEUE_FLAVOUR
							};

    public static String[] CREATE_DST_TOPIC_VALID_ATTRS	= {
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_IS_LOCAL_DEST,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
							};

    public static String[] CREATE_ONLY_DST_ATTRS	= {
						/*
						 * only one attr is
						 * create only for destinations
						 */
    						PROP_NAME_IS_LOCAL_DEST
							};

    public static String[] CHANGEMASTER_VALID_ATTRS	= {
				BrokerConstants.PROP_NAME_BKR_CLS_CFG_SVR,
                };

    public static String[] MIGRATESTORE_VALID_ATTRS	= {
				PROP_NAME_OPTION_PARTITION,
                };

    public static String[] UPDATE_BKR_VALID_ATTRS	= {
				BrokerConstants.PROP_NAME_BKR_PRIMARY_PORT,
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_TOPIC,
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE,
				BrokerConstants.PROP_NAME_BKR_LOG_LEVEL,
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
				/*
				BrokerConstants.PROP_NAME_BKR_METRIC_INTERVAL,
				*/
				BrokerConstants.PROP_NAME_BKR_MAX_MSG,
				BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES,
				BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES,
				BrokerConstants.PROP_NAME_BKR_CLS_URL,
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS,
				BrokerConstants.PROP_NAME_BKR_LOG_DEAD_MSGS,
				BrokerConstants.PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY,
                                BrokerConstants.PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ
							};

    public static String[] UPDATE_BKR_DEPRECATED_ATTRS	= {
				BrokerConstants.PROP_NAME_BKR_QUEUE_DELIVERY_POLICY
							};

    public static String[] UPDATE_DST_QUEUE_VALID_ATTRS	= {
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT,
    						PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_LOCAL_DELIVERY_PREF,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
							};

    public static String[] UPDATE_DST_TOPIC_VALID_ATTRS	= {
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
							};

    public static String[] UPDATE_SVC_VALID_ATTRS	= {
						PROP_NAME_SVC_PORT,
						PROP_NAME_SVC_MIN_THREADS,
						PROP_NAME_SVC_MAX_THREADS
							};

    public static String[] METRIC_TYPE_VALID_VALUES	= {
    						PROP_VALUE_METRICS_TOTALS,
    						PROP_VALUE_METRICS_RATES,
    						PROP_VALUE_METRICS_CONNECTIONS
							};

    public static String[] METRIC_DST_TYPE_VALID_VALUES	= {
    						PROP_VALUE_METRICS_TOTALS,
    						PROP_VALUE_METRICS_RATES,
    						PROP_VALUE_METRICS_CONSUMER,
    						PROP_VALUE_METRICS_DISK
						/*
						PROP_VALUE_METRICS_REMOVE
						*/
							};

    public static String[] GETATTR_DST_QUEUE_VALID_ATTRS = {
						/*
                                                PROP_NAME_OPTION_MAX_MESG_BYTE,
                                                PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
                                                PROP_NAME_OPTION_MAX_MESG,
                                                PROP_NAME_OPTION_CUR_MESG_BYTE,
                                                PROP_NAME_OPTION_CUR_MESG,
						PROP_NAME_OPTION_CUR_PRODUCERS
						*/

						/*
						Complete list:
						*/
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT,
    						PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT,
    						PROP_NAME_IS_LOCAL_DEST,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_LOCAL_DELIVERY_PREF,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
                                                PROP_NAME_OPTION_CUR_MESG_BYTE,
                                                PROP_NAME_OPTION_CUR_MESG,
                                                PROP_NAME_OPTION_CUR_UNACK_MESG,
						PROP_NAME_OPTION_CUR_PRODUCERS,
    						PROP_NAME_OPTION_CUR_A_CONSUMERS,
    						PROP_NAME_OPTION_CUR_B_CONSUMERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
                                                        };

    public static String[] GETATTR_DST_TOPIC_VALID_ATTRS = {
						/*
                                                PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
                                                PROP_NAME_OPTION_CUR_MESG_BYTE,
                                                PROP_NAME_OPTION_CUR_MESG,
						PROP_NAME_OPTION_CUR_PRODUCERS
						*/

						/*
						Complete list:
						*/
						PROP_NAME_OPTION_MAX_MESG_BYTE,
						PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
						PROP_NAME_OPTION_MAX_MESG,
    						PROP_NAME_IS_LOCAL_DEST,
    						PROP_NAME_LIMIT_BEHAVIOUR,
    						PROP_NAME_CONSUMER_FLOW_LIMIT,
    						PROP_NAME_MAX_PRODUCERS,
                                                PROP_NAME_OPTION_CUR_MESG_BYTE,
                                                PROP_NAME_OPTION_CUR_MESG,
                                                PROP_NAME_OPTION_CUR_UNACK_MESG,
						PROP_NAME_OPTION_CUR_PRODUCERS,
    						PROP_NAME_OPTION_CUR_A_CONSUMERS,
    						PROP_NAME_USE_DMQ,
    						PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED,
    						PROP_NAME_XML_SCHEMA_URI_LIST,
    						PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE
                                                        };

    public static String[] GETATTR_SVC_VALID_ATTRS       = {
                                                PROP_NAME_SVC_PORT,
                                                PROP_NAME_SVC_MIN_THREADS,
                                                PROP_NAME_SVC_MAX_THREADS
                                                        };

    public static String[] GETATTR_TXN_VALID_ATTRS       = {
                                                PROP_NAME_OPTION_CUR_TXNS,
                                                PROP_NAME_OPTION_ALL_TXNS
                                                        };

    public static String[] GETATTR_BKR_VALID_ATTRS       = {
                                BrokerConstants.PROP_NAME_BKR_PRIMARY_PORT,
                                BrokerConstants.PROP_NAME_BKR_AUTOCREATE_TOPIC,
                                BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE,
                                BrokerConstants.PROP_NAME_BKR_MAX_MSG,
                                BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES,
                                BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES,
                                BrokerConstants.PROP_NAME_BKR_CUR_MSG,
                                BrokerConstants.PROP_NAME_BKR_CUR_TTL_MSG_BYTES
				/*
                                BrokerConstants.PROP_NAME_BKR_PRODUCT_VERSION,
                                BrokerConstants.PROP_NAME_BKR_LOG_LEVEL,
                                BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
                                BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
                                BrokerConstants.PROP_NAME_BKR_METRIC_INTERVAL,
                                BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES,
                                BrokerConstants.PROP_NAME_BKR_CLS_URL
				 */	
                                                        };

    public static String[] PAUSE_DST_TYPE_VALID_VALUES       = {
                                PROP_VALUE_PAUSETYPE_PRODUCERS,
                                PROP_VALUE_PAUSETYPE_CONSUMERS,
                                PROP_VALUE_PAUSETYPE_ALL
							};

    public static String[] RESET_BKR_TYPE_VALID_VALUES       = {
                                PROP_VALUE_RESETTYPE_METRICS,
                                PROP_VALUE_RESETTYPE_ALL
							};

    /*
     * Destination attributes that can take an unlimited (-1) value
     */
    public static String[] DEST_ATTRS_UNLIMITED       = {
    				PROP_NAME_CONSUMER_FLOW_LIMIT,
				PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
    				PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT,
    				PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT,
				PROP_NAME_OPTION_MAX_MESG,
    				PROP_NAME_MAX_PRODUCERS,
				PROP_NAME_OPTION_MAX_MESG_BYTE
							};

    /*
     * Destination attributes that can take an unlimited (-1) value
     * AND need to be converted over if 0 was specified.
     */
    public static String[] DEST_ATTRS_UNLIMITED_CONV       = {
				PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
				PROP_NAME_OPTION_MAX_MESG,
				PROP_NAME_OPTION_MAX_MESG_BYTE

							};

    /*
     * Broker attributes that can take an unlimited (-1) value
     */
    public static String[] BKR_ATTRS_UNLIMITED       = {
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
				BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS,
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
				BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES,
				BrokerConstants.PROP_NAME_BKR_MAX_MSG,
				BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES
							};

    /*
     * Broker attributes that can take an unlimited (-1) value
     * AND need to be converted over if 0 was specified.
     */
    public static String[] BKR_ATTRS_UNLIMITED_CONV       = {
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_SIZE,
				BrokerConstants.PROP_NAME_BKR_LOG_ROLL_INTERVAL,
				BrokerConstants.PROP_NAME_BKR_MAX_MSG_BYTES,
				BrokerConstants.PROP_NAME_BKR_MAX_MSG,
				BrokerConstants.PROP_NAME_BKR_MAX_TTL_MSG_BYTES
							};

    /*
     * This is the property name for the admin password that is
     * stored in a passfile.
     */
    public static String PROP_NAME_PASSFILE_PASSWD       = "imq.imqcmd.password";
    public static String PROP_NAME_KEYSTORE_PASSWD       = "imq.keystore.password";

    /*
     * END PROPERTY NAMES/VALUES
     */
    
    public static long DEFAULT_METRIC_INTERVAL		= 5;
    public static long DEFAULT_SHUTDOWN_WAIT_INTERVAL	= 100;
}

