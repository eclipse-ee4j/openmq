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
 * @(#)mqconnection-props.h	1.17 06/26/07
 */ 

#ifndef MQ_CONNECTION_PROPERTIES_H
#define MQ_CONNECTION_PROPERTIES_H

/*
 * defines constants for connection properties
 */

static const char * MQ_BROKER_HOST_PROPERTY             = "MQBrokerHostName"; /* MQString */
static const char * MQ_BROKER_PORT_PROPERTY             = "MQBrokerHostPort"; /* MQInt32  */
static const char * MQ_SERVICE_PORT_PROPERTY            = "MQServicePort";    /* MQInt32  */
static const char * MQ_ENABLE_IPV6_PROPERTY             = "MQEnableIPv6"; /* MQBool */
static const char * MQ_WRITE_TIMEOUT_PROPERTY           = "MQWriteTimeout"; /* MQInt32 in millisecond */
static const char * MQ_READ_PORTMAPPER_TIMEOUT_PROPERTY = "MQReadPortMapperTimeout"; /* MQInt32 in millisecond */
static const char * MQ_CONNECTION_TYPE_PROPERTY         = "MQConnectionType"; /* MQString */
static const char * MQ_ACK_TIMEOUT_PROPERTY             = "MQAckTimeout";     /* MQInt32 in millisecond */
static const char * MQ_ACK_ON_PRODUCE_PROPERTY          = "MQAckOnProduce";        /* MQBool */
static const char * MQ_ACK_ON_ACKNOWLEDGE_PROPERTY      = "MQAckOnAcknowledge";    /* MQBool */
static const char * MQ_CONNECTION_FLOW_COUNT_PROPERTY         = "MQConnectionFlowCount";        /* MQInt32 */
static const char * MQ_CONNECTION_FLOW_LIMIT_ENABLED_PROPERTY = "MQConnectionFlowLimitEnabled"; /* MQBool  */
static const char * MQ_CONNECTION_FLOW_LIMIT_PROPERTY         = "MQConnectionFlowLimit";        /* MQInt32 */
static const char * MQ_PING_INTERVAL_PROPERTY           = "MQPingInterval";   /* MQInt32 in second */


/** SSL */
static const char * MQ_SSL_BROKER_IS_TRUSTED            = "MQSSLIsHostTrusted";        /* MQBool */
static const char * MQ_SSL_CHECK_BROKER_FINGERPRINT     = "MQSSLCheckHostFingerprint"; /* MQBool */
static const char * MQ_SSL_BROKER_CERT_FINGERPRINT      = "MQSSLHostCertFingerprint";  /* MQString */


/** connection metadata properties to be used with MQGetMetaData   */
static const char * MQ_NAME_PROPERTY            = "MQ_NAME";
static const char * MQ_VERSION_PROPERTY         = "MQ_VERSION";
static const char * MQ_MAJOR_VERSION_PROPERTY   = "MQ_VMAJOR";
static const char * MQ_MINOR_VERSION_PROPERTY   = "MQ_VMINOR";
static const char * MQ_MICRO_VERSION_PROPERTY   = "MQ_VMICRO";
static const char * MQ_SERVICE_PACK_PROPERTY    = "MQ_SVCPACK";
static const char * MQ_UPDATE_RELEASE_PROPERTY  = "MQ_URELEASE";

#endif /* MQ_CONNECTION_PROPERTIES_H */
