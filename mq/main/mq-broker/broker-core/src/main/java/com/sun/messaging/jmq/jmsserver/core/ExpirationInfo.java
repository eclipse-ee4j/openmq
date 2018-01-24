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
 * @(#)ExpirationInfo.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.io.Serializable;
import java.util.*;
import com.sun.messaging.jmq.io.SysMessageID;

public class ExpirationInfo 
{
    static Comparator expireCompare = new ExpirationComparator();


    static class ExpirationComparator implements Comparator, Serializable
    {
        public int compare(Object o1, Object o2) 
        {
            if (o1 instanceof ExpirationInfo &&
                o2 instanceof ExpirationInfo) 
            {
                 ExpirationInfo ei1=(ExpirationInfo)o1;
                 ExpirationInfo ei2=(ExpirationInfo)o2;
                 long dif = ei2.expireTime - ei1.expireTime;
                 if (dif == 0) {
                     SysMessageID sys1 = ei1.id;
                     SysMessageID sys2 = ei2.id;
                     dif = sys2.getTimestamp() - sys1.getTimestamp();
                     if (dif == 0)
                        dif = sys2.getSequence() - sys1.getSequence();
                }

                if (dif < 0) return 1;
                if (dif > 0) return -1;
                return 0;
             }
            assert false;
            return o1.hashCode() - o2.hashCode();
        }

        public int hashCode() {
            return super.hashCode();
        }

        public boolean equals(Object o1) 
        {
            return super.equals(o1);
        }
    }

    SysMessageID id;
    long expireTime;
    boolean expired = false;
    int reapCount = 0;

    public String toString() {
        return "ExpirationInfo[" + id + "," + expireTime + "]";
    }

    public synchronized boolean isExpired() {
        if (!expired) {
            expired = (expireTime <= System.currentTimeMillis());
        }
        return expired;
    }

    public static Comparator getComparator()
    {
        return expireCompare;
    }

    public ExpirationInfo(SysMessageID id, long expireTime)
    {
        this.id = id;
        this.expireTime = expireTime;
    }
    public long getExpireTime() {
        return expireTime;
    }

    public int getReapCount() {
        return reapCount;
    }

    public void incrementReapCount() {
        reapCount++;
    }

    public void clearReapCount() {
        reapCount = 0;
    }

    public SysMessageID getSysMessageID() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }
    public boolean equals(Object o) {
        if (!(o instanceof ExpirationInfo)) {
            return false;
        }
        ExpirationInfo ei = (ExpirationInfo)o;
        assert id != null && ei.id != null;
        return id.equals(ei.id);
    }
}

