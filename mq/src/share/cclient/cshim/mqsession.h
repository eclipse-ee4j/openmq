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
 * @(#)mqsession.h	1.22 06/26/07
 */ 

#ifndef MQ_SESSION_H
#define MQ_SESSION_H

/*
 * declarations of C interface for session
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"
#include "mqcallback-types.h"

/**
 * Closes the session.  This closes all producers and consumers
 * created from this session.  This will force all threads associated
 * with this session that are blocking in the library (e.g. a consumer
 * calling MQReceiveMessageWait) to return.
 *
 * @param sessionHandle the handle to the session to close
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCloseSession(MQSessionHandle sessionHandle);

/**
 * Creates a destination with the given name and type.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the destination
 * @param destinationName the name of the destination
 * @param destinationType MQ_QUEUE_DESTINATION or MQ_TOPIC_DESTINATION
 * @param destinationHandle the output handle of the newly created
 *        destination.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCreateDestination(const MQSessionHandle sessionHandle,
                    ConstMQString         destinationName,
                    MQDestinationType     destinationType,
                    MQDestinationHandle * destinationHandle);

/**
 * Creates a temporary destination of the given type. 
 *
 * @param sessionHandle the handle to the session for which to
 *        create the temporary destination
 * @param destinationType MQ_QUEUE_DESTINATION or MQ_TOPIC_DESTINATION
 * @param destinationHandle the output handle of the newly created
 *        temporary destination.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCreateTemporaryDestination(const MQSessionHandle sessionHandle,
                             MQDestinationType     destinationType,
                             MQDestinationHandle * destinationHandle);

/**
 * Creates a message producer with a specified destination.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message producer
 * @param destinationHandle the destination to which the created producer
 *        will send messages
 * @param producerHandle the output handle of the newly created
 *        producer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCreateMessageProducerForDestination(
                           const MQSessionHandle     sessionHandle,
                           const MQDestinationHandle destinationHandle,
                           MQProducerHandle *        producerHandle);

/**
 * Creates a message producer with no specified destination.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message producer
 * @param producerHandle the output handle of the newly created
 *        producer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */  
EXPORTED_SYMBOL MQStatus 
MQCreateMessageProducer(const MQSessionHandle sessionHandle,
                          MQProducerHandle *    producerHandle);

/**
 * Creates a message consumer with the given properties for
 * synchronous receiving.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param messageSelector the message selector
 * @param noLocal MQ_TRUE iff the consumer should not receive 
 *        messages sent by a producer on this connection
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */    
EXPORTED_SYMBOL MQStatus 
MQCreateMessageConsumer(const MQSessionHandle     sessionHandle,
                        const MQDestinationHandle destinationHandle,
                        ConstMQString             messageSelector,
                        MQBool                    noLocal,
                        MQConsumerHandle *        consumerHandle);

/**
 * Creates a shared non-durable subscription on the specified Topic 
 * destination with the given properties for synchronous receiving.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param subscriptionName the subscription name
 * @param messageSelector the message selector
 * @param consumerHandle the output handle to the newly created
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateSharedMessageConsumer(const MQSessionHandle     sessionHandle,
                        const MQDestinationHandle destinationHandle,
                        ConstMQString             subscriptionName,
                        ConstMQString             messageSelector,
                        MQConsumerHandle *        consumerHandle);

/**
 * Creates a durable message consumer with the given properties
 * for synchronous receiving.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param durableName the name of the durable subscriber.  
 * @param messageSelector the messages selector
 * @param noLocal if MQ_TRUE the consumer should not receive messages
 *        sent by a producer on a connection with the same client ID
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCreateDurableMessageConsumer(
                      const MQSessionHandle     sessionHandle,
                      const MQDestinationHandle destinationHandle,
                      ConstMQString             durableName,
                      ConstMQString             messageSelector,
                      MQBool                    noLocal, 
                      MQConsumerHandle *        consumerHandle);

/**
 * Creates a shared durable subscription on the specified Topic 
 * destination with the given properties for synchronous receiving.
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param durableName the subscription name
 * @param messageSelector the message selector
 * @param consumerHandle the output handle to the newly created
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateSharedDurableMessageConsumer(const MQSessionHandle     sessionHandle,
                        const MQDestinationHandle destinationHandle,
                        ConstMQString             durableName,
                        ConstMQString             messageSelector,
                        MQConsumerHandle *        consumerHandle);

/**
 * Creates a message consumer for asynchronous receiving.  The session
 * that is represented by the sessionHandle must be created with 
 * MQ_SESSION_ASYNC_RECEIVE
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer.
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param messageSelector the messages selector
 * @param noLocal MQ_TRUE iff the consumer should not receive
 *        messages sent by a producer on this connection
 * @param messageListener the message listener callback function
 * @param listenerCallbackData void * data pointer that to be
 *        passed to the message listener function when it is called
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateAsyncMessageConsumer(const MQSessionHandle     sessionHandle,
                             const MQDestinationHandle destinationHandle,
                             ConstMQString             messageSelector,
                             MQBool                    noLocal,
                             MQMessageListenerFunc     messageListener,
                             void *                    listenerCallbackData,
                             MQConsumerHandle *        consumerHandle);


/**
 * Creates a shared non-durable subscription on the specified Topic
 * destination for asynchronous receiving.  The session that is
 * represented by the sessionHandle must be created with 
 * MQ_SESSION_ASYNC_RECEIVE
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer.
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param subscriptionName the subscription name
 * @param messageSelector the messages selector
 * @param messageListener the message listener callback function
 * @param listenerCallbackData void * data pointer that to be
 *        passed to the message listener function when it is called
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateAsyncSharedMessageConsumer(const MQSessionHandle     sessionHandle,
                             const MQDestinationHandle destinationHandle,
                             ConstMQString             subscriptionName,
                             ConstMQString             messageSelector,
                             MQMessageListenerFunc     messageListener,
                             void *                    listenerCallbackData,
                             MQConsumerHandle *        consumerHandle);

/**
 * Creates a durable message consumer for asynchronous receiving.  
 * The session that is represented by sessionHandle must be created
 * with MQ_SESSION_ASYNC_RECEIVE
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param durableName the name of the durable subscriber.
 * @param messageSelector the message selector
 * @param noLocal MQ_TRUE iff the consumer should not receive
 *        messages sent by a producer on this connection
 * @param messageListener the mesage listener callback function
 * @param listenerCallbackData void * data pointer that to be
 *        passed to the message listener function when it is called
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateAsyncDurableMessageConsumer(
                           const MQSessionHandle     sessionHandle,
                           const MQDestinationHandle destinationHandle,
                           ConstMQString             durableName,
                           ConstMQString             messageSelector,
                           MQBool                    noLocal,
                           MQMessageListenerFunc     messageListener,
                           void *                    listenerCallbackData,
                           MQConsumerHandle *        consumerHandle);


/**
 * Creates a shared durable subscription on the specified Topic
 * destination for asynchronous receiving.  The session that is
 * represented by the sessionHandle must be created with 
 * MQ_SESSION_ASYNC_RECEIVE
 *
 * @param sessionHandle the handle to the session for which to
 *        create the message consumer.
 * @param destinationHandle the destination on which the consumer
 *        receives messages
 * @param durableName the durable name
 * @param messageSelector the messages selector
 * @param messageListener the message listener callback function
 * @param listenerCallbackData void * data pointer that to be
 *        passed to the message listener function when it is called
 * @param consumerHandle the output handle to the newly creaated
 *        consumer
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCreateAsyncSharedDurableMessageConsumer(const MQSessionHandle sessionHandle,
                             const MQDestinationHandle destinationHandle,
                             ConstMQString             durableName,
                             ConstMQString             messageSelector,
                             MQMessageListenerFunc     messageListener,
                             void *                    listenerCallbackData,
                             MQConsumerHandle *        consumerHandle);

/**
 * Unsubscribes the durable message consumer with the given durable
 * name.  This deletes all messages at the broker that were being
 * stored for this durable consumer.  This function cannot be called
 * if there is an active consumer with the specified durable name.
 *
 * @param sessionHandle the handle to the session to use to 
 *        unsubscribe the durable consumer.
 * @param durableName the name of the durable subscriber.  
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQUnsubscribeDurableMessageConsumer(const MQSessionHandle sessionHandle,
                                    ConstMQString const durableName);

/**
 * Stops message delivery in this session, and restarts message
 *  delivery with the oldest unacknowledged message. 
 *
 * All consumers deliver messages in a serial order. Acknowledging
 * a received message automatically acknowledges all messages that
 * have been delivered to the client. 
 *
 * Restarting a session causes it to take the following actions: 
 * .  Stop message delivery 
 * .  Mark all messages that might have been delivered but not acknowledged
 *    as "redelivered" 
 * .  Restart the delivery sequence including all unacknowledged messages
 *    that had been previously delivered. Redelivered messages do not have
 *    to be delivered in exactly their original delivery order. 
 *
 * @param sessionHandle the handle to the session
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQRecoverSession(const MQSessionHandle sessionHandle);


/**
 * Commits all messages done in this transaction
 *
 * @param sessionHandle the handle to the session
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCommitSession(const MQSessionHandle sessionHandle);


/**
 * Rolls back any messages done in this transaction
 *
 * @param sessionHandle the handle to the session
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQRollbackSession(const MQSessionHandle sessionHandle);


/**
 * Get session acknowledge mode
 *
 * @param sessionHandle the handle to the session
 * @param ackMode output parameter
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQGetAcknowledgeMode(const MQSessionHandle sessionHandle, MQAckMode * ackMode);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_SESSION_H */
