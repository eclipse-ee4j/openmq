/*
 * Copyright (c) 2020 Payara Services Ltd. and/or affiliates
 * Copyright 2021 Contributors to the Eclipse Foundation
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
package com.sun.messaging.jmq.util.lists;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author jonathan coustick
 */
class PriorityFifoSetTest extends FifoSetTestBase {
    @Test
    void priorityFifoSetTest() {
        PriorityFifoSet<String> testSet = new PriorityFifoSet<>(10);
        Assertions.assertTrue(testSet.isEmpty());
        testSet.add(2, FIRST);
        testSet.add(SECOND);
        testSet.add(0, THIRD);
        Assertions.assertEquals(3, testSet.size());
        Assertions.assertTrue(testSet.contains(SECOND));
        Assertions.assertEquals(THIRD, testSet.first());
        Assertions.assertEquals(THIRD, testSet.first()); //test that first() has not removed item
        Assertions.assertEquals(SECOND, testSet.last());

        testSet.remove(FIRST);
        Assertions.assertEquals(THIRD, testSet.first());
        Assertions.assertEquals(2, testSet.size());
        testSet.clear();
        Assertions.assertTrue(testSet.isEmpty());
    }
}
