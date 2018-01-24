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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;

/**
 */
public class ChangeRecordInfo {

    public static final int TYPE_RESET_PERSISTENCE = ClusterBroadcast.TYPE_RESET_PERSISTENCE;

    public static final int FLAG_LOCK = 1; 

    //bits in flag for new interest (durable sub)
    public static final int SHARED    = 0x00000001;
    public static final int JMSSHARED = 0x00000002;

    private Long seq = null;
    private String uuid = null;
    private String resetUUID = null;
    private byte[] record = null;
    private int type = 0;
    private String ukey = null;
    private boolean isduraAdd = false;
    private long timestamp = 0;
    private boolean isSelectAll = false;
    private int flag = 0; 

    public ChangeRecordInfo() {}
    
    /*
     * @param ukey must not be null if this object is going to persist store
     */
    public ChangeRecordInfo(Long seq, String uuid, byte[] record,
                            int type, String ukey, long timestamp) {
        this.seq = seq;
        this.uuid = uuid;
        this.record = record;
        this.type = type;
        this.ukey = ukey;
        this.timestamp = timestamp;
    }

    public ChangeRecordInfo(byte[] record, long timestamp) {
        this.record = record;
        this.timestamp = timestamp;
    }

    public void setRecord(byte[] rec) {
        record = rec;
    }

    public byte[] getRecord() {
        return record;
    }

    public void setTimestamp(long ts) {
        timestamp = ts;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }


    public String getUUID() {
        return uuid;
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public void setResetUUID(String uuid) {
        this.resetUUID = uuid;
    }

    public String getResetUUID() {
        return this.resetUUID;
    }

    public int getType() {
        return type;
    }

    public void setType(int t) {
        type = t;
    }

    public String getUKey() {
        return ukey;
    }

    public void setUKey(String k) {
        ukey = k;
    }

    public boolean isDuraAddRecord() {
        return isduraAdd;
    }

    public void setDuraAdd(boolean b) {
        isduraAdd = b;
    }

    public void setFlagBit(int bit) {
        flag |= bit;
    }

    public int getFlag() {
        return flag;
    }
    public static String getFlagString(int f) {
        if ((f & SHARED) == SHARED) {
            if ((f & JMSSHARED) == JMSSHARED) {
                return "jms";
            } 
            return "mq";
        }
        return "";
    }   

    public void setIsSelectAll(boolean b) {
        isSelectAll = b;
    }

    public boolean isSelectAll() {
        return isSelectAll;
    } 

    public String toString() {
        return "seq="+seq+", uuid="+uuid+", type="+type+
            ", timestamp="+timestamp+", resetUUId="+resetUUID;
    }
}
