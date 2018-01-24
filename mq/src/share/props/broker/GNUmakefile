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

PACKAGE         = broker
TOPDIR          = ../../../..
MAKEDEFS        = $(TOPDIR)/src/buildcfg/Defs.gmk
SUBDIRS         =

include $(MAKEDEFS)

all opt:  props.copy default_linux.properties

debug: props.debug

clean:	props.clean

# Generate Linux properties from default.properties
# For now we just remove the syslog log handler since we don't yet support
# it for Linux
default_linux.properties: default.properties
	@if [ $(PLATFORM) != "Windows_NT" ]; then \
	    cat default.properties | sed -e '1,$$s/,syslog//' > $(CONFIGPROPDIR)/broker/default_linux.properties; \
	fi;
