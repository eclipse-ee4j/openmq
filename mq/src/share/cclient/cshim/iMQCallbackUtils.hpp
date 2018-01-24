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
 * @(#)iMQCallbackUtils.hpp	1.18 11/09/07
 */ 

#ifndef MQ_CALLBACKUTILS_HPP
#define MQ_CALLBACKUTILS_HPP

#include "mqcallback-types-priv.h"
#include "../client/MessageConsumer.hpp"
#include "../client/Connection.hpp"


MQError invokeMessageListener(const MessageConsumer * consumer,
                              MQMessageListenerFunc   messageListener,
                              void *                  callbackData,
                              Message               * message, PRBool *invoked);

MQError
invokeMessageListenerBA(const MessageConsumer *   consumer,
                        MQMessageListenerBAFunc   messageListenerBA,
                        void *                    callbackData,
                        const Message *           message,
                        MQError                   mqerror, 
                        PRBool *                  invoked);


MQError invokeMessageArrivedCallback(const MessageConsumer * consumer,
                                     MQMessageArrivedFunc callback,
                                     void * callbackData);

MQBool invokeCreateThreadCallback(MQThreadFunc startFunc,
                                   void * arg,
                                   MQCreateThreadFunc callback,
                                   void * callbackData);

void invokeExceptionListenerCallback(const Connection * const connection,
                                     MQError exceptionError,
                                     MQConnectionExceptionListenerFunc callback,
                                     void * callbackData);

void invokeLoggingCallback(const PRInt32 severity,
                           const PRInt32 logCode,
                           ConstMQString logMessage,
                           const PRInt64 timeOfMessage,
                           const PRInt64 connectionID,
                           ConstMQString filename,
                           const PRInt32 fileLineNumber,
                           MQLoggingFunc callback,
                           void* callbackData);

#endif /* MQ_CALLBACKUTILS_HPP */
