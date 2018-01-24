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

package com.sun.messaging.bridge.service.stomp;

import javax.jms.Queue;
import javax.jms.Topic;
import javax.jms.Destination;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import com.sun.messaging.bridge.api.StompDestination;

/**
 * @author amyk 
 */
public class StompDestinationImpl implements StompDestination  {

    Destination dest = null;

    public StompDestinationImpl(Destination dest) {
        this.dest = dest;
    }

    @Override
    public boolean isQueue() {
        return (dest instanceof Queue);
    }

    @Override
    public boolean isTemporary() {
        return ((dest instanceof TemporaryQueue) ||
                (dest instanceof TemporaryTopic)); 
    }

    @Override
    public String getName() throws Exception {
        if (isQueue()) {
            return ((Queue)dest).getQueueName();
        }
        return ((Topic)dest).getTopicName();
    }

    protected Destination getJMSDestination() {
        return dest;
    }
}
