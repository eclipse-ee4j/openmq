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
 * @(#)Green.java	1.10 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory.levels;

import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;


public class Green extends MemoryLevelHandler
{

    protected static final int DEFAULT_COUNT = 50000;
    protected int messageCount =0;

    public Green(String name) {
        super(name);
        MEMORY_NAME_KEY = BrokerResources.M_MEMORY_GREEN;
        messageCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".count", DEFAULT_COUNT);
    }

    public int getMessageCount(long freeMem, int producers) {
        return messageCount;
    }

    public long getMemory(long freeMemory, int producers) {
        return (freeMemory - MAX_MEMORY_DELTA)/2;
    }

    public int gcCount() {
        return NEVER_GC;
    }

    public int gcIteration() {
        return NEVER_GC;
    }
}

/*
 * EOF
 */
