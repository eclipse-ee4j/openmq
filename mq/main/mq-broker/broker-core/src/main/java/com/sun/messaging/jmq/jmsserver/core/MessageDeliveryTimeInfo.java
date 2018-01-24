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

import java.io.Serializable;
import java.util.*;
import com.sun.messaging.jmq.io.SysMessageID;

public class MessageDeliveryTimeInfo 
{
    static Comparator deliveryTimeCompare = new DeliveryTimeComparator();

    static class DeliveryTimeComparator implements Comparator, Serializable
    {
        public int compare(Object o1, Object o2) {

            if (!(o1 instanceof MessageDeliveryTimeInfo &&
                  o2 instanceof MessageDeliveryTimeInfo)) {
                throw new RuntimeException(
                "Internal Error: unexpected object type passed to "+
                "MessageDeliveryTimeInfo.compare("+o1+", "+o2+")");
            }
            MessageDeliveryTimeInfo di1=(MessageDeliveryTimeInfo)o1;
            MessageDeliveryTimeInfo di2=(MessageDeliveryTimeInfo)o2;
            long diff =  di1.deliveryTime - di2.deliveryTime;
            if (diff != 0L) {  
                return (diff > 0L ? 1:-1);
            }
            SysMessageID sys1 = di1.id;
            SysMessageID sys2 = di2.id;
            diff = sys2.getTimestamp() - sys1.getTimestamp();
            if (diff == 0L) {
                diff = sys2.getSequence() - sys1.getSequence();
            }
            if (diff == 0L) {
                return 0;
            }
            return (diff < 0L ? 1:-1);
        }

        public int hashCode() {
            return super.hashCode();
        }

        public boolean equals(Object o) {
            return super.equals(o);
        }
    }

    private SysMessageID id =  null;
    private long deliveryTime = 0L;
    private boolean deliveryDue = false;
    private boolean deliveryReady = false;
    private boolean inprocessing = false;
    private Boolean onTimerState = null; //null, true, false

    private MessageDeliveryTimeTimer readyListener = null;

    public String toString() {
        return "DeliveryTimeInfo["+id+", "+deliveryTime+"]"+deliveryDue;
    }

    public boolean isDeliveryDue() {
        long currtime = System.currentTimeMillis();
        synchronized(this) {
            if (!deliveryDue) {
                deliveryDue = (deliveryTime <= currtime);
            }
            return deliveryDue;
        }
    }

    public synchronized boolean setInProcessing(boolean b) {
        if (!b) {
            inprocessing = false;
            return true;
        }
        if (inprocessing) {
            return false;
        }
        inprocessing = true;
        return true;
    }

    public synchronized Boolean getOnTimerState() {
        return onTimerState;
    }

    public synchronized void setOnTimerState() {
        onTimerState = Boolean.TRUE;
    }

    public synchronized void setOffTimerState() {
        onTimerState = Boolean.FALSE;
    }

    protected synchronized void setDeliveryReadyListener(
                            MessageDeliveryTimeTimer l) {
        readyListener = l;
    }

    protected void cancelTimer() {
        MessageDeliveryTimeTimer listener = null;
        synchronized(this) {
            if (readyListener != null) {
                listener = readyListener;
            }
        }
        if (listener != null) {
            listener.removeMessage(this);
        }
    }

    public void setDeliveryReady() {
        MessageDeliveryTimeTimer listener = null;
        synchronized(this) {
            deliveryReady = true;
            if (readyListener != null) {
                listener = readyListener;
                readyListener = null;
            }
        }
        if (listener != null) {
            listener.deliveryReady(this);
        }
    }

    public synchronized boolean isDeliveryReady() {
        return deliveryReady;
    }

    public static Comparator getComparator() {
        return deliveryTimeCompare;
    }

    public MessageDeliveryTimeInfo(SysMessageID id, long deliveryTime) {
        this.id = id;
        this.deliveryTime = deliveryTime;
    }

    public long getDeliveryTime() {
        return deliveryTime;
    }

    public SysMessageID getSysMessageID() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }
    public boolean equals(Object o) {
        if (!(o instanceof MessageDeliveryTimeInfo)) {
            return false;
        }
        MessageDeliveryTimeInfo di = (MessageDeliveryTimeInfo)o;
        if (id == null || di.id == null) {
            throw new RuntimeException(
            "Internal Error: unexpected values on "+
            "MessageDeliveryTimeInfo.equals("+di+")"+this.id);
        }
        return id.equals(di.id);
    }
}

