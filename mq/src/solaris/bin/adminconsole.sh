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
# MQ Administration Console startup script: Developer Edition
#
# This is a a version of the iMQ Administration Console startup 
# script that works when run in the "binary" directory (as opposed
# to "dist"). It uses the loose class files and not the jars.
#
# Parse Arguments 
#
#  -imqhome -> sets imq home
#  -imqvarhome -> sets imq home
#  -javahome -> sets javahome
#
# Note: This script fails if you specify any of the options above
# without any arguments e.g.
#	config -imqhome
# (i.e. did not specify a imqhome value)
#

# Specify additional arguments to the JVM here
jvm_args="-Xmx128m"

bin_home=`dirname $0`

imq_home=$bin_home/..
imq_external=${JMQ_EXTERNAL:-/net/jpgserv/export/jmq/external}
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

#
# Save -javahome, -imqhome, -imqvarhome, -imqext
# arg values without recreating the $args string
# so that args with spaces work correctly.
#
javahomenext=false
imqhomenext=false
imqvarhomenext=false
imqextnext=false

for opt in $*
do
  if [ $javahomenext = true ]
  then
    javahome=$opt
    javahomenext=false
  elif [ $imqhomenext = true ]
  then
    imq_home=$opt
    imqhomenext=false
  elif [ $imqvarhomenext = true ]
  then
    imq_varhome=$opt
    imqvarhomenext=false
  elif [ $imqextnext = true ]
  then
    imq_external=$opt
    imqextnext=false
  elif [ $opt = -javahome ]
  then
    javahomenext=true;
  elif [ $opt = -imqhome ]
  then
    imqhomenext=true;
  elif [ $opt = -imqvarhome ]
  then
    imqvarhomenext=true;
  elif [ $opt = -imqext ]
  then
    imqextnext=true;
  fi
done

javacmd=$javahome/bin/$javacmd

jvm_args="$jvm_args -Dimq.home=$imq_home"

#_ext_classes=$imq_external/jndifs/lib/fscontext.jar:../../../../src/buildcfg/tools/ri/javahelp/jhall.jar

_ext_classes=$dependlibs/javax.jms-api.jar:$imq_home/../../share/opt/classes:$dependlibs/grizzly-framework.jar:$dependlibs/grizzly-portunif.jar:$dependlibs/glassfish-api.jar:$dependlibs/hk2-api.jar:$dependlibs/javax.transaction-api.jar:$dependlibs/jhall.jar:$dependlibs/fscontext.jar:$dependlibs/audit.jar:$dependlibs/bdb_je.jar
#
# Append CLASSPATH value to _classes if it is set.
#
if [ ! -z "$CLASSPATH" ]; then
    _classes=$imq_home/../../share/opt/classes:$imq_home/lib/help:$_ext_classes:$CLASSPATH
    CLASSPATH=
    export CLASSPATH
else
    _classes=$imq_home/../../share/opt/classes:$imq_home/lib/help:$_ext_classes
fi

_mainclass=com.sun.messaging.jmq.admin.apps.console.AdminConsole

$javacmd -cp $_classes $jvm_args $_mainclass "$@"
