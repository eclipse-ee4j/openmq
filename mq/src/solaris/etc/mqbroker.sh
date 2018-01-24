#!/sbin/sh
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
# Message Queue Broker Service method script
#
# This script starts the Message Queue broker service 
# within SMF.
#
# To debug this script, set the environmental variable
# DEBUG to 1 (true)
#
# e.g.
# env DEBUG=1 /lib/svc/method start
#
# This file is installed as:
#     /lib/svc/method start
#
# Note: To modify parameters passed to imqbrokerd, do not
# edit this script.  Instead, use svccfg(1m) to modify the
# SMF repository.  For example:
#
# # svccfg
# svc:> select svc:/application/sun/mq/mqbroker
# svc:/application/sun/mq/mqbroker> setprop options/broker_args="-loglevel DEBUGHIGH"
# svc:/application/sun/mq/mqbroker> exit
#
#

. /lib/svc/share/smf_include.sh

###### START LOCAL CONFIGURATION
# You may wish to modify these variables to suit your local configuration

# IMQ_HOME   Location of the Message Queue installation
IMQ_HOME=/
export IMQ_HOME

# IMQ_VARHOME
# Set IMQ_VARHOME if you wish to designate an alternate
# location for storing broker specific data like:
#	- persistent storage
#	- SSL certificates
#	- user passwd database
#
# IMQ_VARHOME=/var/opt/SUNWjmq
# export IMQ_VARHOME

# BROKER_OPTIONS	Default Options to pass to the broker executable
# additional arguments can be defined in the ARGS property
#
BROKER_OPTIONS="-silent"

#
# ETC HOME
IMQ_ETCHOME="/etc/imq"
#
# imqbrokerd.conf
CONF_FILE=$IMQ_ETCHOME/imqbrokerd.conf
#
#
###### END LOCAL CONFIGURATION

FMRI=svc:/application/sun/mq/mqbroker
EXECUTABLE=imqbrokerd


# error "description"
error () {
  echo $0: $* 2>&1
  exit $SMF_EXIT_ERROR_CONFIG
}

# find the named process(es)
findproc() {
  pid=`/usr/bin/ps -ef |
  /usr/bin/grep -w $1 | 
  /usr/bin/grep -v grep |
  /usr/bin/awk '{print $2}'`
  echo $pid
}

# kill the named process(es) (borrowed from S15nfs.server)
killproc() {
   pid=`findproc $1`
   [ "$pid" != "" ] && kill $pid
}

#
# require /usr/bin and Message Queue
#
if [ ! -d /usr/bin ]; then
   error "Cannot find /usr/bin."

elif [ ! -d "$IMQ_HOME" ]; then
   error "Cannot find Message Queue in IMQ_HOME ($IMQ_HOME)."
fi


#
# Start/stop Message Queue Broker
#
case "$1" in
'start')
         if [ -f $CONF_FILE ] ; then 
            ARGS=`grep -v "^#" $CONF_FILE | grep ARGS | cut -c6-`
         fi

	 # Overwrite args with one defined in service
	 # Change '\ ' to just ' '.
	 SVCARGS=`svcprop -p options/broker_args $FMRI`
	 if [ "$SVCARGS" != "\"\"" -a "$SVCARGS" != "" ]; then
             if [ $DEBUG ] ; then 
		echo "options/broker_args found - using $SVCARGS"
	     fi
	     ARGS=$SVCARGS
	 fi
	
         if [ $DEBUG ] ; then 
	     echo "Command \c"
	     echo "$IMQ_HOME/bin/$EXECUTABLE -bgnd $BROKER_OPTIONS  $ARGS... \c"
         fi
	 cd $IMQ_HOME

	 # Check if the server is already running.
	 if [ -n "`findproc $EXECUTABLE`" ]; then
             if [ $DEBUG ] ; then 
	        echo "$EXECUTABLE is already running."
             fi
	     exit $SMF_EXIT_OK
	 fi

	 # sh the command because it may contain '\' chars.
	 sh -c "bin/$EXECUTABLE -bgnd $BROKER_OPTIONS $ARGS" &
	
         if [ $DEBUG ] ; then 
  	     echo "done"
         fi

	;;

'stop')
        if [ $DEBUG ] ; then
	    echo "Stopping $EXECUTABLE ... \c"
        fi
	if [ -z "`findproc $EXECUTABLE`" ]; then
            if [ $DEBUG ] ; then
	        echo "$EXECUTABLE is not running."
            fi
	   exit $SMF_EXIT_OK
	fi
	killproc $EXECUTABLE
        if [ $DEBUG ] ; then
	    echo "done"
        fi
	;;

*)
	echo "Usage: $0 { start | stop }"
	;;
esac
