#
# Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

PACKAGE		= javax.xml.messaging
TOPDIR          = ../../../../../..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk

FILES.java = \
            Endpoint.java \
            FactoryFinder.java \
            JAXMException.java \
            JAXMServlet.java \
            OnewayListener.java \
            ProviderConnection.java \
            ProviderConnectionFactory.java \
            ProviderMetaData.java \
            ReqRespListener.java \
            URLEndpoint.java


include $(MAKEDEFS)

all:		classes.dir classes $(SUBDIRS)
clean: 		classes.clean $(SUBDIRS)
debug: 		classes.dir classes.debug $(SUBDIRS)
optimized: 	classes.optimized $(SUBDIRS)

