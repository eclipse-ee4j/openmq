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

PACKAGE		= 
TOPDIR          = ../../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk
DOCDEFS 	= $(TOPDIR)/src/buildcfg/Doc.gmk
SUBDIRS         = 

include $(MAKEDEFS)
include $(DOCDEFS)

FILES.jar = jms.jar \
	    imq.jar \
	    imqadmin.jar 
#
# - The jar files are only created at this level
#   because at this point all the necessary files
#   that go into the jar file should be compiled.
# - Examples need to be built after jar file is built (classes.jar).
# - Only the external javadocs are built with the all target.
#
all optimized : $(SUBDIRS) manifest web-inf rar-meta-inf properties_files.copy classes.jar .examples.opt javadocs 

debug : $(SUBDIRS) classes_debug.jar .examples.debug javadocs 

#
# 'clean' target at this level cleans classes, jar files,
# examples, and javadocs.
# Anything below this level will only clean jar files.
#
clean : $(SUBDIRS) jars.clean docs.clean .examples.clean

#
# This docs target will built both internal and external javadocs.
#
docs  : javadocs 

.examples.clean:
	@if [ $(BUILD_TYPE) = RI ]; then \
	    echo " "; \
	else \
	    cd examples; $(MAKE) clean; \
	fi
.examples.opt: 
	@if [ $(BUILD_TYPE) = RI ]; then \
	    echo " "; \
	else \
	    cd examples; $(MAKE); \
	fi
.examples.debug: 
	@if [ $(BUILD_TYPE) = RI ]; then \
	    echo " "; \
	else \
	    cd examples; $(MAKE) debug; \
	fi

