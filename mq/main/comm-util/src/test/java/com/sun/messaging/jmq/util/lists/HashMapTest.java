/*
 * Copyright (c) 2020 Payara Services Ltd. and/or affiliates
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
 * package com.sun.messaging.jmq.util;
 */
package com.sun.messaging.jmq.util.lists;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for classes that extends {@link java.util.HashMap}
 * @author jonathan coustick
 * @see SimpleNFLHashMap
 * @see WeakValueHashMap
 */
public class HashMapTest {
    
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String THIRD = "third";
    
    
    private static final String ONE = "one";
    private static final String TWO = "two";
    private static final String THREE = "three";
    
    @Test
    public void simpleNFLHashMapTest() {
        SimpleNFLHashMap<String, String> testMap = new SimpleNFLHashMap<>();
        Assertions.assertTrue(testMap.isEmpty());
        testMap.put(ONE, FIRST);
        testMap.put(TWO, SECOND);
        
        
        Assertions.assertEquals(2, testMap.size());
        
        testMap.setCapacity(2);
        testMap.enforceLimits(true);
        try {
            testMap.put(THREE, THIRD);
            Assertions.fail("Limit should have been exceeded");
        } catch (OutOfLimitsException ooe) {
            //expected
        }
        Assertions.assertTrue(testMap.containsKey(ONE));
        Assertions.assertTrue(testMap.containsValue(FIRST));
        Assertions.assertEquals(FIRST, testMap.get(ONE));
        
    }
    
    @Test
    public void weakValueHashMapTest() {
        WeakValueHashMap<String, String> testMap = new WeakValueHashMap<>("test");
        Assertions.assertTrue(testMap.isEmpty());
        testMap.put(ONE, FIRST);
        testMap.put(TWO, SECOND);
        testMap.put(THREE, THIRD);
        
        Assertions.assertEquals(3, testMap.size());
        Assertions.assertTrue(testMap.containsKey(ONE));
        Assertions.assertEquals(FIRST, testMap.get(ONE));
        
    }
    
}
