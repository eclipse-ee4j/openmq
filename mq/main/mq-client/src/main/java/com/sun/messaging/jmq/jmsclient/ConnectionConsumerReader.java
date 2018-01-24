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
 * @(#)ConnectionConsumerReader.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import com.sun.messaging.jmq.io.*;
import java.io.*;

/**
 * A ConnectionConsumerReader reads off messages from the connection
 * consumer's Read Queue and delivers the messages to the connection
 * consumer which will load the messages to ServerSessions
 */

//XXX REVISIT
public class ConnectionConsumerReader extends ConsumerReader {
    private ConnectionConsumerImpl connectionConsumer = null;
    private int load = 0;
    private int maxMessages;

    public ConnectionConsumerReader(ConnectionConsumerImpl connectionConsumer) {
        super(connectionConsumer.getConnection(), 
              connectionConsumer.getReadQueue());

       this.connectionConsumer = connectionConsumer;
       maxMessages = connectionConsumer.getMaxMessages();
       if (maxMessages < 1) { //should not happen
           maxMessages = 1;
       }
       load = 0;
    }

    protected void deliver(ReadOnlyPacket packet) throws IOException, JMSException {
        MessageImpl message = protocolHandler.getJMSMessage(packet);
        if (maxMessages == 1) {
            connectionConsumer.onMessage(message);
            connectionConsumer.startServerSession();
        }
		else {
            if (load == 0) {
                //'sessionQueue' really should named as 'readQueue'
                load = sessionQueue.size() + 1;
                if (load > maxMessages) {
                    load = maxMessages;
                }
            }
            connectionConsumer.onMessage(message);
            load--;
            if (load == 0) {
                connectionConsumer.startServerSession();
            }
        }
	}

    protected void deliver() throws IOException, JMSException {
        connectionConsumer.onNullMessage();
    }

    public void dump (PrintStream ps) {
        ps.println ("------ ConnectionConsumerReader dump ------");
        ps.println ("maxMessages: " + maxMessages);
        ps.println ("current load: " + load);
        super.dump(ps);
    }

}

