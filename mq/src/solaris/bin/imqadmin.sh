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
# JMS Administration Console startup script
#

_bin_home=`/usr/bin/dirname $0`
_init_file="imqinit"

# Source initialization file. This intitializes the imq_* variables
if [ -f $_bin_home/../share/lib/imq/$_init_file ]; then
    # Bundled location
    .   $_bin_home/../share/lib/imq/$_init_file
elif [ -f $_bin_home/../lib/$_init_file ]; then
    # Unbundled location
    . $_bin_home/../lib/$_init_file
elif [ -f $_bin_home/../private/share/lib/$_init_file ]; then
    # Linux "standard" location
    . $_bin_home/../private/share/lib/$_init_file
#####hpux-dev#####
elif [ -f $_bin_home/../private/share/lib/$_init_file ];then
    # HP-UX "standard" location
    . $_bin_home/../private/share/lib/$_init_file
else
    echo "Error: Could not find required Message Queue initialization file '$_init_file'"
    exit 1
fi

#location of the java help jar on solaris 9
helpjar_location=/usr/j2se/opt/javahelp/lib/jhall.jar

#location of the java help jar on linux
# We get it for free in the jdk only if the JDK was installed first.
helpjar_location_linux1=/usr/java/javahelp2.0/javahelp/lib/jhall.jar
helpjar_location_linux2=/usr/java/packages/javax.help-2.0/javahelp/lib/jhall.jar

#####hpux-dev#####
helpjar_location_HPUX=/opt/sun/share/lib/javahelpruntime/javahelp/lib/jhall.jar

# Specify additional arguments to the JVM here
jvm_args="-Xmx128m"

jvm_args="$jvm_args -Dimq.home=$imq_home -Dimq.varhome=$imq_varhome -Dimq.libhome=$imq_sharelibimq_home"

helpjar=$imq_sharelibimq_home/jhall.jar
if [ -f $helpjar_location ]; then
    helpjar=$helpjar_location;
elif [ -f $helpjar_location_linux1 ]; then
    helpjar=$helpjar_location_linux1;
elif [ -f $helpjar_location_linux2 ]; then
    helpjar=$helpjar_location_linux2;
#####hpux-dev#####
elif [ -f $helpjar_location_HPUX ]; then
    helpjar=$helpjar_location_HPUX;
fi

#
# Append CLASSPATH value to _classes if it is set.
#
if [ ! -z "$CLASSPATH" ]; then
    _classes=$imq_sharelibimq_home/imqadmin.jar:$imq_sharelib_home/fscontext.jar:$helpjar:$imq_sharelibimq_home/help:$CLASSPATH
    CLASSPATH=
    export CLASSPATH
else
    _classes=$imq_sharelibimq_home/imqadmin.jar:$imq_sharelib_home/fscontext.jar:$helpjar:$imq_sharelibimq_home/help
fi

# Default external JARs
if [ ! -z "$imq_ext_jars" ]; then
    _classes=$_classes:$imq_ext_jars
fi

_mainclass=com.sun.messaging.jmq.admin.apps.console.AdminConsole

"$imq_javahome/bin/java" -cp "$_classes" $jvm_args $_mainclass "$@"
