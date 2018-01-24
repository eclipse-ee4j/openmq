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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public final class MigratableStoreUtil {

    public static String makeEffectiveBrokerID(String instName, UID storeSession)
    throws BrokerException {
        if (instName == null) {
            throw new BrokerException("null instance name");
        }
        if(storeSession == null) {
            throw new BrokerException("null store session");
        }
        return instName+"U"+storeSession;
    }

    public static String parseEffectiveBrokerIDToInstName(String brokerid)
    throws BrokerException {
        if (brokerid == null) {
            throw new BrokerException("null effective brokerid");
        }
        int ind = brokerid.lastIndexOf("U");
        if (ind <= 0 || ind == (brokerid.length()-1)) {
            throw new BrokerException("Malformed effective brokerid "+brokerid);
        }
        return brokerid.substring(0, ind);
    }

    public static UID parseEffectiveBrokerIDToStoreSessionUID(String brokerid)
    throws BrokerException {
        if (brokerid == null) {
            throw new BrokerException("null effective brokerid");
        }
        int ind = brokerid.lastIndexOf("U");
        if (ind <= 0 || ind == (brokerid.length()-1)) {
            throw new BrokerException("Malformed effective brokerid "+brokerid);
        }
        return new UID(Long.parseLong(brokerid.substring(ind+1)));
    }

    public static String makeReplicationGroupID(String instName, UID storeSession)
    throws BrokerException {
        if (instName == null) {
            throw new BrokerException("null instance name");
        }
        if(storeSession == null) {
            throw new BrokerException("null store session");
        }
        return instName+"U"+storeSession+"C"+Globals.getClusterID();
    }

}
