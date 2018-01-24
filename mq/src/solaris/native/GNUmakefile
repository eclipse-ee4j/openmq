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

PACKAGE		= native
TOPDIR          = ../../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk

FILES.c = \
		com_sun_messaging_jmq_jmsserver_audit_impl_BSMAuditSession.c \
		com_sun_messaging_jmq_util_log_SysLog.c \
		com_sun_messaging_jmq_util_Rlimit.c \
		com_sun_messaging_jmq_util_Password.c

DLL_NAME=imqutil
VERSION_NUM=1

include $(MAKEDEFS)

ifeq ($(PLATFORM), Windows_NT)
CLASSPATH:=$(_SHARE_CLASSESDIR);$(_NUCLEUS_JAR)
else
CLASSPATH:=$(_SHARE_CLASSESDIR):$(_NUCLEUS_JAR)
endif

all optimized:	FILES_audit.h FILES_util.h $(FILES.o) $(SUBDIRS) $(DLL)
debug: obj.debug $(FILES.o) dll.debug $(DLL)
clean: obj.clean dll.clean

