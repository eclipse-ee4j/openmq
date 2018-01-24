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
# MQ Postinstall Script for Solaris Evaluation 
#
# $1 - First parameter is the installation root of the entire iAS dist.
#
INSTALL_ROOT=$1
if [ "$INSTALL_ROOT" = "" ];then
    echo "Usage: $0 <Installation_Root_Dir>";
    exit 1;
fi;
IMQ_ROOT=$INSTALL_ROOT/imq

#
# Save out $2 (JDK_HOME) to var.init/jdk.env for scripts to pick up.
#
#JDK_HOME=$2
#if [ "${JDK_HOME}x" = "x" ];then
#    cat /dev/null> "$IMQ_ROOT/var/jdk.env"
#else
#    echo $JDK_HOME> "$IMQ_ROOT/var/jdk.env"
#fi;

/bin/mkdir -p "$IMQ_ROOT/var"
/bin/chmod -f 0755 "$IMQ_ROOT/var"
/bin/mkdir -p "$IMQ_ROOT/var/instances"
/bin/chmod -f 01777 "$IMQ_ROOT/var/instances"

# Copy security files only they do not already exist.
#cd "$IMQ_ROOT/var/security";
#if [ ! -s "$IMQ_ROOT/var/security/accesscontrol.properties" ];
#then
#	echo "Copying accesscontrol.properties file in "
#	echo "$IMQ_ROOT/var/security"
#	/bin/cp "$IMQ_ROOT/var.init/security/accesscontrol.properties" .
#	/bin/chmod 644 "$IMQ_ROOT/var/security/accesscontrol.properties";
#fi

#if [ ! -s "$IMQ_ROOT/var/security/passwd" ];
#then
#	echo "Copying passwd files in "
#	echo "$IMQ_ROOT/var/security"
#	/bin/cp "$IMQ_ROOT/var.init/security/passwd" .
#	/bin/chmod 644 "$IMQ_ROOT/var/security/passwd";
#fi

exit 0

