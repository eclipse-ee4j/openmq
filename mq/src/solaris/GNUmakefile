#
# Copyright (c) 2003, 2017 Oracle and/or its affiliates. All rights reserved.
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

TOPDIR		= ../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk
#
# Add to SUBDIRS here for non-solaris 
# otherwise add to SUBDIRS below for solaris.
#
SUBDIRS 	= bin 

#
# Add to SUBDIRS here for solaris.
#
UNIXNAME:=$(shell uname)
ifeq "$(UNIXNAME)"  "SunOS"
    SUBDIRS = bin etc native
endif

#####hpux-dev#####
ifeq ($(UNIXNAME),HP-UX)
    SUBDIRS = bin etc native
endif

ifeq ($(UNIXNAME),AIX)
    SUBDIRS = bin etc native
endif

include $(MAKEDEFS)

all optimized debug clean: $(SUBDIRS)
	@if [ "$(UNIXNAME)" = "SunOS" ]; then \
	  if [ "`uname -p`" = "i386" -a "`uname -r`" = "5.10" ]; then \
	    $(MAKE) DATA_MODEL_64=1 -C native $@; \
	  fi; \
	  if [ "`uname -p`" = "sparc" ]; then \
	    $(MAKE) DATA_MODEL_64=1 -C native $@; \
	  fi; \
	fi

#
# This builds 64-bit libimqutil.
# This target should only be used on 64-bit systems.
#
c-lib64: 
	@if [ "$(UNIXNAME)" = "SunOS" ]; then \
	    $(MAKE) DATA_MODEL_64=1 -C native ; \
	fi;

