#
# Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0, which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause
#

PACKAGE		= 
TOPDIR          = ../../../../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk

DEMOFILES.java = \
		XMLMessageExample.java \
		AckEquivExample.java \
		AsynchQueueExample.java \
		AsynchTopicExample.java \
		BytesMessages.java \
		DurableSubscriberExample.java \
		MessageConversion.java \
		MessageFormats.java \
		MessageHeadersTopic.java \
		ObjectMessages.java \
		RequestReplyQueue.java \
		SampleUtilities.java \
		SenderToQueue.java \
		SynchQueueExample.java \
		SynchTopicExample.java \
		TextListener.java \
		TopicSelectors.java \
		TransactedExample.java

include $(MAKEDEFS)

all optimized : $(DEMOFILES.class)
debug : classes.debug
clean : demo.clean
