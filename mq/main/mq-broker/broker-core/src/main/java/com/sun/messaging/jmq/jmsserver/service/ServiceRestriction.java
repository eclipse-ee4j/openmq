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
 * @(#)ServiceRestriction.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

public class ServiceRestriction 
{
    private final String name;

    private ServiceRestriction(String name) {
        this.name = name;
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean verbose) {
        if (!verbose) {
            return "["+name+ "]";
        }

        if (this == NO_SYNC_WITH_MASTERBROKER) {
            Object m = Globals.getClusterManager().getMasterBroker();
            return Globals.getBrokerResources().getString(
                BrokerResources.I_NO_SYNC_WITH_MASTERBROKER,
                (m == null ? "":m.toString()));
        } else {
            return "["+name+ "]";
        }
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof ServiceRestriction)) return false;
        ServiceRestriction that = (ServiceRestriction)o;
        return this.name.equals(that.name);
    }
   
    public int hashCode() {
        return name.hashCode();
    }

    public static final ServiceRestriction NO_SYNC_WITH_MASTERBROKER = 
                     new ServiceRestriction("NO_SYNC_WITH_MASTERBROKER");

}
