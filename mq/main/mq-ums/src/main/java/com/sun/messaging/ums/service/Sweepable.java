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

package com.sun.messaging.ums.service;

/**
 *
 * A cache object implements this interface and register itself to the Cache
 * sweeper.  
 * 
 * The CacheSweeper wakes up periodically and calls the sweep method of each
 * registered cache -- ClientPool and ConnectionPool.
 * 
 * @author chiaming
 */
public interface Sweepable {
    
    /**
     * Sweep the cache.  Cached object aged (not accessed time) longer than 
     * the specified duration will be sweeped.
     * 
     * @param duration
     */
    public void sweep(long duration);
}
