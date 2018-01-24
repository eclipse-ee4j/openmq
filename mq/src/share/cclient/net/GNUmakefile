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

PACKAGE     = cclient/net
TOPDIR      = ../../../..
MAKEDEFS    = $(TOPDIR)/src/buildcfg/Defs.gmk

include net_srcs.mk
CPPFILES.cpp = $(CCLIENT_NET_SRCS_cpp)
CPPFILES.c = $(CCLIENT_NET_SRCS_c)

include $(MAKEDEFS)

COMPILE.cpp = $(CCLIENT_CC)
CPLUSFLAGS = $(CCLIENT_CPLUSFLAGS) $(CCLIENT_INCLUDES)


all optimized:  $(FILES.o)
debug: obj.debug $(FILES.o)
clean: obj.clean

