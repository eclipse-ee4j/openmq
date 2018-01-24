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
 * @(#)OldestComparator.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.io.Serializable;
import java.util.Comparator;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;

class OldestComparator implements Comparator, Serializable
{
    public int compare(Object o1, Object o2) {
        if (o1 instanceof PacketReference && o2 instanceof PacketReference) {
                PacketReference ref1 = (PacketReference) o1;
                PacketReference ref2 = (PacketReference) o2;
                if (ref1.equals(ref2))
                    return 0;

                long dif = ref1.getTimestamp() - ref2.getTimestamp();

                // then sequence
                if (dif == 0)
                    dif = ref1.getSequence() - ref2.getSequence();

                if (dif == 0)
                    dif = ref1.getCreateTime() - ref2.getCreateTime();

                if (dif < 0) return -1;
                if (dif > 0) return 1;
                return o1.hashCode() - o2.hashCode();
        } else {
            assert false;
//LKS            return o1.hashCode() - o2.hashCode();
throw new RuntimeException("BOGUS");
        }
    }
    public boolean equals(Object o) {
        return super.equals(o);
    }

    public int hashCode() {
        return super.hashCode();
    }
    
}

