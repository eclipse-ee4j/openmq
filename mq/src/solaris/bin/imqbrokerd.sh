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
# Broker startup script
# Script specific properties:
#   -javahome <path>	Use <path> as the location of the Java runtime
#   -bgnd		Start child proces (JVM) in the background. This
#			lets this script catch signals and terminate
#			the VM.
#

#
# Terminate the broker
#
shutdown_broker() {
    # The broker's shutdown hooks will make sure it exits cleanly
    if [ -n "$_javapid" ]; then
        kill -TERM $_javapid
    fi
}

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

# create $imq_varhome/instances directory if it does not exist
# and set permissions to 1777
# (this operation used to be done by installer)
if [ ! -d "$imq_varhome/instances" ]; then
   mkdir -p $imq_varhome/instances
   chmod 1777 $imq_varhome/instances
fi

# Specify default arguments to the JVM here
if [ "$imq_os" = "Linux" ]; then
_def_jvm_args="-Xms192m -Xmx192m -Xss256k"
else
_def_jvm_args="-Xms192m -Xmx192m -Xss192k"
fi
# We will add vendor-specific JVM flags below

_command=$0

# Classes need by the broker
_classpath=$imq_sharelibimq_home/imqbroker.jar:$imq_sharelibimq_home/imqutil.jar:$imq_sharelibimq_home/jsse.jar:$imq_sharelibimq_home/jnet.jar:$imq_sharelibimq_home/jcert.jar:/usr/lib/audit/Audit.jar

#
# Jars needed by JES Monitoring Framework
#
jesmf_jars=""
if [ $imq_os = "SunOS" ]; then
    jesmf_jars=/opt/SUNWjdmk/5.1/lib/jdmkrt.jar:/opt/SUNWmfwk/lib/mfwk_instrum_tk.jar
elif [ "$imq_os" = "Linux" ]; then
    jesmf_jars=/opt/sun/mfwk/share/lib/jdmkrt.jar:/opt/sun/mfwk/share/lib/mfwk_instrum_tk.jar
else
    jesmf_jars=/opt/sun/mfwk/share/lib/jdmkrt.jar:/opt/sun/mfwk/share/lib/mfwk_instrum_tk.jar
fi

_classpath=$_classpath:$jesmf_jars

# Default external JARs
if [ ! -z "$imq_ext_jars" ]; then
    _classpath=$_classpath:$imq_ext_jars
fi

