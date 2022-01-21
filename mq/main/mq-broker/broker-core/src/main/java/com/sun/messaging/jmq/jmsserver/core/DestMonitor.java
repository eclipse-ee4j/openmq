/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.jmsserver.Globals;
import java.util.*;

class DestMonitor extends Monitor {
    DestinationUID target = null;

    DestMonitor(Destination d, DestinationUID target) {
        super(d);
        this.target = target;
    }

    @Override
    protected Hashtable getMonitorData() {

        if (target == null) {
            return (null);
        }

        Destination[] ds = Globals.getDestinationList().getDestination(d.getPartitionedStore(), target);
        Destination td = ds[0];

        if (td == null) {
            return (null);
        }

        Hashtable values = new Hashtable(td.getMetrics());
        return values;
    }

}

