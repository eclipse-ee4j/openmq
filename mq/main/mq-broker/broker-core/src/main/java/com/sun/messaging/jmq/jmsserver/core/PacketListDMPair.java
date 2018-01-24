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
 */ 

package com.sun.messaging.jmq.jmsserver.core;

class PacketListDMPair 
{
    protected DestinationUID duid = null;
    private PacketReference ref = null;
    private boolean ret = true;
    private boolean islocal = true;

    public PacketListDMPair(DestinationUID duid, PacketReference ref) {
        this.duid = duid;
        if (ref != null && !ref.isLocal()) {
            islocal = false;
            this.ref = ref;
        }
    }
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof PacketListDMPair)) {
            return false;
        }
        PacketListDMPair other = (PacketListDMPair)o;
        return this.duid.equals(other.duid);
    }

    public int hashCode() {
        return duid.hashCode();
    }

    public synchronized void nullRef() {
        ref =  null;
    }

    /**
     */ 
    public synchronized boolean canRemove(PacketReference pr, DestinationList dl) {
        if (islocal || pr == null || pr.isLocal()) {
            return true;
        }
        if (ref != null) {
            return (ref == pr);
        }
        return (dl.get(pr.getSysMessageID(), true) == null);
    }

    public void setReturn(boolean ret) {
        this.ret = ret;
    }

    public boolean getReturn() {
        return ret;
    }

}
