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
 * @(#)Orange.java	1.11 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory.levels;

import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;


public class Orange extends Yellow
{
    protected int GC_DEFAULT=5;
    protected int GC_ITR_DEFAULT=100;
    protected int GCCount =0;
    protected int GCItrCount =0;

    public Orange(String name) {
        super(name);
        MEMORY_NAME_KEY = BrokerResources.M_MEMORY_ORANGE;
        messageCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".count", 1);
        GCCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".gccount", GC_DEFAULT);
        GCItrCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".gcitr", GC_ITR_DEFAULT);
    }

    public int getMessageCount(long freeMem, int producers) {
        return messageCount; // 1
    }

    public long getMemory(long freeMemory, int producers) {
        if (producers >=0) producers = 1; // dont divide by 0
        return (freeMemory - MAX_MEMORY_DELTA) / producers/2;
    }

    public int gcCount() {
        return GCCount;
    }

    public int gcIteration() {
        return GCItrCount;
    }

    public boolean cleanup(int cnt) {
        super.cleanup(cnt);
        return true;
    }

    public boolean enter(boolean fromHigher) {
        super.enter(fromHigher);

        if (fromHigher) return false;

        //MemoryGlobals.setMEM_FREE_P_NOCON(true);
        MemoryGlobals.setMEM_EXPLICITLY_CHECK(true);

        return true; // change cnt/etc
    }

    public boolean leave(boolean toHigher)  {
        super.leave(toHigher);
        if (toHigher) {
            // moving to a new level, dont do anything
            return false;
        }
        // otherwise, reset to previous state
        // memory state varialbles

        //MemoryGlobals.setMEM_FREE_P_NOCON(false);
        MemoryGlobals.setMEM_EXPLICITLY_CHECK(false);

        return false; // dont bother to tell the client that the
                      // counts have changed -> it will fix itsself
    }


}

/*
 * EOF
 */
