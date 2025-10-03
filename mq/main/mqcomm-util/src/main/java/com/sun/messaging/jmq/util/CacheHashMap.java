/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2020, 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A LinkedHashMap that is bounded by size. Once the HashMap is full, the oldest entry is discarded as new entries are
 * added.
 */
public class CacheHashMap<K, V> extends LinkedHashMap<K, V> {

    @Serial
    private static final long serialVersionUID = -8739640834510094648L;
    private static int DEFAULT_CAPACITY = 16;
    private int capacity = DEFAULT_CAPACITY;

    /**
     * Create a CacheHashMap with a the specified capacity
     *
     * @param capacity Capacity of the CacheHashMap.
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
        // new line
        return capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > capacity;
    }
}
