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
 * @(#)DestMetricsCounters.java	1.19 06/27/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.HashMap;

/**
 * DestinationInfo encapsulates information about a JMQ Destination. It is
 * used to pass this information between the Broker and an
 * administration client.
 */
public class DestMetricsCounters extends HashMap 
{

    static final long serialVersionUID = 8342915311065017568L;

    public static final String MESSAGES_IN = "numMsgsIn";
    public static final String MESSAGES_OUT = "numMsgsOut";
    public static final String MESSAGES_IN_BYTES = "msgBytesIn";
    public static final String MESSAGES_OUT_BYTES = "msgBytesOut";

    public static final String HIGH_WATER_MESSAGES = "peakNumMsgs";
    public static final String HIGH_WATER_MESSAGE_BYTES = "peakTotalMsgBytes";
    public static final String HIGH_WATER_LARGEST_MSG_BYTES = "peakMsgBytes";

    public static final String CURRENT_MESSAGES = "numMsgs";
    public static final String CURRENT_MESSAGE_BYTES = "totalMsgBytes";

    public static final String AVERAGE_MESSAGES = "avgNumMsgs";
    public static final String AVERAGE_MESSAGE_BYTES = "avgTotalMsgBytes";

    public static final String ACTIVE_CONSUMERS = "numActiveConsumers";
    public static final String FAILOVER_CONSUMERS = "numBackupConsumers";
    public static final String HW_A_CONSUMERS = "peakNumActiveConsumers";
    public static final String HW_F_CONSUMERS = "peakNumBackupConsumers";
    public static final String AVG_A_CONSUMERS = "avgNumActiveConsumers";
    public static final String AVG_F_CONSUMERS = "avgNumBackupConsumers";

    public static final String NUM_CONSUMERS = "numConsumers";
    public static final String HW_N_CONSUMERS = "peakNumConsumers";
    public static final String AVG_N_CONSUMERS = "avgNumConsumers";

    public static final String DISK_RESERVED = "diskReserved";
    public static final String DISK_USED = "diskUsed";
    public static final String DISK_UTILIZATION_RATIO = "diskUtilizationRatio";

    public static final String EXPIRED_CNT = "numExpiredMsgs";
    public static final String PURGED_CNT = "numPurgedMsgs";
    public static final String ACKED_CNT = "numAckedMsgs";
    public static final String DISCARD_CNT = "numDiscardedMsgs";
    public static final String REJECT_CNT = "numRejectedMsgs";
    public static final String ROLLBACK_CNT = "numRolledbackMsgs";

    public long    timeStamp = 0;

    public DestMetricsCounters() {
        super();
        setLongProperty(MESSAGES_IN, 0); 
        setLongProperty(MESSAGES_OUT, 0);
        setIntProperty(HIGH_WATER_MESSAGES, 0); //int
        setLongProperty(HIGH_WATER_MESSAGE_BYTES, 0); //long
        setLongProperty(HIGH_WATER_LARGEST_MSG_BYTES, 0); //long
        setIntProperty(ACTIVE_CONSUMERS, 0); //int
        setIntProperty(FAILOVER_CONSUMERS, 0); //int
        setIntProperty(HW_A_CONSUMERS, 0); //int
        setIntProperty(HW_F_CONSUMERS, 0); //int
        setIntProperty(NUM_CONSUMERS, 0); //int
        setIntProperty(HW_N_CONSUMERS, 0); //int
        setIntProperty(AVG_N_CONSUMERS, 0); //int

        setLongProperty(MESSAGES_IN_BYTES, 0); //long
        setLongProperty(MESSAGES_OUT_BYTES, 0); //long
        setIntProperty(CURRENT_MESSAGES, 0); //long
        setLongProperty(CURRENT_MESSAGE_BYTES, 0); //long
        setIntProperty(AVERAGE_MESSAGES, 0); //long
        setLongProperty(AVERAGE_MESSAGE_BYTES, 0); //long

        setLongProperty(DISK_RESERVED, 0); //long
        setLongProperty(DISK_USED, 0); //long
        setIntProperty(DISK_UTILIZATION_RATIO, 0); //int

        setIntProperty(EXPIRED_CNT, 0); //int
        setIntProperty(PURGED_CNT, 0); //int
        setIntProperty(ACKED_CNT, 0); //int
        setIntProperty(DISCARD_CNT, 0); //int
        setIntProperty(REJECT_CNT, 0); //int
        setIntProperty(ROLLBACK_CNT, 0); //int
    }
    public HashMap getHashMap()
    {
        return new HashMap(this);
    }
    
    public long getLongProperty(String propName) {
        Long l = (Long)get(propName);
        if (l == null) {
            return (long)0;
        } 
        return l.longValue(); 
    }

    public int getIntProperty(String propName) {
        Integer l = (Integer)get(propName);
        if (l == null) {
            return 0;
        } 
        return l.intValue(); 
    }

    public void setLongProperty(String propName, long value) {
        Long l = Long.valueOf(value);
        put(propName, l);
    }

    public void setIntProperty(String propName, int value) {
        Integer l = Integer.valueOf(value);
        put(propName, l);
    }

    public long getMessagesIn() {
        return getLongProperty(MESSAGES_IN);
    }
    public long getMessagesOut() {
        return getLongProperty(MESSAGES_OUT);
    }

    public int getHighWaterMessages() {
        return getIntProperty(HIGH_WATER_MESSAGES);
    }
    public long getHighWaterMessageBytes() {
        return getLongProperty(HIGH_WATER_MESSAGE_BYTES);
    }
    public long getHighWaterLargestMsgBytes() {
        return getLongProperty(HIGH_WATER_LARGEST_MSG_BYTES);
    }
    public int getActiveConsumers() {
        return getIntProperty(ACTIVE_CONSUMERS);
    }
    public int getFailoverConsumers() {
        return getIntProperty(FAILOVER_CONSUMERS);
    }
    public int getHWActiveConsumers() {
        return getIntProperty(HW_A_CONSUMERS);
    }
    public int getHWFailoverConsumers() {
        return getIntProperty(HW_F_CONSUMERS);
    }
    public int getAvgActiveConsumers() {
        return getIntProperty(AVG_A_CONSUMERS);
    }
    public int getAvgFailoverConsumers() {
        return getIntProperty(AVG_F_CONSUMERS);
    }
    public int getNumConsumers() {
        return getIntProperty(NUM_CONSUMERS);
    }
    public int getHWNumConsumers() {
        return getIntProperty(HW_N_CONSUMERS);
    }
    public int getAvgNumConsumers() {
        return getIntProperty(AVG_N_CONSUMERS);
    }
    public long getMessageBytesIn() {
        return getLongProperty(MESSAGES_IN_BYTES);
    }
    public long getMessageBytesOut() {
        return getLongProperty(MESSAGES_OUT_BYTES);
    }
    public int getCurrentMessages() {
        return getIntProperty(CURRENT_MESSAGES);
    }
    public long getCurrentMessageBytes() {
        return getLongProperty(CURRENT_MESSAGE_BYTES);
    }
    public int getAverageMessages() {
        return getIntProperty(AVERAGE_MESSAGES);
    }
    public long getAverageMessageBytes() {
        return getLongProperty(AVERAGE_MESSAGE_BYTES);
    }

    public long getDiskReserved() {
        return getLongProperty(DISK_RESERVED);
    }
    public long getDiskUsed() {
        return getLongProperty(DISK_USED);
    }
    public int getDiskUtilizationRatio() {
        return getIntProperty(DISK_UTILIZATION_RATIO);
    }

    public int getExpiredMsgCnt() {
        return getIntProperty(EXPIRED_CNT);
    }

    public int getPurgedMsgCnt() {
        return getIntProperty(PURGED_CNT);
    }

    public int getAckedMsgCnt() {
        return getIntProperty(ACKED_CNT);
    }

    public int getDiscardedMsgCnt() {
        return getIntProperty(DISCARD_CNT);
    }

    public int getRejectedMsgCnt() {
        return getIntProperty(REJECT_CNT);
    }
    public int getRollbackMsgCnt() {
        return getIntProperty(ROLLBACK_CNT);
    }

    public void setMessagesIn(long cnt) {
         setLongProperty(MESSAGES_IN, cnt);
    }
    public void setMessagesOut(long cnt) {
         setLongProperty(MESSAGES_OUT, cnt);
    }
    public void setHighWaterMessages(int cnt) {
         setIntProperty(HIGH_WATER_MESSAGES, cnt);
    }
    public void setHighWaterMessageBytes(long cnt) {
         setLongProperty(HIGH_WATER_MESSAGE_BYTES, cnt);
    }
    public void setHighWaterLargestMsgBytes(long cnt) {
         setLongProperty(HIGH_WATER_LARGEST_MSG_BYTES, cnt);
    }
    public void setActiveConsumers(int cnt) {
         setIntProperty(ACTIVE_CONSUMERS, cnt);
    }
    public void setFailoverConsumers(int cnt) {
         setIntProperty(FAILOVER_CONSUMERS, cnt);
    }
    public void setHWActiveConsumers(int cnt) {
         setIntProperty(HW_A_CONSUMERS, cnt);
    }
    public void setHWFailoverConsumers(int cnt) {
         setIntProperty(HW_F_CONSUMERS, cnt);
    }
    public void setAvgActiveConsumers(int cnt) {
         setIntProperty(AVG_A_CONSUMERS, cnt);
    }
    public void setAvgFailoverConsumers(int cnt) {
         setIntProperty(AVG_F_CONSUMERS, cnt);
    }
    public void setNumConsumers(int cnt) {
         setIntProperty(NUM_CONSUMERS, cnt);
    }
    public void setHWNumConsumers(int cnt) {
         setIntProperty(HW_N_CONSUMERS, cnt);
    }
    public void setAvgNumConsumers(int cnt) {
         setIntProperty(AVG_N_CONSUMERS, cnt);
    }


    public void setMessageBytesIn(long cnt) {
        setLongProperty(MESSAGES_IN_BYTES, cnt);
    }
    public void setMessageBytesOut(long cnt) {
        setLongProperty(MESSAGES_OUT_BYTES, cnt);
    }
    public void setCurrentMessages(int cnt) {
        setIntProperty(CURRENT_MESSAGES, cnt);
    }
    public void setCurrentMessageBytes(long cnt) {
        setLongProperty(CURRENT_MESSAGE_BYTES, cnt);
    }
    public void setAverageMessages(int cnt) {
        setIntProperty(AVERAGE_MESSAGES, cnt);
    }
    public void setAverageMessageBytes(long cnt) {
        setLongProperty(AVERAGE_MESSAGE_BYTES, cnt);
    }

    public void setDiskReserved(long cnt) {
        setLongProperty(DISK_RESERVED, cnt);
    }
    public void setDiskUsed(long cnt) {
        setLongProperty(DISK_USED, cnt);
    }
    public void setUtilizationRatio(int ratio) {
        setIntProperty(DISK_UTILIZATION_RATIO, ratio);
    }


    public void setExpiredMsgCnt(int val) {
        setIntProperty(EXPIRED_CNT, val);
    }

    public void setPurgedMsgCnt(int val){
        setIntProperty(PURGED_CNT, val);
    }

    public void setAckedMsgCnt(int val) {
        setIntProperty(ACKED_CNT, val);
    }

    public void setDiscardedMsgCnt(int val) {
        setIntProperty(DISCARD_CNT, val);
    }

    public void setRejectedMsgCnt(int val) {
        setIntProperty(REJECT_CNT, val);
    }

    public void setRollbackMsgCnt(int val) {
        setIntProperty(ROLLBACK_CNT, val);
    }

/* DEBUG
    public static void main(String args[]) {
        DestMetricsCounters dmc = new DestMetricsCounters();
        System.out.println("----------------- BEFORE SET ----------");
        System.out.println("getMessagesIn = " 
            + dmc.getMessagesIn());
        System.out.println("getMessagesOut = " 
            + dmc.getMessagesOut());
        System.out.println("getMessagesInRate = " 
            + dmc.getMessagesInRate());
        System.out.println("getMessagesOutRate = " 
            + dmc.getMessagesOutRate());
        System.out.println("getHighWaterMessages = " 
            + dmc.getHighWaterMessages());
        System.out.println("getHighWaterMessageBytes = " 
            + dmc.getHighWaterMessageBytes());
        System.out.println("getHighWaterLargestMsgBytes = " 
            + dmc.getHighWaterLargestMsgBytes());
        System.out.println("getActiveConsumers = " 
            + dmc.getActiveConsumers());
        System.out.println("getFailoverConsumers = " 
            + dmc.getFailoverConsumers());
        System.out.println("getHWActiveConsumers = " 
            + dmc.getHWActiveConsumers());
        System.out.println("getHWFailoverConsumers = " 
            + dmc.getHWFailoverConsumers());
        System.out.println("getAvgActiveConsumers = " 
            + dmc.getAvgActiveConsumers());
        System.out.println("getAvgFailoverConsumers = " 
            + dmc.getAvgFailoverConsumers());
        System.out.println("getMessageBytesIn = " 
            + dmc.getMessageBytesIn());
        System.out.println("getMessageBytesOut = " 
            + dmc.getMessageBytesOut());
        System.out.println("getMessageBytesInRate = " 
            + dmc.getMessageBytesInRate());
        System.out.println("getMessageBytesOutRate = " 
            + dmc.getMessageBytesOutRate());
        System.out.println("getCurrentMessages = " 
            + dmc.getCurrentMessages());
        System.out.println("getCurrentMessageBytes = " 
            + dmc.getCurrentMessageBytes());
        System.out.println("getAverageMessages = " 
            + dmc.getAverageMessages());
        System.out.println("getAverageMessageBytes = " 
            + dmc.getAverageMessageBytes());
        System.out.println("----------------- SETTING ----------");
        int icnt = 10;
        long lcnt = 100;
        dmc.setMessagesIn(icnt ++);
        dmc.setMessagesOut(icnt ++);
        dmc.setMessagesInRate(icnt ++);
        dmc.setMessagesOutRate(icnt ++);
        dmc.setHighWaterMessages(icnt ++);
        dmc.setHighWaterMessageBytes(lcnt ++);
        dmc.setHighWaterLargestMsgBytes(lcnt ++);
        dmc.setActiveConsumers(icnt ++);
        dmc.setFailoverConsumers(icnt ++);
        dmc.setHWActiveConsumers(icnt ++);
        dmc.setHWFailoverConsumers(icnt ++);
        dmc.setAvgActiveConsumers(icnt ++);
        dmc.setAvgFailoverConsumers(icnt ++);
        dmc.setMessageBytesIn(lcnt ++);
        dmc.setMessageBytesOut(lcnt ++);
        dmc.setMessageBytesInRate(lcnt ++);
        dmc.setMessageBytesOutRate(lcnt ++);
        dmc.setCurrentMessages(icnt ++);
        dmc.setCurrentMessageBytes(lcnt ++);
        dmc.setAverageMessages(icnt ++);
        dmc.setAverageMessageBytes(lcnt ++);
        System.out.println("----------------- AFTER SET ----------");
        System.out.println("getMessagesIn = " 
            + dmc.getMessagesIn());
        System.out.println("getMessagesOut = " 
            + dmc.getMessagesOut());
        System.out.println("getMessagesInRate = " 
            + dmc.getMessagesInRate());
        System.out.println("getMessagesOutRate = " 
            + dmc.getMessagesOutRate());
        System.out.println("getHighWaterMessages = " 
            + dmc.getHighWaterMessages());
        System.out.println("getHighWaterMessageBytes = " 
            + dmc.getHighWaterMessageBytes());
        System.out.println("getHighWaterLargestMsgBytes = " 
            + dmc.getHighWaterLargestMsgBytes());
        System.out.println("getActiveConsumers = " 
            + dmc.getActiveConsumers());
        System.out.println("getFailoverConsumers = " 
            + dmc.getFailoverConsumers());
        System.out.println("getHWActiveConsumers = " 
            + dmc.getHWActiveConsumers());
        System.out.println("getHWFailoverConsumers = " 
            + dmc.getHWFailoverConsumers());
        System.out.println("getAvgActiveConsumers = " 
            + dmc.getAvgActiveConsumers());
        System.out.println("getAvgFailoverConsumers = " 
            + dmc.getAvgFailoverConsumers());
        System.out.println("getMessageBytesIn = " 
            + dmc.getMessageBytesIn());
        System.out.println("getMessageBytesOut = " 
            + dmc.getMessageBytesOut());
        System.out.println("getMessageBytesInRate = " 
            + dmc.getMessageBytesInRate());
        System.out.println("getMessageBytesOutRate = " 
            + dmc.getMessageBytesOutRate());
        System.out.println("getCurrentMessages = " 
            + dmc.getCurrentMessages());
        System.out.println("getCurrentMessageBytes = " 
            + dmc.getCurrentMessageBytes());
        System.out.println("getAverageMessages = " 
            + dmc.getAverageMessages());
        System.out.println("getAverageMessageBytes = " 
            + dmc.getAverageMessageBytes());
        System.out.println("----------------- DONE ----------");
    
    }
*/
}
