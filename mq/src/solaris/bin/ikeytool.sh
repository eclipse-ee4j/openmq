#!/bin/sh
#
# Copyright (c) 2000, 2018 Oracle and/or its affiliates. All rights reserved.
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
# imqkeytool is a wrapper script for JDK Keytool for generation of a keystore
# and a self signed certificate for use with SSL.
#
# To generate keystore and self signed certificate for the broker
# usage: imqkeytool [-broker]
#
# To generate keystore and a self-signed certificate for the HTTPS tunnel
# servlet
# usage: imqkeytool -servlet <keystore location>
#

#
# print usage
#
usage() {
    echo ""
    echo "usage:"
    echo "imqkeytool [-broker]"
    echo "   generates a keystore and self-signed certificate for the broker"
    echo ""
    echo "imqkeytool -servlet <keystore_location>"
    echo "   generates a keystore and self-signed certificate for the HTTPS"
    echo "   tunnel servlet, keystore_location specifies the name and location"
    echo "   of the keystore file"
    echo ""
    exit 1
}

# file is stored (by default) in /etc/imq/keystore
#
# The admin script looks in a few set locations for
# java e.g. it doesnt try to automatically pick up
# new versions of the JDK on linux (unlike imqbrokerd)
#
# The scripts do not verify the specific version of
# the JDK (unlike imqbrokerd)

bin_home=`/usr/bin/dirname $0`

# set keystore_home
imq_home=$bin_home/..
keystore_home=$imq_home/etc

# ######hpux-dev#####
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

cmd=broker
while [ $# != 0 ]; do
  case "$1" in
    -broker) cmd="broker"; shift ;;
    -servlet)
	cmd="servlet"
	if [ $# = 1 ]; then
	    echo "Please specify keystore location for the -servlet option"
	    usage
	else
	    keystore=$2
	    shift 2
	fi
	;;
    -javahome) javahome=$2; shift 2;;
    *)  usage ;;
  esac
done

if [ $cmd = broker ]; then
    #
    # generate keystore and certificate for the broker
    #
    echo "Generating keystore for the broker ..."

    if [ ! -w $keystore_home ]; then
    	/bin/echo "You do not have permission to create the keystore file in"
    	/bin/ls -ld $keystore_home
    	exit 1
    fi

    keystore=$keystore_home/keystore
    echo Keystore=$keystore

    # Generate the keypair. This also wraps them in a self-signed certificate
    $javahome/bin/keytool -genkey -keyalg "RSA" -alias imq -keystore $keystore -v 
    status=$?

    #Make sure keystore is readable by everybody so broker can read it
    chmod a+r $keystore

elif [ $cmd = servlet ]; then
    #
    # generate keystore and certificate for the HTTPS tunnel servlet
    #
    echo "Generating keystore for the HTTPS tunnel servlet ..."

    keystoredir=`/usr/bin/dirname $keystore`
    if [ ! -w $keystoredir ]; then
    	/bin/echo "You do not have permission to create the keystore file in"
    	/bin/ls -ld $keystoredir
    	exit 1
    fi

    echo Keystore=$keystore

    # Generate the keypair. This also wraps them in a self-signed certificate
    $javahome/bin/keytool -genkey -keyalg "RSA" -alias imqservlet -keystore $keystore -v 
    status=$?
    if [ $status = 0 ]; then
    	echo "Make sure the keystore is accessible and readable by"
    	echo "the HTTPS tunnel servlet."
    fi

else
    #
    # print usage
    #
    usage
fi
exit $status
