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
TOPDIR          = ../../../../../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk

DEMOFILES.java = \
		SelectorProducerExample.java \
		SelectorConsumerExample.java

include $(MAKEDEFS)

all optimized : $(DEMOFILES.class)
debug : classes.debug
clean : demo.clean
