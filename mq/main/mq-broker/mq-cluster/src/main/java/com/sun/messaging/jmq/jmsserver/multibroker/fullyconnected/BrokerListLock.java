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

class BrokerListLock {

    private Thread owner = null;
    private boolean locked = false;

    public synchronized void lock() {
        Thread me = Thread.currentThread();

        while (locked && owner != me) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        assert (locked == false && owner == null) || (locked == true && owner == me);

        locked = true;
        owner = me;
    }

    public synchronized void unlock() {
        Thread me = Thread.currentThread();

        assert locked == true;
        assert owner == me;

        locked = false;
        owner = null;
        notifyAll();
    }
}

