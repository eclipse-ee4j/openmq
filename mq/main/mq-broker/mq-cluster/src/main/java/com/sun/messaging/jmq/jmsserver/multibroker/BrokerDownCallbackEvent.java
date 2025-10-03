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

package com.sun.messaging.jmq.jmsserver.multibroker;

import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;

class BrokerDownCallbackEvent extends CallbackEvent {
    private BrokerAddress broker;

    BrokerDownCallbackEvent(BrokerAddress broker) {
        this.broker = broker;
    }

    @Override
    public void dispatch(MessageBusCallback cb) {
        cb.brokerDown(broker);
    }

    @Override
    public String toString() {
        return "BrokerDown: " + broker;
    }
}

