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
 * @(#)iMQConstants.hpp	1.17 10/17/07
 */ 

#ifndef IMQCONSTANTS_HPP
#define IMQCONSTANTS_HPP

#include "../cshim/mqconnection-props.h"

static const char * IMQ_CONNECTIONID_PROPERTY     = "JMQConnectionID"; //3.0
static const char * IMQ_SESSIONID_PROPERTY        = "JMQSessionID";    //3.5

static const char * IMQ_ACKMODE_PROPERTY          = "JMQAckMode";
static const char * IMQ_DESTINATION_PROPERTY      = "JMQDestination";
static const char * IMQ_DESTINATION_TYPE_PROPERTY = "JMQDestType";
static const char * IMQ_SELECTOR_PROPERTY         = "JMQSelector";
static const char * IMQ_DURABLE_NAME_PROPERTY     = "JMQDurableName";
static const char * IMQ_SHARED_SUBSCRIPTION_NAME_PROPERTY = "JMQSharedSubscriptionName"; //5.0
static const char * IMQ_JMS_SHARED_PROPERTY       = "JMQJMSShare"; //5.0
static const char * IMQ_NOLOCAL_PROPERTY          = "JMQNoLocal";
static const char * IMQ_RECONNECT_PROPERTY        = "JMQReconnect";
static const char * IMQ_SIZE_PROPERTY             = "JMQSize";
static const char * IMQ_SHARE_PROPERTY            = "JMQShare";        //3.5 not supported

static const char * IMQ_PRODUCERID_PROPERTY       = "JMQProducerID";   //3.5
static const char * IMQ_BYTES_PROPERTY            = "JMQBytes";        //3.5


static const char * IMQ_CONSUMERID_PROPERTY       = "JMQConsumerID";
static const char * IMQ_TRANSACTIONID_PROPERTY    = "JMQTransactionID";
static const char * IMQ_CLIENTID_PROPERTY         = "JMQClientID";
static const char * IMQ_STATUS_PROPERTY           = "JMQStatus";
static const char * IMQ_REASON_PROPERTY           = "JMQReason";
static const char * IMQ_AUTH_TYPE_PROPERTY        = "JMQAuthType";
static const char * IMQ_CHALLENGE_PROPERTY        = "JMQChallenge";
static const char * IMQ_PROTOCOL_LEVEL_PROPERTY   = "JMQProtocolLevel";
static const char * IMQ_PRODUCT_VERSION_PROPERTY  = "JMQVersion";
static const char * IMQ_BLOCK_PROPERTY            = "JMQBlock";
static const char * IMQ_SET_REDELIVERED_PROPERTY  = "JMQSetRedelivered";
static const char * IMQ_USER_AGENT_PROPERTY       = "JMQUserAgent";    //3.5
static const char * IMQ_BODY_TYPE_PROPERTY        = "JMQBodyType";

static const char * MQ_ACK_TYPE_PROPERTY        = "JMQAckType"; //added to c-api 4.5
static const PRInt32 ACK_TYPE_ACKNOWLEDGE_REQUEST=0;
static const PRInt32 ACK_TYPE_UNDELIVERABLE_REQUEST=1;
static const PRInt32 ACK_TYPE_DEAD_REQUEST=2;

static const char * MQ_DEAD_REASON_PROPERTY        = "JMQDeadReason"; //4.5
static const PRInt32 DEAD_REASON_UNDELIVERABLE = 0;
static const PRInt32 DEAD_REASON_EXPIRED = 1;

static const char * MQ_XAFLAGS_PROPERTY           = "JMQXAFlags";
static const char * MQ_XA_ONEPHASE_PROPERTY       = "JMQXAOnePhase";   //4.1
static const char * MQ_QUANTITY_PROPERTY          = "JMQQuantity";   

static const char * MQ_SET_REDELIVER_PROPERTY       = "JMQRedeliver";


static const char * IMQ_AUTHTYPE_JMQADMINKEY      = "jmqadminkey";
static const char * IMQ_AUTHTYPE_JMQBASIC         = "basic";
static const char * IMQ_AUTHTYPE_JMQDIGEST        = "digest";



static const char * TEMPORARY_DESTINATION_URI_PREFIX = "temporary_destination://";
static const char * TEMPORARY_QUEUE_URI_NAME = "queue/";
static const char * TEMPORARY_TOPIC_URI_NAME = "topic/";

//
// Values for the defaults were copied from ConnectionImpl.java
//

// see ConnectionImpl::transportConnectionType (default is "TCP")
static const char * TCP_CONNECTION_TYPE     = "TCP";
static const char * SSL_CONNECTION_TYPE     = "SSL";
static const char * TLS_CONNECTION_TYPE     = "TLS";
static const char * DEFAULT_CONNECTION_TYPE = TCP_CONNECTION_TYPE;

// see ConnectionImpl::ackTime (default is 0 which is no timeout)
static const PRInt32 DEFAULT_ACK_TIMEOUT_MILLISEC = 0;

// see ConnectionImpl::writeTime (default is 0 which is no timeout)
static const PRInt32 DEFAULT_WRITE_TIMEOUT_MILLISEC = 0;

static const PRInt32 DEFAULT_PING_INTERVAL_SEC = 30;

// see ConnectionImpl::protectMode (default is false)
static const PRBool  DEFAULT_CONNECTION_FLOW_LIMIT_ENABLED = PR_FALSE;
static const PRInt32 DEFAULT_CONNECTION_FLOW_LIMIT = 1000;
static const PRInt32 DEFAULT_CONNECTION_FLOW_COUNT = 100; 

static const PRInt32   DEFAULT_CONSUMER_PREFETCH_MAX_MESSAGE_COUNT = -1;
static const PRFloat64 DEFAULT_CONSUMER_PREFETCH_THRESHOLD_PERCENT = 50;

// see ConnectionImpl::ackOnPersistentProduce (default is true)
static const PRBool DEFAULT_ACK_ON_PERSISTENT_PRODUCE = PR_TRUE;

// see ConnectionImpl::ackOnNonPersistentProduce (default is false)
static const PRBool DEFAULT_ACK_ON_NON_PERSISTENT_PRODUCE = PR_FALSE;

// see ConnectionImpl::ackOnAcknowledge (default is true)
static const PRBool DEFAULT_ACK_ON_ACKNOWLEDGE = PR_TRUE;

// see SessionImpl::afterMessageDeliver
static const PRInt32 DEFAULT_DUPS_OK_LIMIT = 10;

// (default is true)
static const PRBool DEFAULT_SSL_BROKER_IS_TRUSTED = PR_TRUE;

// (default is false)
static const PRBool DEFAULT_SSL_CHECK_BROKER_FINGERPRINT = PR_FALSE;

// (default is "")
static const char * DEFAULT_SSL_HOST_CERT_FINGERPRINT = NULL;

/**
 * A normal connection type.
 */
static const char * CONNECTION_TYPE_NORMAL_STR = "NORMAL";

/**
 * A timeout value that implies wait forever.
 */
static const PRUint32 TRANSPORT_NO_TIMEOUT = PR_INTERVAL_NO_TIMEOUT;

/**
 * The amount of time to wait for a connect to the broker to complete. 
 */
static const PRUint32 DEFAULT_CONNECT_TIMEOUT = 60 * 1000 * 1000;  // 1 minute

static const PRInt32 INITIAL_TEMP_DEST_SEQUENCE = 1000;
static const PRInt32 MAX_DESTINATION_NAME_LEN = 10000;


#endif // IMQCONSTANTS_HPP
