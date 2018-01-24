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
 * @(#)MemoryLevelHandler.java	1.12 06/29/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.memory;


import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.util.log.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Parent of all level (green,red, etc) memory managers.<P>
 *
 * Assumes that it is never called from two different threads
 * at the same time !!!
 */

public abstract class MemoryLevelHandler
{
    protected Logger logger = Globals.getLogger();

    private static boolean DEBUG = false;

    protected String MEMORY_NAME_KEY = null; // localization

    protected String localLevelName = null;

    protected int threshold = 0;

    protected int timeBetweenChecks = 0;


    /**
     * reflects if the broker is still in 
     * this level
     */
    protected boolean inLevel = false;

    /**
     * The time this level was entered (entered
     * was called 
     */
    protected long enteredLevelTime = 0;

    /**
     * Cumulitive time in this level 
     */
    protected long totalTimeInLevel = 0;

    /**
     * total time cleanup has been called
     */
    protected long totalCleanupCount = 0;

    /**
     * total # of times we have been in this
     * level (since broker started)
     */
    protected long totalTimesEnteredLevel = 0;


    protected String levelName = "none";

    protected long MAX_MEMORY_DELTA = 1024*10; 

    protected long LEVEL_DELTA = 1024; 

    protected final static int NEVER_GC = 0;
    protected final static int PAUSED = 0;

    public MemoryLevelHandler(String levelName) {
        this.levelName = levelName;
        threshold = Globals.getConfig().getIntProperty(
                Globals.IMQ + "." + levelName + ".threshold", 0);
        timeBetweenChecks = Globals.getConfig().getIntProperty(
                Globals.IMQ + "." + levelName + ".seconds", 5)
                 * 1000;

    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("levelName", levelName);
        ht.put("threshold", Integer.valueOf(threshold));
        ht.put("timeBetweenChecks", Integer.valueOf(timeBetweenChecks));
        ht.put("threshold", Integer.valueOf(threshold));
        ht.put("enteredLevelTime", Long.valueOf(enteredLevelTime));
        ht.put("totalTimeInLevel", Long.valueOf(totalTimeInLevel));
        ht.put("totalCleanupCount", Long.valueOf(totalCleanupCount));
        ht.put("totalTimesEnteredLevel", Long.valueOf(totalTimesEnteredLevel));
        ht.put("MAX_MEMORY_DELTA", Long.valueOf(MAX_MEMORY_DELTA));
        ht.put("LEVEL_DELTA", Long.valueOf(LEVEL_DELTA));
        ht.put("NEVER_GC", Integer.valueOf(NEVER_GC));
        ht.put("PAUSED", Integer.valueOf(PAUSED));
        ht.put("gcCount", Integer.valueOf( gcCount()));
        ht.put("gcIteration", Integer.valueOf( gcIteration()));
        ht.put("inLevel", Boolean.valueOf( inLevel));
        return ht;
    }


    public int getThresholdPercent() {
        return threshold;
    }

    public int getTimeBetweenChecks() {
        return timeBetweenChecks;
    }


    /**
     * Returns the current message count (JMQSize) per connection,
     * at this time, for this level. The routine may (or may not) 
     * use the passed in parameters.
     * 
     * @param freeMem the current free memory available in the system
     * @param producers the current number of producers available in the system
     */ 
    public abstract int getMessageCount(long freeMem, int producers);

    /**
     * Returns the current message bytes (JMQBytes) per connection, 
     * at this time, for this level. The routine may (or may not) 
     * use the passed in parameters.
     * 
     * @param freeMem the current free memory available in the system
     * @param producers the current number of producers available in the system
     */ 
    public abstract long getMemory(long freeMem, int producers);


    /**
     * method called when the broker initially enters the memory level, allows
     * the broker to clean up memory.  This routine will be called until either:
     * <UL><LI>prepare returns true (indicating its done all it can) </LI>
     *     <LI>enough memory is freed to return to the previous level</LI></UL>
     *
     * @returns true if all freeing has completed, false there may be 
     *                more work to do
     */
    public boolean cleanup(int iteration) {

        if (DEBUG) {
            logger.log(Logger.DEBUG, "MM: cleanup() " + toDebugString());
        }
        totalCleanupCount ++;
        return true; // done all it can
    }

    /**
     * Client has officially entered this level (prepare has completed and the
     * system is still in the same state). (e.g. entered Yellow from Green)
     *
     * @returns if true, tells client to send out state change notification
     */
    public boolean enter(boolean fromHigherLevel) {
        if (DEBUG) {
            logger.log(Logger.DEBUG, "MM: enter(" + fromHigherLevel 
                           + ") " + toDebugString());
        }

        enteredLevelTime = System.currentTimeMillis();
        totalTimesEnteredLevel ++;
        inLevel = true;
        return true;
    }

    /**
     * Client has left the state and moved to a different state (e.g. entered
     * Green from Yellow)
     * @param higherLevel true if we have moved to a higher level, false
     *        otherwise
     *
     * @returns if true, tells client to send out state change notification
     */
    public boolean leave(boolean toHigherLevel) {
        if (DEBUG) {
            logger.log(Logger.DEBUG, "MM: leave(" + toHigherLevel + ") " 
                      + toDebugString());
        }
        inLevel = false;
        totalTimeInLevel += System.currentTimeMillis() - enteredLevelTime;
        enteredLevelTime = 0;
        return false;
    }

    /**
     * number of gc's to call when the broker enters the level, to make sure 
     * that the memory footprint is accurate
     *
     * @returns number of gc's to call when state changes
     */
    public abstract int gcCount();

    /**
     * how often to call gc (based on the memory mgr thread iterations)
     * in this level (0 indicated never -> which means gc is called at most
     * during the calculations to determine if we have entered the level).
     *
     * @returns how many iterations between gc (0 indicates never gc)
     */
    public abstract int gcIteration();


    /**
     * string representing  the object
     */
    public String toString()
    {
        return "MemoryLevelHandler["+levelName() + "]";
    }


    /**
     * name of the level (for debug/diag purposes)
     */
    public String levelName() {
        return levelName;
    }

    public String localizedLevelName() {
        if (localLevelName == null) {
            if (MEMORY_NAME_KEY != null) {
                localLevelName = Globals.getBrokerResources().getString(
                    MEMORY_NAME_KEY, levelName);
            } else {
                localLevelName = levelName;
            }
        }
        return localLevelName;
    }

// diag get/set methods

    public long getTotalTimeInLevel() {
        return totalTimeInLevel + getCurrentTimeInLevel();
    }
    public boolean getIsInLevel() {
        return inLevel;
    }

    public long getTotalCleanupCount() {
        return totalCleanupCount;
    }

    public long getTotalTimesEnteredLevel() {
        return totalTimesEnteredLevel;
    }

    public long getCurrentTimeInLevel() {
        if (enteredLevelTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - enteredLevelTime;    
    }


    public String toDebugString() {
        return toString() +"\n\t"+ " inLevel=" +inLevel+"\n\t"
              + ", ThresholdPercent " + getThresholdPercent() +"\n\t"
              + ", totalTimeInLevel " + getTotalTimeInLevel() +"\n\t"
              + ", TotalCleanupCount " + getTotalCleanupCount() +"\n\t"
              + ", totalTimesEnteredLevel " 
                       + getTotalTimesEnteredLevel() +"\n\t"
              + ", CurrentTimeInLevel " + getCurrentTimeInLevel()+"\n\t" 
              + ", TotalCleanupCount " + getTotalCleanupCount() +"\n\t"
              + ", gcCount " + gcCount() +"\n\t"
              + ", timeBetweenChecks " + (timeBetweenChecks/1000) +" sec\n\t"
              + ", gcIteration " + gcIteration();
    }
}