# Additional classes possibly needed for JDBC provider, etc.
_classpath=$_classpath:$imq_sharelibimq_home/ext
# Put all jar files in $IMQ_SHARELIB_HOME/ext in our CLASSPATH
for _file in $imq_sharelibimq_home/ext/*.jar $imq_sharelibimq_home/ext/*.zip; do
    if [ -r "$_file" ]; then
        _classpath=$_classpath:$_file
    fi
done

# On Linux they may be here (as of 3.6)
_classpath=$_classpath:$imq_sharelib_home/ext
# Put all jar files in $IMQ_SHARELIB_HOME/ext in our CLASSPATH
for _file in $imq_sharelib_home/ext/*.jar $imq_sharelib_home/ext/*.zip; do
    if [ -r "$_file" ]; then
        _classpath=$_classpath:$_file
    fi
done

# Broker's main class
_mainclass=com.sun.messaging.jmq.jmsserver.Broker;

_background=""
_verbose=""
_forceclientvm=""
_autorestart=""
_managed=""

# Parse command line arguments
while [ $# != 0 ]; do
  case "$1" in
    -bgnd) _background="true"; _args="$_args $1"; shift ;;
    -autorestart) _autorestart="true"; shift ;;
    -managed) _managed="true"; shift ;;
    -verbose) _verbose="true"; shift ;;
    -clientvm) _forceclientvm="true"; shift ;;
    -vmargs) shift; _jvm_args="$1 $_jvm_args"; shift ;;
    *)  _args="$_args $1"; shift  ;;
  esac
done

# Check to see if we can run with the -server flag
#
# If user is forcing client vm then don't use server VM
if [ -n "$_forceclientvm" ] ; then
    imq_servervm=""
fi


# If the script is killed make sure we shutdown the Broker's Java process
trap "shutdown_broker" TERM INT QUIT 


#####hpux-dev#####
PLATFORM=`uname`

# Set LD_LIBRARY_PATH to locate libimq
# If the -d64 option was passed to the JVM and we are on Solaris
# then adjust the library path to find the 64 bit version of libimqutil first.
# Else use the 32bit version
_d64=`echo $_jvm_args $_def_jvm_args | grep "\-d64"`
if [ "$_d64" ]; then
  if [ $imq_os = "SunOS" ]; then
    if [ "$imq_proc" = "sparc" ]; then
        LD_LIBRARY_PATH=$imq_libhome/sparcv9:$LD_LIBRARY_PATH
        export LD_LIBRARY_PATH
    elif [ "$imq_proc" = "i386" ]; then
        LD_LIBRARY_PATH=$imq_libhome/amd64:$LD_LIBRARY_PATH
        export LD_LIBRARY_PATH
    fi
  fi
  # For the 64bit JVM we need a larger stack size
  _def_jvm_args="$_def_jvm_args -Xss256k"
else
    if [ "$PLATFORM" = HP-UX ] ; then
    SHLIB_PATH=$SHLIB_PATH:$imq_libhome
    export SHLIB_PATH
    _def_jvm_args="$_def_jvm_args -Xss256k"
    else
    LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$imq_libhome
    export LD_LIBRARY_PATH
    fi
fi

# Add vendor-specific JVM flags
$imq_javahome/bin/java -version 2>&1 | grep JRockit > /dev/null
if [ $? -eq 0 ]; then
   # Add JRockit-specific JVM flags
   _jvm_args="$_jvm_args -XgcPrio:deterministic"
else
   # Add Sun-specific JVM flags
   _jvm_args="$_jvm_args -XX:MaxGCPauseMillis=5000"
fi


# Setup arguments to the JVM
_jvm_args="$_def_jvm_args $_jvm_args -Dimq.home=$imq_home -Dimq.varhome=$imq_varhome -Dimq.etchome=$imq_etchome -Dimq.libhome=$imq_sharelibimq_home"

# The command we use to run the broker
_brokercmd="$imq_javahome/bin/java $imq_servervm -cp $_classpath $_jvm_args $_mainclass $_args"


if [ ! -z "$_verbose" ]; then
    if [ "$PLATFORM" = HP-UX ] ; then
    echo " SHLIB_PATH  : $SHLIB_PATH"
    else
    echo "  LD_LIBRARY_PATH  : $LD_LIBRARY_PATH"
    fi
    echo "Command:"
    echo "    $_brokercmd"
fi

# Set umask so any files created by broker are only accessible
# by the user running broker
umask 077

# Restart loop. If the Broker exits with 255 then restart it.
# If autorestart is true and the broker exits abnormally (ie anything other
# then 0, 1, 129 (SIGHUP), 130 (SIGINT), or 143 (SIGTERM)) then restart.
_restart=true
while [ $_restart ]; do
    _javapid=""
    if [ -n "$_background" ]; then
        $_brokercmd &
        # If we are running in the background use wait to wait for child
        _javapid=$!
        wait $_javapid
        _status=$?
    else
        $_brokercmd
        _status=$?
    fi

    if [ $_status -eq 255 ]; then
        # 255 means restart
        if [ ! -z "$_managed" ]; then
           # Managed by Glassfish. Don't restart here, let Glassfish do it
           logger -t imqbrokerd -p daemon.notice imqbrokerd completed with status 255 - letting Glassfish restart
           _restart=
        else
           # Managed by this script. Restart broker
           sleep 0
        fi
	# We pause to avoid pegging system if we get here accidentally
	sleep 1
    elif [ $_status -eq 0 -o $_status -eq 1 -o $_status -eq 129 -o $_status -eq 130 -o $_status -eq 143 ]; then
        # "Normal" termination. Don't restart
        _restart=
    elif [ ! -z "$_autorestart" ]; then
        # Abnormal termination and autorestart specified 
        if [ ! -z "$_managed" ]; then
           # Managed by Glassfish. Ignore _autorestart 
           sleep 0
        else
           # Managed by this script. Restart broker
        logger -t imqbrokerd -p daemon.notice Message Queue broker terminated abnormally -- restarting.
        sleep 2
        fi
    else
        # Abnormal termination but autorestart not specified - simply exit
	_restart=
    fi
done

exit $_status
