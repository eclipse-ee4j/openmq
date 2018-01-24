/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 * @(#)ClusterManager.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.manager;

import java.util.Set;
import java.util.Iterator;
import com.sun.messaging.jmq.io.MQAddress;
import java.util.NoSuchElementException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.UID;

// for javadocs
import com.sun.messaging.jmq.jmsserver.Globals;

/**
 * Typesafe enum class which represents a Reason passed into broker changed
 */
public class ClusterReason
{
        
        /**
         * descriptive string associated with the reason
         */
        private final String name;
        
        /**
         * private constructor for ClusterReason
         */
        private ClusterReason(String name) {
            this.name = name;
        }

        /**
         * a string representation of the object
         */
        public String toString() {
            return "ClusterReason["+ name +"]";
        }

        /**
         * A broker has been added to the cluster.
         */
        public static final ClusterReason ADDED = 
                 new ClusterReason("ADDED");

        /**
         * A broker has been removed from the cluster.
         */
        public static final ClusterReason REMOVED = 
                 new ClusterReason("REMOVED");

        /**
         * The status of a broker has changed.
         * @see BrokerStatus
         */
        public static final ClusterReason STATUS_CHANGED = 
                 new ClusterReason("STATUS_CHANGED");

        /**
         * The state of a broker has changed.
         * @see BrokerState
         */
        public static final ClusterReason STATE_CHANGED = 
                 new ClusterReason("STATE_CHANGED");

        /**
         * The protocol version of a broker has changed.
         */
        public static final ClusterReason VERSION_CHANGED = 
                 new ClusterReason("VERSION_CHANGED");

        /**
         * The portmapper address of a broker has changed.
         */
        public static final ClusterReason ADDRESS_CHANGED = 
                 new ClusterReason("ADDRESS_CHANGED");

        /**
         * The address of the master broker in the cluster
         * has changed.
         */
        public static final ClusterReason MASTER_BROKER_CHANGED = 
                 new ClusterReason("MASTER_BROKER_CHANGED");
    
}

