#
# Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0, which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# This Source Code may also be made available under the following Secondary
# Licenses when the conditions for such availability set forth in the
# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
# version 2 with the GNU Classpath Exception, which is available at
# https://www.gnu.org/software/classpath/license.html.
#
# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
#

#
# @(#)libmqcrt.spec	1.36 11/07/07
# 

#
# Public Interfaces
#

function        MQAcknowledgeMessages
declaration     MQStatus \
                MQAcknowledgeMessages(const MQSessionHandle sessionHandle, \
                                      const MQMessageHandle  messageHandle);
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCloseConnection
declaration     MQStatus MQCloseConnection(const MQConnectionHandle connectionHandle) 
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCloseMessageConsumer
declaration     MQStatus MQCloseMessageConsumer(MQConsumerHandle consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCloseMessageProducer
declaration     MQStatus \
                MQCloseMessageProducer(MQProducerHandle producerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCloseSession
declaration     MQStatus \
                MQCloseSession(MQSessionHandle sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCommitSession
declaration     MQStatus \
                MQCommitSession(const MQSessionHandle sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQRollbackSession
declaration     MQStatus \
                MQRollbackSession(const MQSessionHandle sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateMessage
declaration     MQStatus \
                MQCreateMessage(MQMessageHandle * messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.2
end


function        MQCreateBytesMessage
declaration     MQStatus \
                MQCreateBytesMessage(MQMessageHandle * messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateConnection
declaration     MQStatus \
                MQCreateConnection(MQPropertiesHandle          propertiesHandle, \
                                   ConstMQString                     username, \
                                   ConstMQString                     password, \
                                   ConstMQString                     clientID, \
                                   MQConnectionExceptionListenerFunc exceptionListener, \
                                   void *                            listenerCallbackData, \
                                   MQConnectionHandle *              connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetXAConnection
declaration     MQStatus \
                MQGetXAConnection(MQConnectionHandle * connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.3
end


function        MQGetConnectionProperties
declaration     MQStatus \
                MQGetConnectionProperties(const MQConnectionHandle connectionHandle, \
                                          MQPropertiesHandle * propertiesHandle);

include         "mqcrt.h"
arch            all
version         SUNW_1.3
end


function        MQCreateDestination
declaration     MQStatus \
                MQCreateDestination(const MQSessionHandle sessionHandle, \
                                    ConstMQString         destinationName, \
                                    MQDestinationType     destinationType, \
                                    MQDestinationHandle * destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateDurableMessageConsumer
declaration     MQStatus \
                MQCreateDurableMessageConsumer(const MQSessionHandle     sessionHandle, \
                                               const MQDestinationHandle destinationHandle, \
                                               ConstMQString             durableName, \
                                               ConstMQString             messageSelector, \
                                               MQBool                    noLocal, \
                                               MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

function        MQCreateSharedDurableMessageConsumer
declaration     MQStatus \
                MQCreateSharedDurableMessageConsumer(const MQSessionHandle     sessionHandle, \
                                               const MQDestinationHandle destinationHandle, \
                                               ConstMQString             durableName, \
                                               ConstMQString             messageSelector, \
                                               MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.5
end


function        MQCreateMessageConsumer
declaration     MQStatus \
                MQCreateMessageConsumer(const MQSessionHandle     sessionHandle, \
                                        const MQDestinationHandle destinationHandle, \
                                        ConstMQString             messageSelector, \
                                        MQBool                    noLocal, \
                                        MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

function        MQCreateSharedMessageConsumer
declaration     MQStatus \
                MQCreateSharedMessageConsumer(const MQSessionHandle     sessionHandle, \
                                        const MQDestinationHandle destinationHandle, \
                                        ConstMQString             subscriptionName, \
                                        ConstMQString             messageSelector, \
                                        MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.5
end


function        MQCreateAsyncDurableMessageConsumer
declaration     MQStatus \
                MQCreateAsyncDurableMessageConsumer(const MQSessionHandle     sessionHandle, \
                                                    const MQDestinationHandle destinationHandle, \
                                                    ConstMQString             durableName, \
                                                    ConstMQString             messageSelector, \
                                                    MQBool                    noLocal, \
                                                    MQMessageListenerFunc     messageListener, \
                                                    void *                    listenerCallbackData, \
                                                    MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

function        MQCreateAsyncSharedDurableMessageConsumer
declaration     MQStatus \
                MQCreateAsyncSharedDurableMessageConsumer(const MQSessionHandle     sessionHandle, \
                                                    const MQDestinationHandle destinationHandle, \
                                                    ConstMQString             durableName, \
                                                    ConstMQString             messageSelector, \
                                                    MQMessageListenerFunc     messageListener, \
                                                    void *                    listenerCallbackData, \
                                                    MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.5
end

function        MQCreateAsyncMessageConsumer
declaration     MQStatus \
                MQCreateAsyncMessageConsumer(const MQSessionHandle     sessionHandle, \
                                             const MQDestinationHandle destinationHandle, \
                                             ConstMQString             messageSelector, \
                                             MQBool                    noLocal, \
                                             MQMessageListenerFunc     messageListener,\
                                             void *                    listenerCallbackData, \
                                             MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

function        MQCreateAsyncSharedMessageConsumer
declaration     MQStatus \
                MQCreateAsyncSharedMessageConsumer(const MQSessionHandle     sessionHandle, \
                                             const MQDestinationHandle destinationHandle, \
                                             ConstMQString             subscriptionName, \
                                             ConstMQString             messageSelector, \
                                             MQMessageListenerFunc     messageListener,\
                                             void *                    listenerCallbackData, \
                                             MQConsumerHandle *        consumerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.5
end

function        MQCreateMessageProducer
declaration     MQStatus \
                MQCreateMessageProducer(const MQSessionHandle sessionHandle, \
                                        MQProducerHandle *    producerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateMessageProducerForDestination
declaration     MQStatus \
                MQCreateMessageProducerForDestination(const MQSessionHandle     sessionHandle, \
                                                      const MQDestinationHandle destinationHandle, \
                                                      MQProducerHandle *        producerHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateProperties
declaration     MQStatus \
                MQCreateProperties(MQPropertiesHandle * propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateSession
declaration     MQStatus \
                MQCreateSession(const MQConnectionHandle connectionHandle, \
                                MQBool                   isTransacted, \
                                MQAckMode                acknowledgeMode, \
                                MQReceiveMode            receiveMode, \
                                MQSessionHandle *        sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateXASession
declaration     MQStatus \
                MQCreateXASession(const MQConnectionHandle connectionHandle, \
                                  MQReceiveMode            receiveMode, \
                                  MQMessageListenerBAFunc  beforeMessageListener, \
                                  MQMessageListenerBAFunc  afterMessageListener, \
                                  void *                   callbackData, \
                                  MQSessionHandle *        sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.3
end


function        MQCreateTemporaryDestination
declaration     MQStatus \
                MQCreateTemporaryDestination(const MQSessionHandle sessionHandle, \
                                             MQDestinationType     destinationType, \
                                             MQDestinationHandle * destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQCreateTextMessage
declaration     MQStatus \
                MQCreateTextMessage(MQMessageHandle * messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQFreeConnection
declaration     MQStatus \
                MQFreeConnection(MQConnectionHandle connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQFreeDestination
declaration     MQStatus \
                MQFreeDestination(MQDestinationHandle destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQFreeMessage
declaration     MQStatus \
                MQFreeMessage(MQMessageHandle messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQFreeProperties
declaration     MQStatus \
				MQFreeProperties(MQPropertiesHandle propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQFreeString
declaration     void \
				MQFreeString(MQString String)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetAcknowledgeMode
declaration     MQStatus \
                MQGetAcknowledgeMode(const MQSessionHandle sessionHandle, \
                                     MQAckMode *           ackMode)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetBoolProperty
declaration     MQStatus \
                MQGetBoolProperty(const MQPropertiesHandle propertiesHandle, \
                                  ConstMQString            key, \
                                  MQBool *                 value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetBytesMessageBytes
declaration     MQStatus \
                MQGetBytesMessageBytes(const MQMessageHandle messageHandle, \
                                       const MQInt8 **       messageBytes, \
                                       MQInt32 *             messageBytesSize)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetDestinationType
declaration     MQStatus \
                MQGetDestinationType(const MQDestinationHandle destinationHandle, \
                                     MQDestinationType *       destinationType)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

function        MQGetDestinationName
declaration     MQStatus \
                MQGetDestinationName(const MQDestinationHandle destinationHandle, \
                                     MQString *                destinationName)
include         "mqcrt.h"
arch            all
version         SUNW_1.2
end


function        MQGetErrorTrace
declaration     MQString \
                MQGetErrorTrace()
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetFloat32Property
declaration     MQStatus \
                MQGetFloat32Property(const MQPropertiesHandle propertiesHandle, \
                                     ConstMQString            key, \
                                     MQFloat32 *              value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetFloat64Property
declaration     MQStatus \
                MQGetFloat64Property(const MQPropertiesHandle propertiesHandle, \
                                     ConstMQString            key, \
                                     MQFloat64 *              value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetInt16Property
declaration     MQStatus \
                MQGetInt16Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt16 *                value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetInt32Property
declaration     MQStatus \
                MQGetInt32Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt32 *                value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetInt64Property
declaration     MQStatus \
                MQGetInt64Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt64 *                value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetInt8Property
declaration     MQStatus \
                MQGetInt8Property(const MQPropertiesHandle propertiesHandle, \
                                  ConstMQString            key, \
                                  MQInt8 *                 value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetMessageHeaders
declaration     MQStatus \
                MQGetMessageHeaders(const MQMessageHandle messageHandle, \
                                    MQPropertiesHandle *  headersHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetMessageProperties
declaration     MQStatus \
                MQGetMessageProperties(const MQMessageHandle messageHandle, \
                                       MQPropertiesHandle *  propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetMessageReplyTo
declaration     MQStatus \
                MQGetMessageReplyTo(const MQMessageHandle messageHandle, \
                                    MQDestinationHandle * destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetMessageType
declaration     MQStatus \
                MQGetMessageType(const MQMessageHandle messageHandle, \
                                 MQMessageType *       messageType)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetMetaData
declaration     MQStatus \
                MQGetMetaData(const MQConnectionHandle connectionHandle, \
                              MQPropertiesHandle *     propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetPropertyType
declaration     MQStatus \
                MQGetPropertyType(const MQPropertiesHandle propertiesHandle, \
                                  ConstMQString            key, \
                                  MQType *                 propertyType)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetStatusCode
declaration     MQError \
                MQGetStatusCode(const MQStatus status)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetStatusString
declaration     MQString \
                MQGetStatusString(const MQStatus status)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end



function        MQGetStringProperty
declaration     MQStatus \
                MQGetStringProperty(const MQPropertiesHandle propertiesHandle, \
                                    ConstMQString             key, \
                                    ConstMQString *           value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQGetTextMessageText
declaration     MQStatus \
                MQGetTextMessageText(const MQMessageHandle messageHandle, \
                                     ConstMQString *       messageText)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQInitializeSSL
declaration     MQStatus \
                MQInitializeSSL(ConstMQString certificateDatabasePath)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQPropertiesKeyIterationGetNext
declaration     MQStatus \
                MQPropertiesKeyIterationGetNext(const MQPropertiesHandle propertiesHandle, \
                                                ConstMQString * key)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQPropertiesKeyIterationHasNext
declaration     MQBool \
                MQPropertiesKeyIterationHasNext(const MQPropertiesHandle propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQPropertiesKeyIterationStart
declaration     MQStatus \
                MQPropertiesKeyIterationStart(const MQPropertiesHandle propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQReceiveMessageNoWait
declaration     MQStatus \
                MQReceiveMessageNoWait(const MQConsumerHandle consumerHandle, \
                                       MQMessageHandle *      messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQReceiveMessageWait
declaration     MQStatus \
                MQReceiveMessageWait(const MQConsumerHandle consumerHandle, \
                                     MQMessageHandle *      messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQReceiveMessageWithTimeout
declaration     MQStatus \
                MQReceiveMessageWithTimeout(const MQConsumerHandle consumerHandle, \
                                            MQInt32                timeoutMilliSeconds, \
                                            MQMessageHandle *      messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQRecoverSession
declaration     MQStatus \
                MQRecoverSession(const MQSessionHandle sessionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSendMessage
declaration     MQStatus \
                MQSendMessage(const MQProducerHandle producerHandle, \
                              const MQMessageHandle  messageHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSendMessageExt
declaration     MQStatus \
                MQSendMessageExt(const MQProducerHandle producerHandle, \
                                 const MQMessageHandle  messageHandle, \
                                 MQDeliveryMode         msgDeliveryMode, \
                                 MQInt8                 msgPriority, \
                                 MQInt64                msgTimeToLive)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSendMessageToDestination
declaration     MQStatus \
                MQSendMessageToDestination(const MQProducerHandle producerHandle, \
                                           const MQMessageHandle messageHandle, \
                                           const MQDestinationHandle destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSendMessageToDestinationExt
declaration     MQStatus \
                MQSendMessageToDestinationExt(const MQProducerHandle    producerHandle, \
                                              const MQMessageHandle     messageHandle, \
                                              const MQDestinationHandle destinationHandle, \
                                              MQDeliveryMode            msgDeliveryMode, \
                                              MQInt8                    msgPriority, \
                                              MQInt64                   msgTimeToLive)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetDeliveryDelay
declaration     MQStatus \
                MQSetDeliveryDelay(const MQProducerHandle    producerHandle, \
                                   MQInt64                   deliveryDelay)
include         "mqcrt.h"
arch            all
version         SUNW_1.4
end

function        MQGetDeliveryDelay
declaration     MQStatus \
                MQGetDeliveryDelay(const MQProducerHandle    producerHandle, \
                                   MQInt64 *                 deliveryDelay)
include         "mqcrt.h"
arch            all
version         SUNW_1.4
end


function        MQSetBoolProperty
declaration     MQStatus \
                MQSetBoolProperty(const MQPropertiesHandle propertiesHandle, \
                                  ConstMQString            key, \
                                  MQBool                   value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetBytesMessageBytes
declaration     MQStatus \
                MQSetBytesMessageBytes(const MQMessageHandle messageHandle, \
                                       const MQInt8 *        messageBytes, \
                                       MQInt32               messageBytesSize)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetFloat32Property
declaration     MQStatus \
                MQSetFloat32Property(const MQPropertiesHandle propertiesHandle, \
                                     ConstMQString            key, \
                                     MQFloat32                value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetFloat64Property
declaration     MQStatus \
                MQSetFloat64Property(const MQPropertiesHandle propertiesHandle, \
                                     ConstMQString            key, \
                                     MQFloat64                value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetInt16Property
declaration     MQStatus \
                MQSetInt16Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt16                  value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetInt32Property
declaration     MQStatus \
                MQSetInt32Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt32                  value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetInt64Property
declaration     MQStatus \
                MQSetInt64Property(const MQPropertiesHandle propertiesHandle, \
                                   ConstMQString            key, \
                                   MQInt64                  value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetInt8Property
declaration     MQStatus \
                MQSetInt8Property(const MQPropertiesHandle propertiesHandle, \
                                  ConstMQString            key, \
                                  MQInt8                   value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetMessageHeaders
declaration     MQStatus \
                MQSetMessageHeaders(const MQMessageHandle    messageHandle, \
                                    MQPropertiesHandle       headersHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetMessageProperties
declaration     MQStatus \
                MQSetMessageProperties(const MQMessageHandle    messageHandle, \
                                       MQPropertiesHandle       propertiesHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetMessageReplyTo
declaration     MQStatus \
                MQSetMessageReplyTo(const MQMessageHandle     messageHandle, \
                                    const MQDestinationHandle destinationHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetStringProperty
declaration     MQStatus \
                MQSetStringProperty(const MQPropertiesHandle propertiesHandle, \
                                    ConstMQString            key, \
                                    ConstMQString            value)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQSetTextMessageText
declaration     MQStatus \
                MQSetTextMessageText(const MQMessageHandle messageHandle, \
                                     ConstMQString         messageText)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQStartConnection
declaration     MQStatus \
                MQStartConnection(const MQConnectionHandle connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQStatusIsError
declaration     MQBool \
                MQStatusIsError(const MQStatus status)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQStopConnection
declaration     MQStatus \
                MQStopConnection(const MQConnectionHandle connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end


function        MQUnsubscribeDurableMessageConsumer
declaration     MQStatus \
                MQUnsubscribeDurableMessageConsumer(const MQSessionHandle sessionHandle, \
                                                    ConstMQString         durableName)
include         "mqcrt.h"
arch            all
version         SUNW_1.1
end

data            sun_mq_xa_switch
arch            all
version         SUNW_1.3
end


function        mq_xa_open
declaration     int \ 
                mq_xa_open(char *xa_info, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_close
declaration     int \ 
                mq_xa_open(char *xa_info, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_start
declaration     int \ 
                mq_xa_start(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_end
declaration     int \ 
                mq_xa_start(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_complete
declaration     int \ 
                mq_xa_complete(int *handle, int *retval, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_prepare
declaration     int \ 
                mq_xa_prepare(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_rollback
declaration     int \ 
                mq_xa_rollback(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_commit
declaration     int \ 
                mq_xa_commit(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_recover
declaration     int \ 
                mq_xa_recover(XID *xid, long count, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end


function        mq_xa_forget
declaration     int \ 
                mq_xa_forget(XID *xid, int rmid, long flags)
include         "xa.h"
arch            all
version         SUNW_1.3
end

#
#
# Private Intefaces
#
function        MQCreateConnectionExt
declaration     MQStatus \
                MQCreateConnectionExt(MQPropertiesHandle                propertiesHandle, \
                                      ConstMQString                     username, \
                                      ConstMQString                     password, \
                                      ConstMQString                     clientID, \
                                      MQConnectionExceptionListenerFunc exceptionListener, \
                                      void *                            exceptionCallbackData, \
                                      MQCreateThreadFunc                createThreadFunc, \
                                      void *                            createThreadFuncData, \
                                      MQBool                            isXA, \
                                      MQConnectionHandle *              connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQCreateXAConnection
declaration     MQStatus \
                MQCreateXAConnection(MQPropertiesHandle          propertiesHandle, \
                                     ConstMQString                     username, \
                                     ConstMQString                     password, \
                                     ConstMQString                     clientID, \
                                     MQConnectionExceptionListenerFunc exceptionListener, \
                                     void *                            listenerCallbackData, \
                                     MQConnectionHandle *              connectionHandle)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQCloseXAConnection
declaration     MQStatus MQCloseXAConnection(const MQConnectionHandle connectionHandle) 
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQGetLogFileLogLevel
declaration     MQStatus \
                MQGetLogFileLogLevel(MQLoggingLevel * logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQGetStdErrLogLevel
declaration     MQStatus \
                MQGetStdErrLogLevel(MQLoggingLevel * logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQGetCallbackLogLevel
declaration     MQStatus \
                MQGetCallbackLogLevel(MQLoggingLevel * logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQGetLogMask
declaration     MQStatus \
                MQGetLogMask(MQLoggingLevel logLevel, MQInt32 * logMask)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetStdErrLogLevel
declaration     MQStatus \
                MQSetStdErrLogLevel(MQLoggingLevel logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetLogFileLogLevel
declaration     MQStatus \
                MQSetLogFileLogLevel(MQLoggingLevel logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetCallbackLogLevel
declaration     MQStatus \
                MQSetCallbackLogLevel(MQLoggingLevel logLevel)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetLogFileName
declaration     MQStatus \
                MQSetLogFileName(ConstMQString loggingFileName)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetLoggingFunc
declaration     MQStatus \
                MQSetLoggingFunc(MQLoggingFunc  loggingFunc,
                                 void*          callbackData)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetLogMask
declaration     MQStatus \
                MQSetLogMask(MQLoggingLevel logLevel, MQInt32 logMask)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetMaxLogSize
declaration     MQStatus \
                MQSetMaxLogSize(MQInt32 maxLogSize) 
include         "mqcrt.h"
arch            all
version         SUNWprivate
end


function        MQSetMessageArrivedFunc
declaration     MQStatus \
                MQSetMessageArrivedFunc(const MQConsumerHandle consumerHandle, \
                                        MQMessageArrivedFunc   messageCallback, \
                                        void *                 callbackData)
include         "mqcrt.h"
arch            all
version         SUNWprivate
end
