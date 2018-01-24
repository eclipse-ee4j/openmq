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
 * @(#)TransactionInfoHandler.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class TransactionInfoHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public TransactionInfoHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(MessageBusCallback cb, BrokerAddress sender, GPacket pkt) {
        if (pkt.getType() == ProtocolGlobals.G_TRANSACTION_INFO) {
            if (DEBUG) {
                logger.log(logger.DEBUG,
                    "TransactionInfoHandler. G_TRANSACTION_INFO from : ", sender);
            }
            p.receivedTransactionInfo(pkt, sender, cb);
        }
        else {
            logger.log(logger.WARNING, "TransactionInfoHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }
}


/*
 * EOF
 */
