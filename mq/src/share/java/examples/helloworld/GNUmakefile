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
DOCDEFS 	= $(TOPDIR)/src/buildcfg/Doc.gmk
#
# If you add an example, add the subdirectory here:
#
SUBDIRS         = helloworldmessage helloworldmessagejndi

include $(MAKEDEFS)

all clean debug optimized : $(SUBDIRS)
