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
 * @(#)CacheHashMap.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A LinkedHashMap that is bounded by size. Once the HashMap is 
 * full, the oldest entry is discarded as new entries are added.
 */
public class CacheHashMap extends LinkedHashMap {

    private static int DEFAULT_CAPACITY = 16;
    private int capacity = DEFAULT_CAPACITY;

    /**
     * Create a CacheHashMap with a the specified capacity 
     *
     * @param   capacity    Capacity of the CacheHashMap.
     */
    public CacheHashMap(int capacity) {
        super(capacity);
        this.capacity = capacity;
    }

    /**
     * Create a CacheHashMap with a default capacity (16)
     */
    public CacheHashMap() {
        this(DEFAULT_CAPACITY);
    }

    public int capacity() {
	// BugId 6360052
	// Tom Ross
	// 10 Oct 2006

	// old line
	// return capacity();
	//new line
        return capacity;
    }

    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > capacity;
    }

    public static void main(String args[]) {

        CacheHashMap c = new CacheHashMap(5);

        for (int i = 0; i < 10; i++) {
            c.put(Long.valueOf(i), "a" + i);
        }

        System.out.println(c);
    }
}
