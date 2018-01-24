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
 * @(#)MemoryGlobals.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory;

import com.sun.messaging.jmq.jmsserver.Globals;

/** 
 * This class contains globals which are used for memory mgt
 * through out the broker.
 *
 * Individual MemoryLevelHandlers may change the state of these
 * values
 */


public class MemoryGlobals
{

// Variables controled by various memory management levels
    /**
     * automatically free all persistent messages
     * that have been sent but not acknowledged
     */
    private static boolean MEM_FREE_P_ACKED = false;

    public static void setMEM_FREE_P_ACKED(boolean ack) {
        MEM_FREE_P_ACKED=ack;
    }

    /**
     * automatically swap all non-persistent messages
     * that have been sent but not acknowledged
     * (not currently used)
     */
    private static boolean MEM_FREE_NP_ACKED = false;

    public static void setMEM_FREE_NP_ACKED(boolean ack) {
        MEM_FREE_NP_ACKED=ack;
    }

    public static final boolean getMEM_FREE_NP_ACKED() {
        return MEM_FREE_NP_ACKED;
    }

    public static final boolean getMEM_FREE_P_ACKED() {
        return MEM_FREE_P_ACKED;
    }
    

    /*
    //automatically free all persistent messages
    //that have no active consumers
    private static boolean MEM_FREE_P_NOCON = false;
    public static void setMEM_FREE_P_NOCON(boolean ack) {
        MEM_FREE_P_NOCON=ack;
    }

    //automatically swap all non-persistent messages
    //that have no active consumers (not currently used)
    private static boolean MEM_FREE_NP_NOCON = false;
    public static void setMEM_FREE_NP_NOCON(boolean ack) {
        MEM_FREE_NP_NOCON=ack;
    }

    //automatically free all persistent messages
    private static boolean MEM_FREE_P_ALL = false;
    public static void setMEM_FREE_P_ALL(boolean ack) {
        MEM_FREE_P_ALL=ack;
    }

    //automatically swap all non-persistent messages
    public static boolean MEM_FREE_NP_ALL = false;
    public static void setMEM_FREE_NP_ALL(boolean ack) {
        MEM_FREE_NP_ALL=ack;
    }
    */

    /**
     * no longer allow producers
     */
    private static boolean MEM_DISALLOW_PRODUCERS = false; 

    public static void setMEM_DISALLOW_PRODUCERS(boolean ack) {
        MEM_DISALLOW_PRODUCERS=ack;
    }

    public static final boolean getMEM_DISALLOW_PRODUCERS() {
        return MEM_DISALLOW_PRODUCERS;
    }

    /**
     * no longer allow new destinations to be created
     */
    private static boolean MEM_DISALLOW_CREATE_DEST = false;

    public static void setMEM_DISALLOW_CREATE_DEST(boolean ack) {
        MEM_DISALLOW_CREATE_DEST=ack;
    }

    public static final boolean getMEM_DISALLOW_CREATE_DEST() {
        return MEM_DISALLOW_CREATE_DEST;
    }


// Properties which control basic memory management behavior

    /**
     * determine whether non-persistent messages should be swapped w/
     * the current persistence implementation or the old swapping code
     */

    public static final boolean SWAP_USING_STORE = 
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.swapUsingStore", true);
    public static final boolean SWAP_NP_MSGS =
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.swapNPMsgs", true);
    public static final boolean KEEP_NP_MSGS_AT_START = 
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.keepNPMsgs", false);


    /**
     * always check memory after a packet is read into the
     * system before processing it
     */
    private static boolean MEM_EXPLICITLY_CHECK = 
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.explicitCheck", false);

    public static void setMEM_EXPLICITLY_CHECK(boolean ack) {
        MEM_EXPLICITLY_CHECK=ack;
    }

    public static final boolean getMEM_EXPLICITLY_CHECK() {
        return MEM_EXPLICITLY_CHECK;
    }

    /**
     * always check memory after a packet is read into the
     * system is larger than MEM_SIZE_TO_QUICK_CHECK
     */
    public static final boolean MEM_QUICK_CHECK= 
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.quickCheck", false);

    /**
     * Packet size for triggering MEM_QUICK_CHECK
     */
    public static final int MEM_SIZE_TO_QUICK_CHECK =
              Globals.getConfig().getIntProperty(Globals.IMQ +
                  ".memory_management.quickCheckSize", 1024*10);

 
    /**
     * automatically free persistent messages at startup
     * after processing (default)
     */
    public static final boolean MEM_FREE_AT_RESTART = 
              Globals.getConfig().getBooleanProperty(Globals.IMQ +
                  ".memory_management.freeAutomaticallyAtRestart", true);

}


