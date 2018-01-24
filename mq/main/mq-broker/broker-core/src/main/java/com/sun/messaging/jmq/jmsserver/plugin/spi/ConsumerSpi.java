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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.plugin.spi;

import java.util.*;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.lists.EventType;
import com.sun.messaging.jmq.util.lists.EventListener;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;

/**
 */

public interface ConsumerSpi {

	public Object getAndFillNextPacket(Packet p);

    public void attachToSession(SessionUID uid);

    public Object removeEventListener(Object id);

    public Object addEventListener(
        EventListener listener, EventType type, Object userData)
        throws UnsupportedOperationException;

    public int getPrefetch(); 

    public ConsumerUID getConsumerUID();
    public ConsumerUID getStoredConsumerUID();
    public DestinationUID getDestinationUID();
    public ConnectionUID getConnectionUID();

    public void resumeFlow(int v); 

    public DestinationSpi getFirstDestination();

	public void pause(String reason); 
	public void resume(String reason); 

    public void setPrefetch(int count, boolean useConsumerFlowControl);

    public void attachToConnection(ConnectionUID uid);

    public void debug(String prefix); 

    public void dump(String prefix);

    public boolean isBusy();

}
