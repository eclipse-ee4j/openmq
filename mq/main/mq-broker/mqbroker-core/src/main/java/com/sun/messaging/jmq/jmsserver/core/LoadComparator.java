/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.core;

import java.util.*;
import java.io.*;

class LoadComparator implements Comparator, Serializable {
    @Serial
    private static final long serialVersionUID = -6719078017856553946L;

    @Override
    public int compare(Object o1, Object o2) {
        if (o1 instanceof PacketReference && o2 instanceof PacketReference) {
            PacketReference ref1 = (PacketReference) o1;
            PacketReference ref2 = (PacketReference) o2;
            // compare priority
            long dif = ref2.getPriority() - ref1.getPriority();

            if (dif == 0) {
                dif = ref1.getTimestamp() - ref2.getTimestamp();
            }

            // then sequence
            if (dif == 0) {
                dif = ref1.getSequence() - ref2.getSequence();
            }
            if (dif < 0) {
                return -1;
            }
            if (dif > 0) {
                return 1;
            }
            return 0;
        } else {
            assert false;
            return o1.hashCode() - o2.hashCode();
        }
    }
}
