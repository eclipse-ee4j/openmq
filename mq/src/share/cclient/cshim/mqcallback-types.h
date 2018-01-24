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
 * @(#)mqcallback-types.h	1.18 11/09/07
 */ 

#ifndef MQ_CALLBACK_TYPES_H
#define MQ_CALLBACK_TYPES_H

/*
 * defines MQ C-API callback types 
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"

/**
 * This callback is used to notify the user that an exception occurred.
 *
 * @param connectionHandle a handle to the connection on which the
 *        connection exception occurred
 * @param exception the connection exception that occurred
 * @param callbackData whatever void * pointer that was passed to
 *        MQCreateConnection
 * @see MQCreateConnection.  */
typedef void (*MQConnectionExceptionListenerFunc)(
                                const MQConnectionHandle  connectionHandle,
                                MQStatus                  exception,
                                void *                    callbackData );

/**
 * This callback is used for asynchronous receiving messages
 *
 * @param sessionHandle a handle to the session
 * @param consumerHandle a handle to the message consumer 
 * @param messageHandle a handle to the message 
 * @param callbackData whatever void * pointer that was passed to
 *        MQCreateAsyncMessageConsumer or MQCreateAsyncDurableMessageConsumer
 * @see MQCreateAsyncMessageConsumer and MQCreateAsyncDurableMessageConsumer. */
typedef MQError (*MQMessageListenerFunc)(const MQSessionHandle  sessionHandle,
                                         const MQConsumerHandle consumerHandle,
                                         MQMessageHandle        messageHandle,
                                         void *                 callbackData);


/**
 * This callback is called before and after MQMessageListenerFunc call
 * for a XA session. The sessionHandle, consumerHandle, messageHandle
 * are for 'read-only' purpose only.  Please do not call MQFreeMessage  
 * for the messageHandle or MQCloseSession for the sessionHandle or 
 * MQCloseMessageConsumer for the consumerHandle in this callback 
 * function or any fuctions called by this callback. Restrictions in 
 * MQMessageListenerFunc callback also applies to this callback type.
 *
 * @param sessionHandle a handle to the session
 * @param consumerHandle a handle to the message consumer
 * @param messageHandle a handle to the message
 * @param errorCode processing status of the current message
 * @param callbackData whatever void * pointer that was passed to
 *        MQCreateAsyncMessageConsumer or MQCreateAsyncDurableMessageConsumer
 * @see MQCreateAsyncMessageConsumer and MQCreateAsyncDurableMessageConsumer. */
typedef MQError (*MQMessageListenerBAFunc)(const MQSessionHandle  sessionHandle,
                                           const MQConsumerHandle consumerHandle,
                                           const MQMessageHandle  messageHandle,
                                           MQError                errorCode,
                                           void *                 callbackData);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_CALLBACK_TYPES_H */
