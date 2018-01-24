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
# JMS Object Administration startup script
#

# Specify additional arguments to the JVM here
jvm_args="-Xmx128m"

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
    . $_bin_home/../private/share/lib/$_init_file
else
    echo "Error: Could not find required Message Queue initialization file '$_init_file'"
    exit 1
fi

jvm_args="$jvm_args -Dimq.home=$imq_home -Dimq.varhome=$imq_varhome"

#
# Append CLASSPATH value to _classes if it is set.
#
if [ ! -z "$CLASSPATH" ]; then
    _classes=$imq_sharelibimq_home/imqadmin.jar:$imq_sharelib_home/fscontext.jar:$CLASSPATH
    CLASSPATH=
    export CLASSPATH
else
    _classes=$imq_sharelibimq_home/imqadmin.jar:$imq_sharelib_home/fscontext.jar
fi

# Default external JARs
if [ ! -z "$imq_ext_jars" ]; then
    _classes=$_classes:$imq_ext_jars
fi

_mainclass=com.sun.messaging.jmq.admin.apps.objmgr.ObjMgr

# Needed to locate libimq
#####hpux-dev#####
if [ "$PLATFORM" = HP-UX ] ; then
SHLIB_PATH=$SHLIB_PATH:$imq_libhome; export SHLIB_PATH
else
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$imq_libhome; export LD_LIBRARY_PATH
fi


"$imq_javahome/bin/java" -cp "$_classes" $jvm_args $_mainclass "$@"
