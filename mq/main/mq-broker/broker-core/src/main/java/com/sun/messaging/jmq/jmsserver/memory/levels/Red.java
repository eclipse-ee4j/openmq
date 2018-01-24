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
 * @(#)Red.java	1.11 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory.levels;

import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.util.log.*;


public class Red extends MemoryLevelHandler
{
    protected static final boolean SWAP_NON_PERSIST = false;

    protected static final int GC_DEFAULT=10;
    protected static final int GC_ITR_DEFAULT=10;
    protected int GCCount =0;
    protected int GCItrCount =0;

    public Red(String name) {
        super(name);
        MEMORY_NAME_KEY = BrokerResources.M_MEMORY_RED;
        GCCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".gccount", GC_DEFAULT);
        GCItrCount = Globals.getConfig().getIntProperty(
                         Globals.IMQ + "." + name + ".gcitr", GC_ITR_DEFAULT);
    }

    public int getMessageCount(long freeMem, int producers) {
        return PAUSED;
    }

    public long getMemory(long freeMemory, int producers) {
        return PAUSED;
    }

    public int gcCount() {
        return GCCount;
    }

    public int gcIteration() {
        return GCItrCount;
    }

    public boolean cleanup(int cnt) {
        super.cleanup(cnt);

        switch (cnt) {

            case 0: // clean up persistent messages
                logger.log(Logger.INFO,BrokerResources.I_LOW_MEMORY_FREE);
                logger.log(Logger.DEBUG,"Broker is swapping all persistent messages");
  
                //LKS - XXX 
                //PacketReference.inLowMemoryState(true);
                break;
            default:
                assert false ;
        }

        // if we are on the first iteration and SWAP_NON_PERSIST
        // is true -> return false so we go around for another
        // iteration IF we stay in RED

        return !SWAP_NON_PERSIST || cnt == 1;
    }

    public boolean enter(boolean fromHigher) {
        super.enter(fromHigher);

        if (fromHigher) return true;

        //MemoryGlobals.setMEM_FREE_P_ALL(true);
        MemoryGlobals.setMEM_DISALLOW_PRODUCERS(true);
        MemoryGlobals.setMEM_DISALLOW_CREATE_DEST(true);

        try {
            Globals.getClusterBroadcast().pauseMessageFlow();
        } catch (Exception ex) {
             logger.logStack(Logger.DEBUG,"Got exception in Red", ex);
        }

        return true; // change cnt/etc
    }

    public boolean leave(boolean toHigher)  {
        super.leave(toHigher);

        if (toHigher) {
            // we went up a level, dont do anything
            return true;
        }
        //MemoryGlobals.setMEM_FREE_NP_ALL(true);
        //MemoryGlobals.setMEM_FREE_P_ALL(false);
        MemoryGlobals.setMEM_DISALLOW_PRODUCERS(false);
        MemoryGlobals.setMEM_DISALLOW_CREATE_DEST(false);

        try {
            Globals.getClusterBroadcast().resumeMessageFlow();
        } catch (Exception ex) {
             logger.logStack(Logger.DEBUG,"Got exception in Red", ex);
        }

        return true; // we have to notify, the client wont fix
                     // itsself anymore
    }


}


/*
 * EOF
 */
