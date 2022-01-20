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

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.util.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;

class WarningTask extends TimerTask {
    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();
    private ClusterImpl parent = null;

    WarningTask(ClusterImpl parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        if (parent.isConfigServerResolved()) {
            cancel();
        } else {
            if (Globals.nowaitForMasterBroker()) {
                logger.log(Logger.WARNING, br.W_MBUS_STILL_TRYING_NOWAIT, parent.getConfiguredConfigServer());
            } else {
                logger.log(Logger.WARNING, br.W_MBUS_STILL_TRYING, parent.getConfiguredConfigServer());
            }
        }
    }
}

