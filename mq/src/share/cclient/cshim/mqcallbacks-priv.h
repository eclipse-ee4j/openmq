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
 * @(#)mqcallbacks-priv.h	1.10 06/26/07
 */ 

#ifndef MQ_CALLBACKS_PRIV_H
#define MQ_CALLBACKS_PRIV_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqcallback-types-priv.h"


/**
 * Associates threadCreator with the connection specified by
 * connectionHandle.  Whenever the connection needs to create a
 * thread, it will call threadCreator.  The connection will use it's
 * own method for creating a thread if this method is not called , or
 * NULL is passed for threadCreator.
 *
 * @param connectionHandle handle to the connection to install the callback for
 * @param threadCreator the callback to use for creating threads
 * @param callbackData data to pass to threadCreator */
EXPORTED_SYMBOL MQStatus 
MQSetCreateThreadFunc(MQConnectionHandle        connectionHandle,
                      const MQCreateThreadFunc  threadCreator,
                      void*                     callbackData);

/**
 * Associates messageCallback with the consumer specified by
 * consumerHandle.  Whenever a message arrives for the consumer,
 * messageCallback is invoked.  The thread that invokes this callback
 * is a vital internal thread.  messageCallback should run quickly,
 * and it MUST NOT call any other MQ methods such as
 * MQReceiveMessageNoWait.  Instead, messageCallback should notify
 * another thread that a message has arrived, and this other thread
 * can call MQReceiveMessageNoWait.
 *
 * @param consumerHandle handle to the consumer to install the callback for
 * @param messageCallback the function to call when a message arrives for
 *        the consumer
 * @param callbackData data to pass to messageCallback */
EXPORTED_SYMBOL MQStatus 
MQSetMessageArrivedFunc(const MQConsumerHandle            consumerHandle,
                        MQMessageArrivedFunc              messageCallback,
                        void *                            callbackData);

/**
 * Installs loggingFunc as the callback function to call when messages
 * are logged.
 *
 * @param loggingFunc the function to call when a message is logged
 * @param callbackData data to pass to loggingFunc */
EXPORTED_SYMBOL MQStatus 
MQSetLoggingFunc(const MQLoggingFunc  loggingFunc,
                 void *               callbackData);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_CALLBACKS_PRIV_H */
