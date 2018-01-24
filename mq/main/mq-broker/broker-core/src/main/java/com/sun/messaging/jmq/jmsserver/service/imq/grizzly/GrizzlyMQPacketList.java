/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import com.sun.messaging.jmq.io.Packet;
import java.util.ArrayList;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;

/**
 *
 * @author oleksiys
 */
public final class GrizzlyMQPacketList {
    private static final ThreadCache.CachedTypeIndex<GrizzlyMQPacketList> CACHED_IDX =
            ThreadCache.<GrizzlyMQPacketList>obtainIndex(GrizzlyMQPacketList.class, 1);
    
    private final List<Packet> packets =
            new ArrayList<Packet>();
    
    private Buffer packetsBuffer;

    private GrizzlyMQPacketList() {
    }
    
    public static GrizzlyMQPacketList create() {
        GrizzlyMQPacketList list = ThreadCache.takeFromCache(CACHED_IDX);
        if (list == null) {
            list = new GrizzlyMQPacketList();
        }
        
        return list;
    }
    
    public List<Packet> getPackets() {
        return packets;
    }

    public void setPacketsBuffer(final Buffer packetsBuffer) {
        this.packetsBuffer = packetsBuffer;
    }

    public Buffer getPacketsBuffer() {
        return packetsBuffer;
    }
    
    public void recycle(final boolean isDisposeBuffer) {
        if (isDisposeBuffer && packetsBuffer != null) {
            packetsBuffer.dispose();
        }
        packetsBuffer = null;
        
        packets.clear();
        
        ThreadCache.putToCache(CACHED_IDX, this);
    }
}
