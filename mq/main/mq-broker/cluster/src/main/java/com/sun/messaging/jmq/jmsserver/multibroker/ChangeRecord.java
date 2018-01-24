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

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.io.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterDestInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterSubscriptionInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.RaptorProtocol;

/**
 * An object of this class is used to represent a change record 
 * for processing at runtime in broker
 *
 * This class also contains static methods related to change record processing
 */
public class ChangeRecord {

    private static boolean DEBUG = false || Globals.getConfig().getBooleanProperty(
        Globals.IMQ+".debug.com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecord") ||
		(Globals.getLogger().getLevel() <= Logger.DEBUG);

    public static final int TYPE_RESET_PERSISTENCE = 
                  ProtocolGlobals.G_RESET_PERSISTENCE;

    public static final String UUID_PROPERTY = "UUID"; 


    private GPacket gp;
    private boolean discard = false;
    protected int operation = ProtocolGlobals.G_RESET_PERSISTENCE;

    public static ChangeRecord makeChangeRecord(byte[] rec) throws IOException {
        return makeChangeRecord(rec, null);
    }

    public static ChangeRecord makeChangeRecord(byte[] rec, String uuid)
    throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(rec);
        GPacket pkt = GPacket.getInstance();
        pkt.read(bis);
        if (uuid != null) {
            pkt.putProp(UUID_PROPERTY, uuid);
        }
        return makeChangeRecord(pkt);
    }

    public static ChangeRecord makeChangeRecord(GPacket pkt) {

        ChangeRecord cr = null;

        if (pkt.getType() == ProtocolGlobals.G_NEW_INTEREST ||
            pkt.getType() == ProtocolGlobals.G_REM_DURABLE_INTEREST) {
            cr = new InterestUpdateChangeRecord(pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_UPDATE_DESTINATION ||
            pkt.getType() == ProtocolGlobals.G_REM_DESTINATION) {
            cr = new DestinationUpdateChangeRecord(pkt);
        }
        else if (pkt.getType() == ProtocolGlobals.G_RESET_PERSISTENCE) {
            cr = new ChangeRecord();
        } else {
            throw new RuntimeException("Unexpected change record type in packet "+
                          ProtocolGlobals.getPacketTypeString(pkt.getType()));
        }

        cr.gp = pkt;
        cr.discard = false;
        return cr;
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        gp.write(bos);
        bos.flush();
        return bos.toByteArray();
    }

    public String getUniqueKey() {
        return "???";
    }

    public boolean isAddOp() {
        return false;
    }

    public int getOperation() {
        return operation;
    }

    public boolean isDuraAdd() {
        return (getOperation() == ProtocolGlobals.G_NEW_INTEREST);
    }

    public boolean isDiscard() {
        return discard;
    }

    public void setDiscard(boolean b) {
        discard = b;
    }

    public String getUUID() { 
        return (String)gp.getProp(UUID_PROPERTY);
    }

    public int getPacketType() { 
        return gp.getType();
    }

    public void transferFlag(ChangeRecordInfo cri) {
    }

    public String toString() {
        return getUniqueKey() + ", isAddOp() = " + isAddOp();
    }


    public static synchronized void syncChangeRecord(
                  ChangeRecordCallback cb, MessageBusCallback mbcb,
                  RaptorProtocol proto, boolean fromStart) 
                  throws BrokerException {

        Long seq = null;
        String resetUUID = null;
        if (fromStart) {
            seq = retrieveLastSeq();
            resetUUID = retrieveLastResetUUID();
        } else {
            if (cb.getLastSyncedChangeRecord() != null) {
               seq = cb.getLastSyncedChangeRecord().getSeq();
               resetUUID = cb.getLastSyncedChangeRecord().getResetUUID();
            } 
        }
        
        List<ChangeRecordInfo> records = null;
        try {
            records = Globals.getStore().
                          getShareConfigChangeStore().getChangeRecordsSince(
                                        seq, resetUUID, fromStart/*canReset*/);
        } catch (BrokerException e) {
            if (e.getStatusCode() == Status.PRECONDITION_FAILED) {
                Globals.getLogger().logStack(Logger.ERROR, e.getMessage(), e);
                Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(),
                    e.getMessage(), BrokerEvent.Type.RESTART, null, false, true, false);
            }
            throw e;
        }

        processChangeRecords(records, cb, mbcb, proto, fromStart);

    }

    private static void storeLastSeq(Long seq) {
        try {

            Globals.getStore().updateProperty(
                ClusterGlobals.STORE_PROPERTY_LASTSEQ, seq, true);
        } catch (Exception e) {
            Globals.getLogger().logStack(Logger.WARNING, 
                Globals.getBrokerResources().getKString(
                BrokerResources.W_UNABLE_STORE_LAST_SEQ_FOR_SHARECC,
                String.valueOf(seq), e.getMessage()), e); 
        }
    }

    private static void storeLastResetUUID(String uuid) {
        try {

            Globals.getStore().updateProperty(
                ClusterGlobals.STORE_PROPERTY_LAST_RESETUUID, uuid, true);
        } catch (Exception e) {
            Globals.getLogger().logStack(Logger.WARNING, 
                Globals.getBrokerResources().getKString(
                BrokerResources.W_UNABLE_STORE_LAST_RESET_UUID_FOR_SHARECC,
                uuid, e.getMessage()), e); 
        }
    
    }
        
    private static Long retrieveLastSeq() {
        Long seq = null;
        try {
            seq = (Long)Globals.getStore().getProperty(
                      ClusterGlobals.STORE_PROPERTY_LASTSEQ);
        } catch (Exception e) {
            Globals.getLogger().log(Globals.getLogger().WARNING, 
            "Unable to retrieve property "+ClusterGlobals.STORE_PROPERTY_LASTSEQ);
        }
        return seq;
    }

    private static String retrieveLastResetUUID() {
        String uuid = null;
        try {
            uuid = (String)Globals.getStore().getProperty(
                      ClusterGlobals.STORE_PROPERTY_LAST_RESETUUID);
        } catch (Exception e) {
            Globals.getLogger().log(Globals.getLogger().WARNING, 
            "Unable to retrieve property "+ClusterGlobals.STORE_PROPERTY_LAST_RESETUUID);
        }
        return uuid;
    }

    public static synchronized void recordUpdateDestination(
                               Destination d, ChangeRecordCallback cb) 
                               throws BrokerException {

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
        GPacket gp = cdi.getGPacket(ProtocolGlobals.G_UPDATE_DESTINATION, true);
        ChangeRecordInfo cri = storeChangeRecord(gp, cb);
        d.setCurrentChangeRecordInfo(ProtocolGlobals.G_UPDATE_DESTINATION, cri);
    }

    public static void recordRemoveDestination(
                  Destination d, ChangeRecordCallback cb) 
                  throws BrokerException {

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
        GPacket gp = cdi.getGPacket(ProtocolGlobals.G_REM_DESTINATION, true);
        ChangeRecordInfo cri = storeChangeRecord(gp, cb);
        d.setCurrentChangeRecordInfo(ProtocolGlobals.G_UPDATE_DESTINATION, cri);
    }

    public static void recordCreateSubscription(
                  Subscription sub, ChangeRecordCallback cb)
                  throws BrokerException {

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
        GPacket gp = csi.getGPacket(ProtocolGlobals.G_NEW_INTEREST, true);
        ChangeRecordInfo cri = storeChangeRecord(gp, cb);
        sub.setCurrentChangeRecordInfo(ProtocolGlobals.G_NEW_INTEREST, cri);
    }

    public static void recordUnsubscribe(
                  Subscription sub, ChangeRecordCallback cb)
                  throws BrokerException {

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub); 
        GPacket gp = csi.getGPacket(ProtocolGlobals.G_REM_DURABLE_INTEREST, true);
        ChangeRecordInfo cri = storeChangeRecord(gp, cb);
        sub.setCurrentChangeRecordInfo(ProtocolGlobals.G_REM_DURABLE_INTEREST, cri);
    }

    public static void storeResetRecordIfNecessary(ChangeRecordCallback cb)
    throws BrokerException {

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_RESET_PERSISTENCE);
        storeChangeRecord(gp, cb);
    }

    private static synchronized ChangeRecordInfo 
    storeChangeRecord(GPacket gp, ChangeRecordCallback cb)
    throws BrokerException {

        String uuid = UUID.randomUUID().toString();
        gp.putProp(ChangeRecord.UUID_PROPERTY, uuid);

        ChangeRecord cr = ChangeRecord.makeChangeRecord(gp);

        ChangeRecordInfo rec = null;
        try {
            rec = new ChangeRecordInfo((Long)null, uuid, cr.getBytes(),
                                   cr.getOperation(), cr.getUniqueKey(), 
                                   System.currentTimeMillis());
            rec.setDuraAdd(cr.isDuraAdd());
            cr.transferFlag(rec);
        } catch (Exception e) {
            throw new BrokerException(e.toString(), e);
        }

        if (gp.getType() == ProtocolGlobals.G_RESET_PERSISTENCE) {
            Globals.getStore().getShareConfigChangeStore().storeResetRecord(rec, true, true);
            return null;
        }

        String resetUUID = null;
        if (cb.getLastSyncedChangeRecord() != null) {
            resetUUID = cb.getLastSyncedChangeRecord().getResetUUID();
        }
        if (resetUUID == null && cb.getLastStoredChangeRecord() != null) {
            resetUUID = cb.getLastStoredChangeRecord().getResetUUID();
        }
        rec.setResetUUID(resetUUID);
        rec = Globals.getStore().getShareConfigChangeStore().storeChangeRecord(rec, true);
        cb.setLastStoredChangeRecord(rec);
        return rec;
    }

    private static void processChangeRecords(
                   List<ChangeRecordInfo> records, 
                   ChangeRecordCallback cb, MessageBusCallback mbcb,
                   RaptorProtocol proto, boolean fromStart)
                   throws BrokerException { 

        Globals.getLogger().log(Logger.INFO,  Globals.getBrokerResources().getKString(
                                BrokerResources.I_CLUSTER_PROCESS_CHANGE_RECORDS,
                                Integer.valueOf(records.size())));

        boolean resetFlag = false;
        if (records.size() > 0 && records.get(0).isSelectAll()) {
            resetFlag = true;
        }

        String resetUUID = null;
        try {
            ArrayList l = new ArrayList();

            for (int i = 0; i < records.size(); i++) {
                ByteArrayInputStream bis = new ByteArrayInputStream(
                                               records.get(i).getRecord());
                DataInputStream dis = new DataInputStream(bis);
                GPacket gp = GPacket.getInstance();
                gp.read(dis);
                if (gp.getType() != records.get(i).getType()) { 
                   throw new BrokerException(Globals.getBrokerResources().getKString(
                       BrokerResources.X_SHARECC_RECORD_TYPE_CORRUPT, 
                       ProtocolGlobals.getPacketTypeString(gp.getType()),
                       records.get(i).toString()));
                }

                if (gp.getType() == ProtocolGlobals.G_RESET_PERSISTENCE) {
                    String uuid = records.get(i).getUUID(); 
                    if (resetUUID != null && !resetUUID.equals(uuid)) {
                        throw new BrokerException(Globals.getBrokerResources().getKString(
                            BrokerResources.X_SHARECC_RESET_RECORD_UUID_CORRUPT, 
                            ProtocolGlobals.getPacketTypeString(ProtocolGlobals.G_RESET_PERSISTENCE),
                            "["+resetUUID+", "+uuid+"]"));
                    } else if (resetUUID == null) {
                        resetUUID = uuid;
                    }
                }
                if (resetFlag) {
                    l.add(gp);
                } else {
                    proto.handleGPacket(mbcb, Globals.getMyAddress(), gp);
                }
            }

            if (resetFlag) {
                proto.applyPersistentStateChanges(Globals.getMyAddress(), l);
            }

            if (records.size() > 0) {
                ChangeRecordInfo rec = records.get(records.size()-1);
                cb.setLastSyncedChangeRecord(rec);
                storeLastSeq(rec.getSeq());
                if (resetFlag && resetUUID != null) {
                    rec.setResetUUID(resetUUID);
                    storeLastResetUUID(resetUUID);
                }
            }

        } catch (Throwable t) {
            Globals.getLogger().logStack(Logger.ERROR, 
                Globals.getBrokerResources().getKString(
                BrokerResources.E_FAIL_PROCESS_SHARECC_RECORDS,
                t.getMessage()), t);
            if (t instanceof BrokerException) throw (BrokerException)t;
            throw new BrokerException(t.getMessage(), t);
        }
    }

    public static ChangeRecordInfo makeResetRecord(boolean withUUID) {

        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,
                "ChangeRecord.makeResetRecord("+withUUID+")");
        }

        ChangeRecordInfo cri = new ChangeRecordInfo();

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_RESET_PERSISTENCE);
        if (withUUID) {
            String uuid = UUID.randomUUID().toString();
            gp.putProp(ChangeRecord.UUID_PROPERTY, uuid);
            cri.setUUID(uuid);
        }
        cri.setTimestamp(System.currentTimeMillis());

        ChangeRecord cr = ChangeRecord.makeChangeRecord(gp);
        cri.setUKey(cr.getUniqueKey());
        try {
            cri.setRecord(cr.getBytes());
        } catch (Exception e) {
            Globals.getLogger().log(Logger.ERROR,
            "Unexpected exception in makeResetRecord("+withUUID+"):"+e.toString());
        }
        return cri;
    }

    /**
     * Backup the change records.
     */
    public static void backupRecords(List<ChangeRecordInfo> records,
                                     String fileName, boolean throwEx)
                                     throws BrokerException {

        Logger logger = Globals.getLogger();
        if (DEBUG) {
            logger.logToAll(Logger.INFO, "ChangeRecord.backup("+fileName+")");
        }

        BrokerResources br = Globals.getBrokerResources();
        int loglevel = (throwEx ? Logger.ERROR:Logger.WARNING);

        FileOutputStream fos = null;
        DataOutputStream dos  = null;
        try {
            // Make sure that the file does not exist.
            File f = new File(fileName);
            if (!f.createNewFile()) {
                String emsg = br.getKString(br.W_MBUS_CANCEL_BACKUP2, fileName);
                logger.logToAll(loglevel, emsg);
                if (throwEx) {
                    throw new BrokerException(emsg);
                }
                return;
            }

            fos = new FileOutputStream(f);
            dos = new DataOutputStream(fos);

            ArrayList<ChangeRecord> recordList = compressRecords(records);

            dos.writeInt(ProtocolGlobals.getCurrentVersion()); 
            dos.writeUTF(ProtocolGlobals.CFGSRV_BACKUP_PROPERTY); // Signature.

            // Write the RESET record here.
            ChangeRecordInfo cri = makeResetRecord(true);
            byte[] rst = cri.getRecord();
            dos.writeInt(rst.length);
            dos.write(rst, 0, rst.length);
            if (DEBUG) {
                logger.logToAll(Logger.INFO, "ChangeRecord.backupRecords backup record "+cri);
            }

            ChangeRecord cr = null;
            for (int i = 0; i < recordList.size(); i++) {
                cr = recordList.get(i);

                if (! cr.isDiscard()) {
                    byte[] rec = cr.getBytes();

                    dos.writeInt(rec.length);
                    dos.write(rec, 0, rec.length);
                    if (DEBUG) {
                        logger.logToAll(Logger.INFO, "ChangeRecord.backupRecords() backup record "+cr);
                    }
                }
            }
            dos.writeInt(0);
            logger.logToAll(Logger.INFO, br.I_CLUSTER_MB_BACKUP_SUCCESS, fileName);

        } catch (Exception e) {
            String emsg = br.getKString(br.W_MBUS_BACKUP_ERROR, e.getMessage());
            logger.logStack((throwEx ? Logger.ERROR:Logger.WARNING), emsg, e);
            if (throwEx) {
                throw new BrokerException(emsg);
            }
        } finally {
            if (dos != null) {
                try {
                dos.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
            if (fos != null) {
                try {
                fos.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
        }

        if (DEBUG) {
            logger.logToAll(Logger.INFO, "ChanageRecord.backup complete");
        }
    }

    public static ArrayList<ChangeRecord> compressRecords(List<ChangeRecordInfo> records)
    throws Exception {

        ArrayList<ChangeRecord> recordList = new ArrayList<ChangeRecord>();
        LinkedHashMap<String, ChangeRecord> recordMap = 
                      new LinkedHashMap<String, ChangeRecord>();

        Logger logger = Globals.getLogger();
        if (DEBUG) {
            logger.logToAll(Logger.INFO,
            "ChangeRecord.compressRecords: compress " + records.size() + " change records");
        }

        for (int i = 0; i < records.size(); i++) {
            byte[] rec = records.get(i).getRecord();
            ChangeRecord cr = makeChangeRecord(rec, records.get(i).getUUID());

            if (DEBUG) {
                logger.logToAll(Logger.INFO, "ChangeRecord.compressRecords: #"+ i+" "+
                                records.get(i)+" "+
                                ProtocolGlobals.getPacketTypeString(cr.getOperation()) +
                                " key=" + cr.getUniqueKey());
            }

            recordList.add(cr);

            // Discard previous record with the same name.
            ChangeRecord prev = (ChangeRecord)recordMap.get(cr.getUniqueKey());
            if (prev != null) {
                prev.setDiscard(true);

                if (DEBUG) {
                    logger.logToAll(Logger.INFO,
                        ">>>>ChangeRecord.compressRecords: discard previous record " +
                        ProtocolGlobals.getPacketTypeString(prev.getOperation()) + 
                        " key=" + cr.getUniqueKey() );
                }
            }

            // Keep only the last add operation.
            if (cr.isAddOp() != true) {
                cr.setDiscard(true);

                if (DEBUG) {
                    logger.logToAll(Logger.INFO,
                    ">>>>ChangeRecord.compressRecords: discard this non-add record ");
                }
            }
            recordMap.put(cr.getUniqueKey(), cr);
	    }

        return recordList;
    }

    /**
     * Preparing for restoring change records.
     */
    public static List<ChangeRecordInfo> prepareRestoreRecords(String fileName)
    throws Exception {

        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();
        if (DEBUG) {
            logger.logToAll(Logger.INFO, "ChangeRecord.prepareRestoreRecords from file " + fileName);
        }

        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            // Make sure that the file does exist.
            File f = new File(fileName);
            if (! f.exists()) {
                String emsg = br.getKString(br.W_MBUS_CANCEL_RESTORE1, fileName);
                logger.log(Logger.WARNING, emsg);
                throw new BrokerException(emsg);
            }

            fis = new FileInputStream(f);
            dis = new DataInputStream(fis);

            int curversion = dis.readInt(); // Version
            String sig = dis.readUTF(); // Signature.

            if (!sig.equals(ProtocolGlobals.CFGSRV_BACKUP_PROPERTY)) {
                String emsg = br.getKString(br.W_MBUS_CANCEL_RESTORE2, fileName);
                logger.logToAll(Logger.WARNING, emsg);
                throw new BrokerException(emsg);
            }

            if (curversion < ProtocolGlobals.VERSION_350 ||
                curversion > ProtocolGlobals.getCurrentVersion()) {
                String emsg = br.getKString(br.W_MBUS_CANCEL_RESTORE3,
                                  String.valueOf(curversion),
                                  String.valueOf(ProtocolGlobals.getCurrentVersion())); 
                logger.logToAll(Logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

            ArrayList<ChangeRecordInfo> records = new ArrayList<ChangeRecordInfo>();

            while (true) {
                int recsize = dis.readInt();
                if (recsize == 0)
                    break;

                byte[] rec = new byte[recsize];
                dis.readFully(rec, 0, recsize);

                ChangeRecordInfo cri = new ChangeRecordInfo();
                cri.setRecord(rec);

                ChangeRecord cr = makeChangeRecord(rec);
                cri.setType(cr.getPacketType());
                cri.setUKey(cr.getUniqueKey());
                cri.setDuraAdd(cr.isDuraAdd());

                if (Globals.useSharedConfigRecord()) {
                    String uuid = cr.getUUID();
                    if (uuid == null) {
                        uuid = UUID.randomUUID().toString();
                    }
                    cri.setUUID(uuid);
                }
                records.add(cri);
                if (DEBUG) {
                    logger.logToAll(Logger.INFO, 
                        "ChangeRecord.prepareRestoreRecords restore record " + cri);
                }
            }

            dis.close();
            fis.close();

            logger.logToAll(Logger.INFO, br.getKString(br.I_CLUSTER_MB_RESTORE_PROCESS_RECORDS,
                                         String.valueOf(records.size()), fileName));
            return records;

        } catch (Exception e) {
            String emsg = br.getKString(br.W_MBUS_RESTORE_ERROR, fileName, e.getMessage());
            logger.logStack(Logger.ERROR, emsg, e);
            throw e;
        } finally {
            if (dis != null) {
                try {
                dis.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
            if (fis != null) {
                try {
                fis.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
        }
    }
}

