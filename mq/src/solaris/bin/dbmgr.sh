#!/bin/sh
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

#
# MQ Database Administration startup script: Developer Edition
#
# This is a a version of the database administration startup script
# that works when run in the "binary" directory (as opposed to "dist").
# It uses the loose class files and not the jars
#
# Parse Arguments 
#
#  -imqhome -> sets imq home
#  -imqvarhome -> sets imq var home
#  -javahome -> sets javahome
#

def_jvm_args="-Xmx128m"

bin_home=`dirname $0`

imq_home=$bin_home/..
imq_varhome=$bin_home/../var
dependlibs=$bin_home/../../../share/opt/depend

javacmd=java
# #####hpux-dev#####
PLATFORM=`uname`
ARCH=`uname -p`
if [ $PLATFORM = HP-UX ]; then
    javahome=${_JAVA_HOME:-/opt/java6}
elif [ $PLATFORM = Darwin ]; then
    javahome=${_JAVA_HOME:-/Library/Java/Home}
elif [ $PLATFORM = AIX ]; then
    javahome=${_JAVA_HOME:-/usr/java6}
elif [ $PLATFORM = SunOS ]; then
    javahome=${_JAVA_HOME:-/usr/jdk/latest}
elif [ $PLATFORM = Linux ]; then
    javahome=${_JAVA_HOME:-/usr/java/latest}
fi

while [ $# != 0 ]; do
  case "$1" in
    -imqhome) imq_home=$2; shift 2;;
    -imqvarhome) imq_varhome=$2 ; jvm_args="$jvm_args -Dimq.varhome=$imq_varhome"; shift 2;;
    -javahome) javahome=$2; shift 2;;
    -vmargs) shift; jvm_args="$jvm_args $1"; shift ;;
    *)  args="$args $1"; shift  ;;
  esac
done

javacmd=$javahome/bin/$javacmd

jvm_args="$def_jvm_args $jvm_args -Dimq.home=$imq_home"

#_classes=$imq_home/../../share/opt/classes
_classes=$imq_home/../../share/opt/classes:$dependlibs/javax.jms-api.jar:$dependlibs/grizzly-framework.jar:$dependlibs/grizzly-portunif.jar:$dependlibs/glassfish-api.jar:$dependlibs/hk2-api.jar:$dependlibs/javax.transaction-api.jar:$dependlibs/jhall.jar:$dependlibs/fscontext.jar:$dependlibs/audit.jar:$dependlibs/bdb_je.jar

# Additional classes possibly needed for JDBC provider
_classes=$_classes:$imq_home/lib/ext
# Put all jar and zip files in $imq_home/lib/ext in the classpath
for file in $imq_home/lib/ext/*.jar $imq_home/lib/ext/*.zip; do
    if [ -r "$file" ]; then
	_classes=$_classes:$file
    fi
done

_mainclass=com.sun.messaging.jmq.jmsserver.persist.jdbc.DBTool

# Needed to locate libimq
#####hpux-dev#####
if [ "$PLATFORM" = HP-UX ] ; then
SHLIB_PATH=$SHLIB_PATH:$imq_home/lib; export SHLIB_PATH
else
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$imq_home/lib; export LD_LIBRARY_PATH
fi

$javacmd -cp $_classes $jvm_args $_mainclass $args
