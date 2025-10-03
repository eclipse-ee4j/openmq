/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.RemoteTransaction;

public abstract class RemoteTransactionEvent extends TransactionEvent {
    public static final byte Type2PPrepareEvent = 1;
    public static final byte Type2PCompleteEvent = 2;

    RemoteTransaction remoteTransaction;

    static TransactionEvent create(byte subtype) {
        TransactionEvent result = null;
        switch (subtype) {
        case Type2PPrepareEvent:
            result = new RemoteTransaction2PPrepareEvent();
            break;
        case Type2PCompleteEvent:
            result = new RemoteTransaction2PCompleteEvent();
            break;
        default:
            throw new UnsupportedOperationException();
        }
        return result;
    }

    @Override
    int getType() {
        return BaseTransaction.REMOTE_TRANSACTION_TYPE;
    }

    abstract int getSubType();
}

