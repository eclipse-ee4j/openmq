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

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.IOException;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterDestInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

public class DestinationUpdateChangeRecord extends ChangeRecord {
    private String name;
    private int type;

    public DestinationUpdateChangeRecord(GPacket gp) {
        operation = gp.getType();

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(gp);
        name = cdi.getDestName();
        type = cdi.getDestType();
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getUniqueKey() {
        return "dst:" + name + ":" + type;
    }

    public boolean isAddOp() {
        return (operation == ProtocolGlobals.G_UPDATE_DESTINATION);
    }
}

