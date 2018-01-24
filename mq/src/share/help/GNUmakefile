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

PACKAGE         = help
TOPDIR          = ../../..
MAKEDEFS        = $(TOPDIR)/src/buildcfg/Defs.gmk
SUBDIRS         =

include $(MAKEDEFS)

HELP_ZIPFILE=helpfiles.zip
HELP_FILES=en images Master.hs
HELP_DIR=$(TOPDIR)/binary/$(ARCH)/opt/lib/help

all opt:  help.copy

debug: help.debug
	
clean:	help.clean

#
# This target zips up the files listed
# in HELP_FILES and creates a helpfiles.zip file to check in.
#
zipfile:
	@echo "### Making $(HELP_ZIPFILE)"
	if [ -f $(HELP_ZIPFILE) ]; then \
	    $(RM) $(RMFLAGS) $(HELP_ZIPFILE); \
	fi;
	$(ZIP) $(ZIPFLAGS) $(HELP_ZIPFILE) $(HELP_FILES)
	@echo "### Done Making $(HELP_ZIPFILE)"

