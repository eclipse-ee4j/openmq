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
 * @(#)MemoryManager.java	1.29 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory;

import com.sun.messaging.jmq.util.DiagManager;
import com.sun.messaging.jmq.util.DiagManager.Data;
import com.sun.messaging.jmq.util.DiagDictionaryEntry;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;
import java.util.*;
import java.lang.reflect.Constructor;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;


/**
 * class which handles the broker memory management
 */

public class MemoryManager implements DiagManager.Data
{
    private static boolean NO_GC_DEFAULT = false;

    private static final String GC_JAVA_VERSION="1.4.2";

    private static final String PACKAGE =
         "com.sun.messaging.jmq.jmsserver.memory.levels.";



    protected static final Logger logger = Globals.getLogger();

    static {
        NO_GC_DEFAULT=getNoGCDefault();
    }

    private static boolean getNoGCDefault() {
        boolean NoGCDefault = false;
        Version v = Globals.getVersion();
        int cmp = v.compareVersions((String)System.getProperties()
                  .get("java.version"), GC_JAVA_VERSION, true) ;
        if (cmp < 0) {
            // version is before 1.4.2
            NoGCDefault = false;
        } else {  // 1.4.2 or later
            NoGCDefault = true;
        }

        logger.log(Logger.DEBUGHIGH,"NoGC commuted from JDK:  " + NoGCDefault);
        return NoGCDefault;
    }

    public boolean NO_GC=Globals.getConfig().getBooleanProperty(
              "imq.memory_management.nogc", NO_GC_DEFAULT);

    private static boolean DEBUG = false;

    ArrayList diagDictionary = null;

    /**
     * set of memory handlers
     */
    MemoryLevelHandler[] levelHandlers = null;

    /**
     * level to enter a specific memory handler
     */
    long[] byteLevels = null;

    /**
     * memory state the broker is currently in
     */
    protected int currentLevel = 0;


    /**
     * memory state string [Displayed in diag]
     */
    protected String currentLevelString = "";

    /**
     * minimum amount of memory needed for the broker
     * runtime [Displayed in diag]
     */
    protected long baseMemory;


    /**
     * maximum size of a message allowed on the system
     * (-1 indicates no limit)
     * [displayed in diag]
     */
    protected long maxMessageSize;


    /**
     * variable which stores the value of Max memory on the
     * broker [ displayed in diag]
     */
    protected long maxAvailableMemory;

    /**
     * max memory as retrived from Runtime.maxMemory()
     */
    protected long maxSizeOfVM;

    /**
     * variable which stores the current Heap Size of the
     * broker [ displayed in diag]
     */
    protected long totalMemory;

    /**
     * variable which contains the currently available free
     * memory (based on total Memory .. not max memory)
     */
    protected long freeMemory;

    /**
     * variable which contains the currently used
     * memory (totalMemory - freeMemory)
     */
    protected long allocatedMemory;


    /**
     * variable which contains the true available memory
     * (maxAvailableMemory - (usedMemory)
     */
    protected long availMemory;



    /**
     * producer count
     */
    protected int producerCount = 0;


    /**
     * JMQSize value
     */
    protected int JMQSizeValue = 0;

    /**
     * JMQBytes value
     */
    protected long JMQBytesValue = 0;

    /**
     * JMQMaxMessageSize value
     */
    protected long JMQMaxMessageSize = 0;


    /*
     * Average Memory Usage
     */
    protected long averageMemUsage = 0;


    /**
     * High Memory Usage
     */
    protected long highestMemUsage = 0;

    /**
     * number of memory checks
     */
    protected long memoryCheckCount = 0; 
    
    /**
     * time in level
     */
    protected long timeInLevel = 0;

    /**
     * cumulative time in level
     */
    protected long cumulativeTimeInLevel = 0;
    

    /**
     * time Memory mgt was started
     */
    protected long startTime = 0;


    /**
     * amount of memory which must be freed in
     * a gc() to preform another iteration
     */
    private static final int GC_DELTA_DEFAULT = 1024; // 1K
    private static int GC_DELTA = Globals.getConfig().getIntProperty(
         Globals.IMQ + ".memory.gcdelta", GC_DELTA_DEFAULT);

    /**
     * amount of buffer memory in the system
     * (used when calculating memory limits to
     * add in a little extra room
     */
    private static long OVERHEAD_MEMORY_DEFAULT = 1024*10;

    private static long OVERHEAD_MEMORY = Globals.getConfig().getLongProperty(
                    Globals.IMQ+".memory.overhead", OVERHEAD_MEMORY_DEFAULT);


    private boolean turnOffMemory=
         !Globals.getConfig().getBooleanProperty(
         Globals.IMQ + ".memory_management.enabled", true);

    /**
     * the amount the system must be BELOW the
     * threshold of a level to re-enter that
     * level
     */
    private static int THRESHOLD_DELTA_DEFAULT = 1024;
    private static int THRESHOLD_DELTA = 
                 Globals.getConfig().getIntProperty(
                 Globals.IMQ+".memory.hysteresis", 
                 THRESHOLD_DELTA_DEFAULT);


    private HashMap callbacklist = new HashMap();
    private List pausedList = new ArrayList();

    private boolean active = false;

    /**
     * thread which wakes up and periodically checks memory
     */

    private class MyTimerTask extends TimerTask 
    {
           public void run() {
              checkMemoryState();
           }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("turnOffMemory", Boolean.valueOf(turnOffMemory));
        ht.put("active", Boolean.valueOf(active));
        ht.put("noForcedGC", Boolean.valueOf(NO_GC));
        ht.put("baseMemory", Long.valueOf(baseMemory));
        ht.put("maxMessageSize", Long.valueOf(maxMessageSize));
        ht.put("maxAvailableMemory", Long.valueOf(maxAvailableMemory));
        ht.put("maxSizeOfVM", Long.valueOf(maxSizeOfVM));
        ht.put("totalMemory", Long.valueOf(totalMemory));
        ht.put("freeMemory", Long.valueOf(freeMemory));
        ht.put("allocatedMemory", Long.valueOf(allocatedMemory));
        ht.put("availMemory", Long.valueOf(availMemory));
        ht.put("allocatedMemory", Long.valueOf(allocatedMemory));
        ht.put("JMQBytesValue", Long.valueOf(JMQBytesValue));
        ht.put("JMQMaxMessageSize", Long.valueOf(JMQMaxMessageSize));
        ht.put("averageMemUsage", Long.valueOf(averageMemUsage));
        ht.put("highestMemUsage", Long.valueOf(highestMemUsage));
        ht.put("memoryCheckCount", Long.valueOf(memoryCheckCount));
        ht.put("timeInLevel", Long.valueOf(timeInLevel));
        ht.put("cumulativeTimeInLevel", Long.valueOf(cumulativeTimeInLevel));
        ht.put("startTime", Long.valueOf(startTime));
        ht.put("OVERHEAD_MEMORY", Long.valueOf(OVERHEAD_MEMORY));
        ht.put("currentLevel", Integer.valueOf(currentLevel));
        ht.put("producerCount", Integer.valueOf(producerCount));
        ht.put("JMQSizeValue", Integer.valueOf(JMQSizeValue));
        ht.put("GC_DELTA", Integer.valueOf(GC_DELTA));
        ht.put("THRESHOLD_DELTA", Integer.valueOf(THRESHOLD_DELTA));
        ht.put("currentLevelString", currentLevelString);
        if (byteLevels != null) {
            ht.put("byteLevels#", Integer.valueOf(byteLevels.length));
            Vector v = new Vector();
            for (int i=0; i < byteLevels.length; i ++) {
                v.add(Long.valueOf(byteLevels[i]));
            }
            ht.put("byteLevels", v);
        }
        if (levelHandlers != null) {
            Vector v = new Vector();
            ht.put("levelHandlers#", Integer.valueOf(levelHandlers.length));
            for (int i=0; i < levelHandlers.length; i ++) {
                v.add(levelHandlers[i].getDebugState());
            }
            ht.put("levelHandlers", v);
        }
        ht.put("pausedList#", Integer.valueOf(pausedList.size()));
        ht.put("callbacklist#", Integer.valueOf(callbacklist.size()));
        if (pausedList.size() > 0) {
            Vector v = new Vector();
            for (int i=0; i < pausedList.size(); i ++) {
                v.add(pausedList.get(i).toString());
            }
            ht.put("pausedList", v);
         }
         if (callbacklist.size() > 0) {
            Vector v = new Vector();
            Iterator itr = callbacklist.values().iterator();
            while (itr.hasNext()) {
                v.add(itr.next().toString());
            }
            ht.put("callbacklist", v);
         }
           
        return ht;
    }

    private Object stateChangeLock = new Object();
    private Object valuesObjectLock = new Object();
    private Object timerObjectLock = new Object();

    private MyTimerTask mytimer = null;

    public MemoryManager() {
        if (turnOffMemory) {
            JMQSizeValue = -1;
            JMQBytesValue = -1;
            JMQMaxMessageSize = -1;
            return;
        }

        maxSizeOfVM = Runtime.getRuntime().maxMemory()
               - (64*1024*1024*1024);
        maxAvailableMemory = maxSizeOfVM
                          - OVERHEAD_MEMORY_DEFAULT;
        String[] levels = Globals.getConfig().getArray(
                Globals.IMQ + ".memory.levels");
        if (levels == null) {
            levels = new String[0];
        }
            
        try {
            levelHandlers = new MemoryLevelHandler[levels.length];
            byteLevels = new long[levels.length];
            for (int i = 0; i < levels.length; i ++) {
                 String fullclassname = Globals.getConfig().getProperty(
                     Globals.IMQ + "." + levels[i] + ".classname");
                 if (fullclassname == null) {
                     StringBuffer classname = new StringBuffer(levels[i]);
                     // capitalize first letter
                     char first = classname.charAt(0);
                     char upper = Character.toUpperCase(first);
                     classname.setCharAt(0, upper);
    
                     fullclassname = PACKAGE + classname;
                  }
                  if (DEBUG)
                      logger.log(Logger.DEBUG,
                          "Loading level " + levels[i] 
                          + " as " + fullclassname);
                  Class myclass = Class.forName(fullclassname);
                  Class cons_args[] = {String.class};
                  Constructor constructor = myclass.getConstructor(cons_args);
                  Object args[] = { levels[i]};
                  levelHandlers[i] = (MemoryLevelHandler)
                                constructor.newInstance(args);
                  byteLevels[i] = levelHandlers[i].getThresholdPercent() 
                                 * maxAvailableMemory / 100;
            }
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
                  BrokerResources.E_INTERNAL_BROKER_ERROR,
                  "loading memory manager", ex);
        }
    }

    public void stopManagement() {
        active = false;
        synchronized (timerObjectLock) {
            if (mytimer != null) {
                mytimer.cancel();
                mytimer = null;
            }
        }
    }

    public void startManagement() {
        if (turnOffMemory) {
              logger.log(Logger.DEBUG,
                 "Memory Management turned off");
              return;
        }
        if (active) {
              logger.log(Logger.DEBUG, "Memory Management already active");
              return;
        }
        active = true;

        // always display some basic startup information in DEBUG
        // (even if DEBUG flag is false)
        logger.log(Logger.DEBUG,
                "Starting Memory Management: adjusted available"
                + " memory is " + (maxAvailableMemory/1024) + "K");
        logger.log(Logger.DEBUG,"Explicitly GC : " + (!NO_GC));
        for (int i = 0; i < levelHandlers.length; i ++) 
            logger.log(Logger.DEBUG, "LEVEL:" + levelHandlers[i].levelName()
                    + "[percent = " 
                    + levelHandlers[i].getThresholdPercent() 
                    + "%"
                    + ", bytes = " 
                    + (byteLevels[i]/(long)1024) 
                    + "K]");
 
        
        DiagManager.register(this);
        startTime = System.currentTimeMillis();
        baseMemory = Runtime.getRuntime().totalMemory() - 
                     Runtime.getRuntime().freeMemory();
        // update state and memory variables
        currentLevel = calculateState();
        MemoryLevelHandler currentHandler = levelHandlers[currentLevel];
        currentLevelString = currentHandler.levelName();

        currentHandler.enter(false);
        // get initial Size/Bytes values
        JMQSizeValue = currentHandler.getMessageCount(
                      availMemory, producerCount);
        JMQBytesValue = currentHandler.getMemory(
                            availMemory, producerCount);
        JMQMaxMessageSize = maxAvailableMemory/2;
        updateMaxMessageSize(-2);

        mytimer = new MyTimerTask();
        long time = currentHandler.getTimeBetweenChecks();

        try {
            Globals.getTimer().schedule(mytimer, time, time);
        } catch (IllegalStateException ex) {
            logger.log(Logger.DEBUG,"Timer canceled " , ex);
        }

    }


    public int getJMQSize() {
        synchronized (valuesObjectLock) {
            return JMQSizeValue;
        }
    }

    public long getJMQBytes() {
        synchronized (valuesObjectLock) {
            return JMQBytesValue;
        }
    }

    public long getJMQMaxMsgBytes() {
        synchronized (valuesObjectLock) {
            return JMQMaxMessageSize;
        }
    }


    /**
     * request notification when the caller can resume.
     * specifying an optional # of bytes which may be available
     *
     * @param cb class requesting notification
     * @param bytes bytes of memory which must be available
     *              before the client can resume (or 0
     *              if free memory is not important)
     */



    public synchronized void notifyWhenAvailable(MemoryCallback cb, long bytes)
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "Registering notifyWhenAvailable at " 
                + bytes + " for " + cb);
        }
        if (turnOffMemory|| !active) {
             cb.resumeMemory(JMQSizeValue, JMQBytesValue, JMQMaxMessageSize);
             return;
        }
        checkMemoryState();
        synchronized (valuesObjectLock) {
              // force slowing using callback in orange
             if ((bytes == 0 || bytes < JMQBytesValue) && JMQSizeValue > 1) {
               cb.resumeMemory(JMQSizeValue, JMQBytesValue, JMQMaxMessageSize);
               return;
             }
         }
        // wait for notification
        MemoryCallbackEntry mce = (MemoryCallbackEntry)callbacklist.get(cb);
        if (mce == null) {
            mce = new MemoryCallbackEntry();
            callbacklist.put(cb, mce);
            mce.cb = cb;
            mce.keepAfterNotify = false;
        }
        mce.paused = true;
        mce.bytes = bytes;
        synchronized (pausedList) {
            pausedList.add(mce);
        }
        return;
    }



    /**
     * request notification when memory levels have changed
     * because the system has changed memory levels
     *
     * @param cb class requesting notification
     */


    public  void registerMemoryCallback(MemoryCallback cb)
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "Registering registerMemoryCallback  for " + cb);
        }
        MemoryCallbackEntry mce = new MemoryCallbackEntry();
        mce.cb = cb;
        mce.keepAfterNotify = true;
        mce.paused = false;
        mce.bytes = 0;
        synchronized (callbacklist) {
            callbacklist.put(cb, mce);
        }
    }

    public  void removeMemoryCallback(MemoryCallback cb) {
        synchronized (callbacklist) {
            callbacklist.remove(cb);
        }
    }


    public int getCurrentLevel() {
        synchronized (stateChangeLock) {
            return currentLevel;
         }
    }

    public String getCurrentLevelName() {
        synchronized (stateChangeLock) {
	    return (levelHandlers[currentLevel].localizedLevelName());
         }
    }

    /**
     * notifies all MemoryCallbackEntry objects that the bytes, count, etc
     * have changed.
     * If override is false, it will only notify non-paused clients
     *
     * @param override if true, notify even if paused
     */

    public  void notifyAllOfStateChange(boolean override) {

        if (turnOffMemory|| !active) {
             return;
        }

// GENERAL notes:

//    resumeMemory is called when a pauses state can be resumed
//    updateMemory is called for any other state change

        List l = new ArrayList();

        synchronized (callbacklist) {
            l.addAll(callbacklist.values());
        }

        long BytesValue = 0;
        int SizeValue = 0;
        long MaxMessageSize = 0;
        synchronized (valuesObjectLock) {
            BytesValue = JMQBytesValue;
            SizeValue = JMQSizeValue;
            MaxMessageSize = JMQMaxMessageSize;
        }

        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                    "notifyAllOfStateChange [size,bytes,max] = ["
                    +JMQSizeValue +","+ JMQBytesValue+","
                    + JMQMaxMessageSize + "]");
        }
        Iterator waiting = l.iterator();
        while (waiting.hasNext()) {
            MemoryCallbackEntry mce = (MemoryCallbackEntry)waiting.next();
            if (!override && mce.paused) {
                continue; // ignore it, will be cause when pause is handled
            } else if (mce.paused) { // we are paused but getting a notification
                                     // decide if we should be removed from
                                     // the pausedList
                if (mce.bytes == 0 && mce.bytes < BytesValue) {
                     // heck .. send a resume
                     mce.paused = false;
                     synchronized (pausedList) {
                         pausedList.remove(mce);
                     }
                     mce.cb.resumeMemory(SizeValue, BytesValue, MaxMessageSize);
                     if (DEBUG) {
                         logger.log(Logger.DEBUGMED, 
                             "\tresumeMemory for  "  +mce.bytes 
                             +" bytes on "+ mce.cb);
                     }
                } else { // not resuming, just notifying
                     if (DEBUG) {
                         logger.log(Logger.DEBUGMED,
                             "\tupdateMemory for  " + mce.cb);
                     }
                     mce.cb.updateMemory(SizeValue, BytesValue, MaxMessageSize);
                }
            } else { 
                if (DEBUG) {
                    logger.log(Logger.DEBUGMED, 
                        "\tupdateMemory for  " + mce.cb);
                }
                mce.cb.updateMemory(SizeValue, BytesValue, MaxMessageSize);
            }
        }
    }

    public  void checkAndNotifyPaused()
    {
        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                    "checkAndNotifyPaused [size,bytes,max] = ["
                    +JMQSizeValue +","+ JMQBytesValue
                    +","+ JMQMaxMessageSize + "]");
        }
        List l = null;
        synchronized (pausedList) {
            if (pausedList.isEmpty()) {
                return; // nothing to do
            }
            l = new ArrayList();
            l.addAll(pausedList);
        }
        long BytesValue = 0;
        int SizeValue = 0;
        long MaxMessageSize = 0;
        synchronized (valuesObjectLock) {
            BytesValue = JMQBytesValue;
            SizeValue = JMQSizeValue;
            MaxMessageSize = JMQMaxMessageSize;
        }

        Iterator waiting = l.iterator();
        while (waiting.hasNext()) {
            MemoryCallbackEntry mce = (MemoryCallbackEntry)waiting.next();
            if (mce.bytes == 0 && mce.bytes < BytesValue) {
                     // heck .. send a resume
                 mce.paused = false;
                 waiting.remove();
                 synchronized (pausedList) {
                     pausedList.remove(mce);
                 }
                 if (DEBUG) {
                     logger.log(Logger.DEBUGMED, "\tresumeMemory for  " +
                         mce.bytes +" bytes on " + mce.cb);
                 }
                 mce.cb.resumeMemory(SizeValue, BytesValue, MaxMessageSize);
            }
         
        }
    }


    public String toString() {
        return "MemoryManager";
    }


    public synchronized void addProducer() {
        producerCount++;
        if (DEBUG) {
            logger.log(Logger.DEBUG, "addProducer " +producerCount);
        }
    }

    public synchronized void removeProducer() {
        producerCount--;
        if (DEBUG) {
            logger.log(Logger.DEBUG, "removeProducer " + producerCount);
        }
    }

    public synchronized void removeProducer(int cnt) {
        producerCount -= cnt;
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "removeProducer(" + cnt + ") " + producerCount);
        }
    }

    private boolean quickState(int state) {
        if (turnOffMemory|| !active) {
             return false;
        }
        freeMemory = Runtime.getRuntime().freeMemory();
        totalMemory = Runtime.getRuntime().totalMemory();
        allocatedMemory = totalMemory - freeMemory;
        if (state < (byteLevels.length -1) && 
            allocatedMemory > byteLevels[state +1]) {
            // increased
            return true;
        }
        return false;
    }

    /**
     * @param size bytes addition to check
     * @return false if allocate size of mem causes level change
     *               or currentLevel not in lowest level
     */
    public boolean allocateMemCheck(long size) { 
        if (DEBUG) {
            int level = currentLevel;
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < byteLevels.length; i++) {
                sb.append("byteLevel["+i+"]="+byteLevels[i]+", ");
            }
            long freem = Runtime.getRuntime().freeMemory();
            long totalm = Runtime.getRuntime().totalMemory();
            long allocatedm = totalm - freem;
           
            logger.log(logger.INFO, 
            "MemoryManager: turnOffMemory="+turnOffMemory+", active="+active+
            ", check size="+size+", currentLevel="+level+"("+byteLevels.length+"), "+sb.toString()+
            ", freemem="+freem+", totalmem="+totalm+", allocatedmem="+allocatedm);
        }

        if (turnOffMemory|| !active) {
             return true;
        }

        int currentl = currentLevel;
        if (currentl >= (byteLevels.length -1)) return false; 

        long freem = Runtime.getRuntime().freeMemory();
        long totalm = Runtime.getRuntime().totalMemory();
        long allocatedm = totalm - freem + size;
        if (allocatedm > byteLevels[byteLevels.length-1]) {
            return false;
        }
        return true;
    }

    private int calculateState() {
        // update all of the various memory values which
        // are required for determining the level
        if (turnOffMemory|| !active) {
             return 0;
        }

        freeMemory = Runtime.getRuntime().freeMemory();
        totalMemory = Runtime.getRuntime().totalMemory();
        allocatedMemory = totalMemory - freeMemory;
        //long foo = Runtime.getRuntime().maxMemory();

        if (allocatedMemory > maxAvailableMemory)
            recalcMemory();

        availMemory = maxAvailableMemory - allocatedMemory;

        int targetstate = 0;
        for (int i = byteLevels.length-1; i >= 0; i --) {
            if (allocatedMemory > byteLevels[i]) {
                targetstate = i;
                break;
            }
        }

        int returnstate = targetstate;
        if (targetstate < (byteLevels.length -1)
           && allocatedMemory > byteLevels[targetstate+1]- THRESHOLD_DELTA) {
            if (DEBUG) {
                logger.log(Logger.DEBUGMED,
                    "calculateState:didnt meet delta requirements");
            }
            returnstate = targetstate + 1;
        }

        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                "calculateState [oldstate, calcstate, returnstate] = [" 
                + currentLevel + "," + targetstate + "," + returnstate + "]");
        }

        return returnstate;

    }


    /**
     * Update the MaxMessageSize:
     * @param size value of MAX_MESSAGE_SIZE property (or -2 if
     *        being called because maxAvailableMemory was updated)
     */
    public void updateMaxMessageSize(long size) {
        synchronized (valuesObjectLock) {
            if (size != -2)
                maxMessageSize = size;

            JMQMaxMessageSize = maxAvailableMemory/2;
            if (maxMessageSize > -1 && maxMessageSize < JMQMaxMessageSize) {
                 JMQMaxMessageSize = maxMessageSize;
            }
            if (DEBUG) {
                logger.log(Logger.DEBUG, 
                    "updateMaxMessageSize [size, JMQMaxMessageSize] = [" 
                    + size + "," + JMQMaxMessageSize + "]");
            }
        }
    }


    /*
     * main processing for the memory manager:
     *
     * Logic:
     *
     * First the new state (based on free memory) is
     * calculated.
     *
     * Next the system tries to determine if we have
     * entered a new "higher" (tighter) memory state
     *     - system performs gc and cleanup tasks
     *          until all tasks are done OR we have
     *          moved back to our old memory state
     *     - if its still in the same (new) state, 
     *          the system enters the new state
     *
     * If we haven't entered a higher memory state, 
     * the system tries to determine if we have entered
     * a "lower" memory state.
     *     - if we are in a lower memory state the system
     *       calls leave() on the last state
     *     -  sets boolean to update waiting clients (if necessary)
     *
     * If we are still in the same state as last iteration
     * the system:
     *     may try to gc (if we periodically gc in the
     *     existing level) -> determined by gcIterations
     *
     *
     *     - calculate the current state
     *     - is the new state > the old state
     *         yes - entering lower memory state
     *            - garbage collect -> still in state
     *                  yes -> try and clean up memory
     *                     after cleanup-> still in state
     *                          yes -> Move to new state
     * Finally, the memory count and memory size values for
     * clients are updated AND any resume messages are
     * sent
     */
    public void quickMemoryCheck() {
        if (quickState(currentLevel)) {
            // we are SLOW
            checkMemoryState();
        }
    }

    boolean completedRunningCleanup = true;
    int cleanupCnt = 0;

    public  void checkMemoryState() {
        if (turnOffMemory|| !active) {
             return;
        }
        // OK -> first check memory
        // calculate new state

        if (DEBUG) {
            logger.log(Logger.DEBUG, "checkMemoryState  " + memoryCheckCount);
        }


        boolean notify = false;

        int oldLevel = 0;
        int newState = 0;
        synchronized (stateChangeLock) {
            newState = calculateState();
            oldLevel = currentLevel;
            currentLevel = newState;    
        }
        MemoryLevelHandler currentHandler = levelHandlers[oldLevel];

        if (newState != oldLevel) {

            MemoryLevelHandler newHandler = levelHandlers[newState];

            if (newState > oldLevel) { // entered new state
                gc(newHandler.gcCount());
                newState = calculateState();
            }
            for (int i = oldLevel; i < newState; i ++) { // higher
                notify = levelHandlers[i+1].enter(false);
                notify |= levelHandlers[i].leave(true);
            }
            for (int i = oldLevel; i > newState; i --) { // lower
                notify |= levelHandlers[i-1].enter(true);
                notify |= levelHandlers[i].leave(false);
            }
            newHandler = levelHandlers[newState];

            // update variables 
            synchronized (valuesObjectLock) {
                JMQSizeValue = newHandler.getMessageCount(
                        availMemory, producerCount);
                JMQBytesValue = newHandler.getMemory(
                        availMemory, producerCount);
            }

            if (notify) {
                notifyAllOfStateChange(true);
            }
            currentLevel = newState;
            if (newState > oldLevel) { // cleanup
                completedRunningCleanup = false;
                cleanupCnt = 0;
                completedRunningCleanup = 
                    levelHandlers[newState].cleanup(cleanupCnt++);
            } else if (newState < oldLevel) {
                completedRunningCleanup = true;
                cleanupCnt = 0;
            }
            if (newState != oldLevel) {
                String args[] = {levelHandlers[newState].localizedLevelName(), 
                        levelHandlers[oldLevel].localizedLevelName(), 
                        String.valueOf((allocatedMemory/1024)), 
                        String.valueOf((
                          allocatedMemory*100/maxAvailableMemory))};

                logger.log(Logger.INFO, 
                           BrokerResources.I_CHANGE_OF_MEMORY_STATE,
                           args);

                Agent agent = Globals.getAgent();
                if (agent != null)  {
                    agent.notifyResourceStateChange(levelHandlers[oldLevel].localizedLevelName(),
					levelHandlers[newState].localizedLevelName(),
					null);
                }

                currentLevel = newState;
                currentHandler = newHandler;    
                currentLevelString = currentHandler.levelName(); // diags

                synchronized (timerObjectLock) {

                    // set new timer
                    if (mytimer != null) {
                        mytimer.cancel();
                        mytimer = null;
                    }
                    mytimer = new MyTimerTask();
                    long time = currentHandler.getTimeBetweenChecks();
                    try {
                        Globals.getTimer(true).schedule(mytimer, time, time);
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Timer canceled " , ex);
                    }
                }
            }

        } else {
            if (!completedRunningCleanup) {
                completedRunningCleanup = levelHandlers[oldLevel].cleanup(
                         cleanupCnt++);
            }
            // periodically perform gcs on some iterations
            if (currentHandler.gcIteration() != 0 &&
                memoryCheckCount % currentHandler.gcIteration() == 0) {
                 gc(); 
            }
            // update variables 
            synchronized (valuesObjectLock) {
                JMQSizeValue = currentHandler.getMessageCount(
                        availMemory, producerCount);
                JMQBytesValue = currentHandler.getMemory(
                        availMemory, producerCount);
            }


            if (JMQSizeValue > 0)
                checkAndNotifyPaused();
        }

        if (allocatedMemory > highestMemUsage)
             highestMemUsage = allocatedMemory;

        // XXX racer revisit
        // we may not want to calculate this on each call
        averageMemUsage = ((averageMemUsage*memoryCheckCount) 
            + allocatedMemory)/ (memoryCheckCount + 1);

        memoryCheckCount++;



    }

    public void forceRedState() {
        long size =   Runtime.getRuntime().maxMemory() -
                      Runtime.getRuntime().freeMemory();
        logger.log(Logger.WARNING, BrokerResources.W_EARLY_OUT_OF_MEMORY,
                String.valueOf(size), 
                String.valueOf(Runtime.getRuntime().maxMemory()));
        recalcMemory();
        checkMemoryState();
    }

    public void recalcMemory() {
        // update memory values
        maxAvailableMemory = Runtime.getRuntime().totalMemory();
        updateMaxMessageSize(-2);

        for (int i = 0; i < byteLevels.length; i ++) {
            byteLevels[i] = levelHandlers[i].getThresholdPercent() 
                           * maxAvailableMemory / 100;
        }
    }


    protected void gc() {
        gc(1, GC_DELTA);
    }
    protected void gc(int count) {
        gc(count, GC_DELTA);
    }

    protected void gc(int count, long delta) {
        if (!NO_GC) {
        	 logger.log(Logger.DEBUG,"calling Runtime.freeMemory()");
            long free = Runtime.getRuntime().freeMemory(); 
            int i = 0;
            for (i = 0; i < count; i ++) {
                Runtime.getRuntime().gc();
                long newfree = Runtime.getRuntime().freeMemory();
                if (free - newfree > delta) {
                     // we freed enough memory
                     break;
                }

            }
        }
        else{
        	// do nothing
        	
        }
    }



    /* 
     * ---------------------------------------------------
     *             DIAG SUPPORT
     * ---------------------------------------------------
     */
    public synchronized List getDictionary() {
        if (diagDictionary == null) {
            diagDictionary = new ArrayList();
            diagDictionary.add(new DiagDictionaryEntry("currentLevelString", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("allocatedMemory", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("timeInLevel", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("cumulativeTimeInLevel", 
                                 DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("JMQSizeValue", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("JMQBytesValue", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("JMQMaxMessageSize", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("totalMemory", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("freeMemory", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("availMemory", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("maxAvailableMemory", 
                                 DiagManager.CONSTANT));
            diagDictionary.add(new DiagDictionaryEntry("producerCount", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("averageMemUsage", 
                                 DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("memoryCheckCount", 
                                 DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("highestMemUsage", 
                                 DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("maxSizeOfVM", 
                                 DiagManager.CONSTANT));

        }
        return diagDictionary;
    }




    public  void update() {
        calculateState(); // update memory, ignore new state
        MemoryLevelHandler currentHandler = null;
        synchronized (stateChangeLock) {
            currentHandler = levelHandlers[currentLevel];
        }
        timeInLevel = currentHandler.getCurrentTimeInLevel();
        cumulativeTimeInLevel = currentHandler.getTotalTimeInLevel();
    }

    public String getPrefix() {
        return "mem_mgr";
    }

    public String getTitle() {
        return "MemoryManager";
    }


    public String toDebugString() {
        StringBuffer retstr = new StringBuffer();
        retstr.append("MemoryManager: [" + currentLevel + "]"+"\n");
        for (int i = 0; i < levelHandlers.length; i ++) {
            retstr.append("\t" + i + "\t" + levelHandlers[i].levelName()
                      +"\t" + levelHandlers[i].getThresholdPercent()
                      + "\t" + byteLevels[i] + "\n");
        }
        for (int i = 0; i < levelHandlers.length; i ++) {
               retstr.append("-------------------------------\n");
               retstr.append(levelHandlers[i].toDebugString()+" \n\n");
        }
        return retstr.toString();
        
    }



}

class MemoryCallbackEntry
{
    MemoryCallback cb = null;
    long bytes = 0;
    boolean paused = false;
    boolean keepAfterNotify = false;

    public String toString() {
        return "MCE[ bytes=" + bytes +", paused=" + paused
            + ", keepAfterNotify="+keepAfterNotify
            + ", object = " + cb.toString() + "]";
    }
}
