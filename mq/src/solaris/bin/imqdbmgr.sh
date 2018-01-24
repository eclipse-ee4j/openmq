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
# Message Queue Database Administration startup script
#
# Script specific properties:
#   -javahome <path>	Use <path> as the location of the Java runtime
#

# Specify additional arguments to the JVM here
_def_jvm_args="-Xmx128m"

_bin_home=`/usr/bin/dirname $0`
_init_file="imqinit"

# Source initialization file. This intitializes the imq_* variables
if [ -f $_bin_home/../share/lib/imq/$_init_file ]; then
    # bundled location
    . $_bin_home/../share/lib/imq/$_init_file
elif [ -f $_bin_home/../lib/$_init_file ]; then
    # unbundled location
    . $_bin_home/../lib/$_init_file
elif [ -f $_bin_home/../private/share/lib/$_init_file ]; then
    # Linux "standard" location
    . $_bin_home/../private/share/lib/$_init_file
#####hpux-dev#####
elif [ -f $_bin_home/../private/share/lib/$_init_file ]; then
    # HP-UX "standard" location
    $_bin_home/../private/share/lib/$_init_file
else
    echo "Error: Could not find required Message Queue initialization file '$_init_file'"
    exit 1
fi

# Parse command line arguments. We eat these so they are not passed to dbmgr
while [ $# != 0 ]; do
  case "$1" in
    -verbose) _verbose=true; shift 1;;
    -javahome) shift 2;;
    -vmargs) shift; _jvm_args="$1 $_jvm_args"; shift ;;
    *)  args="$args $1"; shift  ;;
  esac
done

# classes needed by imqdbmgr
_classes=$imq_sharelibimq_home/imqbroker.jar

# Default external JARs
if [ ! -z "$imq_ext_jars" ]; then
    _classes=$_classes:$imq_ext_jars
fi

# Additional classes possibly needed for JDBC provider
_classes=$_classes:$imq_sharelibimq_home/ext
# Put all jar and zip files in $imq_varhome/lib in the classpath
for file in $imq_sharelibimq_home/ext/*.jar $imq_sharelibimq_home/ext/*.zip; do
    if [ -r "$file" ]; then
	_classes=$_classes:$file
    fi
done

#####hpux-dev#####
# On Linux and HP-UX they may be here (as of 3.6)
_classes=$_classes:$imq_sharelib_home/ext
# Put all jar files in $IMQ_SHARELIB_HOME/ext in our CLASSPATH
for _file in $imq_sharelib_home/ext/*.jar $imq_sharelib_home/ext/*.zip; do
    if [ -r "$_file" ]; then
        _classes=$_classes:$_file
    fi
done

# imqdbmgr's main class
_mainclass=com.sun.messaging.jmq.jmsserver.persist.jdbc.DBTool

# setup arguments to the JVM
jvm_args="$_def_jvm_args $_jvm_args -Dimq.home=$imq_home -Dimq.varhome=$imq_varhome -Dimq.libhome=$imq_sharelibimq_home -Dimq.etchome=$imq_etchome"

# Needed to locate libimq
#####hpux-dev#####
PLATFORM=`uname`
if [ "$PLATFORM" = HP-UX ] ; then
SHLIB_PATH=$SHLIB_PATH:$imq_libhome; export SHLIB_PATH
else
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$imq_libhome; export LD_LIBRARY_PATH
fi

# run imqdbmgr
"$imq_javahome/bin/java" -cp $_classes $jvm_args $_mainclass $args

