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
 * @(#)mqheader-props.h	1.11 06/26/07
 */ 

#ifndef MQ_HEADER_PROPERTIES_H
#define MQ_HEADER_PROPERTIES_H

/*
 * defines constants for message headers
 */

static const char * MQ_PERSISTENT_HEADER_PROPERTY          = "MQPersistent";    /* MQBool */
static const char * MQ_REDELIVERED_HEADER_PROPERTY         = "MQRedelivered";   /* MQBool */
static const char * MQ_EXPIRATION_HEADER_PROPERTY          = "MQExpiration";    /* MQInt64 */
static const char * MQ_DELIVERY_TIME_HEADER_PROPERTY       = "MQDeliveryTime";  /* MQInt64 (read only)*/
static const char * MQ_PRIORITY_HEADER_PROPERTY            = "MQPriority";      /* MQInt8 */
static const char * MQ_TIMESTAMP_HEADER_PROPERTY           = "MQTimestamp";     /* MQInt64 */
static const char * MQ_MESSAGE_ID_HEADER_PROPERTY          = "MQMessageID";     /* MQString */
static const char * MQ_CORRELATION_ID_HEADER_PROPERTY      = "MQCorrelationID"; /* MQString */
static const char * MQ_MESSAGE_TYPE_HEADER_PROPERTY        = "MQType";          /* MQString  */


#endif /* MQ_HEADER_PROPERTIES_H */
