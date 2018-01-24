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
 * @(#)mqproducer.h	1.15 06/26/07
 */ 
 
#ifndef MQ_PRODUCER_H
#define MQ_PRODUCER_H

/*
 * declarations of C interface for message producer
 */ 

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"
#include "mqmessage.h"
  
/**
 * Closes the message producer.  
 *
 * @param producerHandle the handle to the producer to close
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQCloseMessageProducer(MQProducerHandle producerHandle);

/**
 * Has the producer specified by producerHandle send the message
 * specified by messageHandle to the producer's destination with
 * the default message properties.  This call can only be used with 
 * a producer that has a specified destination at creation time (i.e.
 * producers created by calling MQCreateMessageProducerForDestination)
 *
 * @param producerHandle the handle to the producer to close
 * @param messageHandle the message to send
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSendMessage(const MQProducerHandle producerHandle,
              const MQMessageHandle  messageHandle);

/**
 * Has the producer specified by producerHandle send the message
 * specified by messageHandle to the producer's destination with the
 * specified message properties.  This call can only be used with a
 * producer that has a specified destination at creation time (i.e.
 * producers created by calling MQCreateMessageProducerForDestination)
 *
 * @param producerHandle the handle to the producer to close
 * @param messageHandle the message to send
 * @param msgDeliveryMode the persistent delivery mode of the
 *        message.  Options are MQ_NON_PERSISTENT_DELIVERY and
 *        MQ_PERSISTENT_DELIVERY
 * @param msgPriority the priority of the message. There are 10 levels
 *        of priority, with 0 lowest and 9 highest. The default level
 *        is 4. A JMS provider tries to deliver higher-priority
 *        messages before lower-priority ones, but does not have to
 *        deliver messages in exact order of priority.
 * @param msgTimeToLive the message's lifetime (in milliseconds)
 *        If the specified value is zero, the message never expires.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSendMessageExt(const MQProducerHandle producerHandle,
                 const MQMessageHandle  messageHandle,
                 MQDeliveryMode         msgDeliveryMode,
                 MQInt8                 msgPriority,
                 MQInt64                msgTimeToLive);

/**
 * Has the producer specified by producerHandle send the message
 * specified by messageHandle to the destination specified by
 * destinationHandle with the default message properties. This 
 * call can only be used with a producer that does not have a
 * specified destination at creation time (i.e. producers created
 * by calling MQCreateMessageProducer)
 *
 * @param producerHandle the handle to the producer to close
 * @param messageHandle the message to send
 * @param destinationHandle the destination to send the message to
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSendMessageToDestination(const MQProducerHandle    producerHandle,
                           const MQMessageHandle     messageHandle,
                           const MQDestinationHandle destinationHandle);

/**
 * Has the producer specified by producerHandle send the message
 * specified by messageHandle to the destination specified by
 * destinationHandle with the specified message properties.  This 
 * call can only be used with a producer that does not have a specified
 * destination at creation time (i.e. producers created by calling
 * MQCreateMessageProducer)
 *
 * @param producerHandle the handle to the producer to close
 * @param messageHandle the message to send
 * @param destinationHandle the destination to send the message to
 * @param msgDeliveryMode the persistent delivery mode of the
 *        message.  Options are MQ_NON_PERSISTENT_DELIVERY and
 *        MQ_PERSISTENT_DELIVERY
 * @param msgPriority the priority of the message. There are 10 levels
 *        of priority, with 0 lowest and 9 highest. The default level
 *        is 4. A JMS provider tries to deliver higher-priority
 *        messages before lower-priority ones, but does not have to
 *        deliver messages in exact order of priority.
 * @param msgTimeToLive the number of milliseconds until the
 *        message expires.  If the specified value is zero, the message
 *        never expires.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus 
MQSendMessageToDestinationExt(const MQProducerHandle    producerHandle,
                              const MQMessageHandle     messageHandle,
                              const MQDestinationHandle destinationHandle,
                              MQDeliveryMode            msgDeliveryMode,
                              MQInt8                    msgPriority,
                              MQInt64                   msgTimeToLive);

/**
 * Set the minimum length of time in milliseconds from its dispatch time
 * before a produced message becomes visible on the target destination and 
 * available for delivery to consumers.
 */
EXPORTED_SYMBOL MQStatus
MQSetDeliveryDelay(const MQProducerHandle producerHandle,
                   MQInt64 deliveryDelay);

/**
 * Get the minimum length of time in milliseconds from its dispatch time
 * before a produced message becomes visible on the target destination and 
 * available for delivery to consumers.
 */
EXPORTED_SYMBOL MQStatus
MQGetDeliveryDelay(const MQProducerHandle producerHandle,
                   MQInt64 * deliveryDelay);
  
#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_PRODUCER_H */

