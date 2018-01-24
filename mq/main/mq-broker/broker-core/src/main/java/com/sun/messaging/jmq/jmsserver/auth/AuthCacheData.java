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
 * @(#)AuthCacheData.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import javax.security.auth.Refreshable;
import javax.security.auth.RefreshFailedException;

public class AuthCacheData 
{
    Refreshable cacheData = null;
    //private boolean staled = true;

    public AuthCacheData() { }
    
    public synchronized void setCacheData(Refreshable data) {
        if (data == null) return;

        synchronized(data) {
            if (data.isCurrent()) {
                cacheData = data;
            }
        }
    }

    public synchronized Refreshable getCacheData() {
        return cacheData;
    }
   
    public synchronized void refresh() throws RefreshFailedException { 
        if (cacheData == null) return;

        synchronized(cacheData) {
            cacheData.refresh();
        }
    }
}
