#
# Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

CCLIENT_CLIENT_SRCS_cpp = \
	BytesMessage.cpp      \
	Connection.cpp        \
	Destination.cpp       \
	FlowControl.cpp       \
	Message.cpp           \
	MessageConsumer.cpp   \
	MessageID.cpp         \
	MessageProducer.cpp   \
	ProducerFlow.cpp      \
	PortMapperClient.cpp  \
	ProtocolHandler.cpp   \
	ReadChannel.cpp       \
	PingTimer.cpp         \
	ReadQTable.cpp        \
	MessageConsumerTable.cpp \
	ReceiveQueue.cpp      \
	Session.cpp           \
	XASession.cpp         \
	XIDObject.cpp         \
	SessionQueueReader.cpp\
	SessionMutex.cpp      \
	NSSInitCall.cpp       \
 	TextMessage.cpp       

CCLIENT_CLIENT_SRCS_c =

CCLIENT_CLIENT_OBJS = $(basename $(CCLIENT_CLIENT_SRCS_cpp) \
                                 $(CCLIENT_CLIENT_SRCS_c))
