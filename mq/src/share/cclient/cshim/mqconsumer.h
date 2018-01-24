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
 * @(#)mqconsumer.h	1.12 06/26/07
 */ 

#ifndef MQ_CONSUMER_H
#define MQ_CONSUMER_H

/*
 * declarations of C interface for message consumer
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"
  
/**
 * Closes the message consumer.  
 *
 * @param consumerHandle the handle to the consumer to close
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCloseMessageConsumer(MQConsumerHandle consumerHandle);

/**
 * Waits until the consumer specified by consumerHandle receives a
 * message and returns this message in messageHandle.  If there is
 * already a message pending for this consumer, then this call returns
 * it immediately and does not block.  If an exception occurs, such as
 * the connection closing before a message arrives, then this call
 * returns with an error.
 *
 * @param consumerHandle the handle to the consumer to wait for a message
 *        to arrive
 * @param messageHandle the output parameter that contains the received
 *        message
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageWait(const MQConsumerHandle consumerHandle, 
                     MQMessageHandle *      messageHandle);

/**
 * Waits for up to timeoutMilliSeconds milliseconds until the consumer
 * specified by consumerHandle receives a message and returns this
 * message in messageHandle.  If there is already a message pending
 * for this consumer, then this call returns it immediately and does
 * not block.  If an exception occurs before a message arrives or the
 * timeout expires, such as the connection closing, then this call
 * returns with an error.
 *
 * @param consumerHandle the handle to the consumer to wait for a message
 *        to arrive
 * @param timeoutMilliSeconds the number of milliseconds to wait for a
 *        message to arrive for this consumer         
 * @param messageHandle the output parameter that contains the received
 *        message
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageWithTimeout(const MQConsumerHandle consumerHandle, 
                            MQInt32                timeoutMilliSeconds,
                            MQMessageHandle *      messageHandle);

/**
 * If a message is pending for the consumer, then this call
 * immediately returns it.  Otherwise, it immediately returns an
 * error.
 *
 * @param consumerHandle the handle to the consumer to wait for a message
 *        to arrive
 * @param messageHandle the output parameter that contains the received
 *        message
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQReceiveMessageNoWait(const MQConsumerHandle consumerHandle, 
                       MQMessageHandle *      messageHandle);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_CONSUMER_H */
