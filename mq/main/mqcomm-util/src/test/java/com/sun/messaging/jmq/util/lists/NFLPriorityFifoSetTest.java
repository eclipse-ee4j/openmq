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
class NFLPriorityFifoSetTest extends FifoSetTestBase {
    @Test
    void testNFLPriorityFifoSet() {
        NFLPriorityFifoSet<String> testSet = new NFLPriorityFifoSet<>();
        Assertions.assertTrue(testSet.isEmpty());
        testSet.add(FIRST);
        testSet.add(SECOND);
        testSet.add(THIRD);
        Assertions.assertEquals(3, testSet.size());
        Assertions.assertThrows(AssertionError.class, () -> testSet.contains(SECOND));
        synchronized (testSet) {
            Assertions.assertTrue(testSet.contains(SECOND));
            Assertions.assertEquals(FIRST, testSet.first());
            //
            Assertions.assertEquals(FIRST, testSet.first()); //test that first() has not removed item
            Assertions.assertEquals(THIRD, testSet.last());
        }

        testSet.remove(FIRST);
        synchronized (testSet) {
            Assertions.assertEquals(SECOND, testSet.first());
        }
        Assertions.assertEquals(2, testSet.size());
        testSet.clear();
        Assertions.assertTrue(testSet.isEmpty());
    }
}
