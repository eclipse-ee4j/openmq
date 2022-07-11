/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

    Object getAndFillNextPacket(Packet p);

    void attachToSession(SessionUID uid);

    Object removeEventListener(Object id);

    /** @throws UnsupportedOperationException */
    Object addEventListener(EventListener listener, EventType type, Object userData);

    int getPrefetch();

    ConsumerUID getConsumerUID();

    ConsumerUID getStoredConsumerUID();

    DestinationUID getDestinationUID();

    ConnectionUID getConnectionUID();

    void resumeFlow(int v);

    DestinationSpi getFirstDestination();

    void pause(String reason);

    void resume(String reason);

    void setPrefetch(int count, boolean useConsumerFlowControl);

    void attachToConnection(ConnectionUID uid);

    void debug(String prefix);

    void dump(String prefix);

    boolean isBusy();

}
