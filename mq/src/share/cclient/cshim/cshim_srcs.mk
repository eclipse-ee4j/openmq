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

CCLIENT_CSHIM_SRCS_cpp = \
	iMQBytesMessageShim.cpp   \
	iMQCallbacks.cpp          \
	iMQConnectionShim.cpp     \
	iMQConsumerShim.cpp       \
	iMQDestinationShim.cpp    \
	iMQLogUtilsShim.cpp       \
	iMQMessageShim.cpp        \
	iMQProducerShim.cpp       \
	iMQPropertiesShim.cpp     \
	iMQSSLShim.cpp            \
	iMQSessionShim.cpp        \
	iMQStatusShim.cpp         \
	iMQTextMessageShim.cpp    \
	iMQTypes.cpp              \
	shimUtils.cpp             \
	xaswitch.cpp

CCLIENT_CSHIM_SRCS_c =

CCLIENT_CSHIM_OBJS = $(basename $(CCLIENT_CSHIM_SRCS_cpp) \
                                $(CCLIENT_CSHIM_SRCS_c))

CCLIENT_PUBLIC_HEADERS = \
	mqcrt.h	             \
	mqtypes.h            \
	mqbasictypes.h       \
	mqconnection.h	     \
	mqsession.h          \
	mqproducer.h         \
	mqconsumer.h         \
	mqmessage.h          \
	mqtext-message.h     \
	mqbytes-message.h	 \
	mqdestination.h      \
	mqproperties.h       \
	mqconnection-props.h \
	mqheader-props.h     \
	mqcallback-types.h   \
	mqssl.h              \
	mqstatus.h           \
	mqerrors.h           \
	mqversion.h

