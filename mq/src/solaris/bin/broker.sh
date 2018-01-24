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
# Broker startup script: Developer Edition
#
# This is a a version of the broker startup script that works when
# run in the "binary" directory (as opposed to "dist"). It uses the
# loose class files and not the jars
#
# Parse Arguments 
#
#  -jmqhome -> sets jmq home
#  -jmqvarhome -> sets jmq var home
#  -javahome -> sets javahome
#

#def_jvm_args="-Djava.compiler=NONE -Xms192m -Xmx192m -Xss192k";
PLATFORM=`uname`

if [ $PLATFORM = Linux ]; then
def_jvm_args="-Xms192m -Xmx192m -Xss256k";
else
def_jvm_args="-Xms192m -Xmx192m -Xss192k";
fi
# We will add vendor-specific JVM flags below

bin_home=`dirname $0`

imq_home=$bin_home/..
imq_varhome=$bin_home/../var
dependlibs=$bin_home/../../../share/opt/depend

javacmd=java

# #####hpux-dev#####
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
    -jmqhome) shift; imq_home=$1; shift ;;
    -jmqvarhome) shift; imq_varhome=$1 ; jvm_args="$jvm_args -Dimq.varhome=$imq_varhome"; shift  ;;
    -varhome) shift; imq_varhome=$1 ; jvm_args="$jvm_args -Dimq.varhome=$imq_varhome"; shift  ;;
    -javahome) shift;  javahome=$1; shift;;
    -jrehome) shift; javahome=$1; shift;;
    -vm) shift; jvm_args="$jvm_args $1"; shift ;;
    -vmargs) shift; jvm_args="$jvm_args $1"; shift ;;
    -managed) shift ;; #ignore JMSRA testing
    *)  args="$args $1"; shift  ;;
  esac
done

javacmd=$javahome/bin/$javacmd

# Add  vendor-specific JVM flags
$javacmd -version 2>&1 | grep JRockit > /dev/null
if [ $? -eq 0 ]; then
   # Add JRockit-specific JVM flags
   jvm_args="$jvm_args -XgcPrio:deterministic"
else
   # Add Sun-specific JVM flags
   jvm_args="$jvm_args -XX:MaxGCPauseMillis=5000"
fi

jvm_args="$def_jvm_args $jvm_args -Dimq.home=$imq_home"

_classes=$dependlibs/javax.jms-api.jar:$imq_home/../../share/opt/classes:$dependlibs/grizzly-framework.jar:$dependlibs/grizzly-portunif.jar:$dependlibs/glassfish-api.jar:$dependlibs/hk2-api.jar:$dependlibs/grizzly-http.jar:$dependlibs/grizzly-http-server.jar:$dependlibs/grizzly-http-servlet.jar:$dependlibs/javax.servlet-api.jar:$dependlibs/grizzly-websockets.jar:$dependlibs/javax.json.jar:$dependlibs/javax.transaction-api.jar:$dependlibs/jhall.jar:$dependlibs/fscontext.jar:$dependlibs/audit.jar:$dependlibs/bdb_je.jar

# Additional classes possibly needed for JDBC provider
_classes=$_classes:$imq_home/lib/ext
# Put all jar and zip files in $imq_home/lib/ext in the classpath
for file in $imq_home/lib/ext/*.jar $imq_home/lib/ext/*.zip; do
    if [ -r "$file" ]; then
	_classes=$_classes:$file
    fi
done

# Needed to locate libimq
#####hpux-dev#####
if [ "$PLATFORM" = HP-UX ] ; then
SHLIB_PATH=$SHLIB_PATH:$imq_home/lib; export SHLIB_PATH
else
LD_LIBRARY_PATH=$imq_home/lib:$LD_LIBRARY_PATH; export LD_LIBRARY_PATH
fi

# Restart loop. If the Broker exits with 255 then restart it
restart=true
while [ $restart ]; do
    $javacmd -cp $_classes $jvm_args com.sun.messaging.jmq.jmsserver.Broker $args
    status=$?

    if [ $status -eq 255 ]; then
	# We pause to avoid pegging system if we get here accidentally
	sleep 1
    else
	restart=
    fi
done

exit $status
