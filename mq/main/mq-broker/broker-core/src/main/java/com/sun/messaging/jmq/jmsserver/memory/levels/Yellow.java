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
 * @(#)Yellow.java	1.12 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory.levels;

import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.util.log.*;

public class Yellow extends Green
{

    protected static final int DEFAULT_GC_ITR = 1000;
    protected  int gcIterationCount = 0;


    public Yellow(String name) {
        super(name);
        MEMORY_NAME_KEY = BrokerResources.M_MEMORY_YELLOW;
        gcIterationCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".gcitr", DEFAULT_GC_ITR);
    }

    public int getMessageCount(long freeMemory, int producers) {
       // never divide by 0
       if (producers >= 0) producers = 1;
        return super.getMessageCount(freeMemory,producers)/producers;
    }

    public long getMemory(long freeMemory, int producers) {
       // never divide by 0
       if (producers >= 0) producers = 1;
        return super.getMemory(freeMemory,producers)/producers/2;
    }

    public int gcCount() {
        return 1;
    }

    public int gcIteration() {
        return gcIterationCount;
    }

    public boolean cleanup(int cnt) {
        super.cleanup(cnt);
        logger.log(Logger.INFO,BrokerResources.I_LOW_MEMORY_FREE);
        logger.log(Logger.DEBUG,"Broker is swapping persistent/sent but un-acked  messages");
        Globals.getConnectionManager().cleanupMemory(true /* persist */);

        return true;
    }

    public boolean enter(boolean fromHigher) {
        super.enter(fromHigher);

        if (fromHigher) return false;

        MemoryGlobals.setMEM_FREE_P_ACKED(true);

        return true; // change cnt/etc
    }

    public boolean leave(boolean toHigher)  {
        super.leave(toHigher);

        if (toHigher) {
            // went to higher level, do nothing
            return false;
        }

        MemoryGlobals.setMEM_FREE_P_ACKED( false);

        return false; // dont bother to tell the client that the
                      // counts have changed -> it will fix itsself
    }

}



/*
 * EOF
 */
