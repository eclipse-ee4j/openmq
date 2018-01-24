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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.PHashMapMMF;
import com.sun.messaging.jmq.io.disk.ObjectInputStreamCallback;
import com.sun.messaging.jmq.io.disk.VRFileWarning;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;
import com.sun.messaging.bridge.service.jms.tx.BranchXid;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.api.KeyNotFoundException;
import com.sun.messaging.bridge.api.UpdateOpaqueDataCallback;
import com.sun.messaging.bridge.api.DupKeyException;
import com.sun.messaging.bridge.service.jms.JMSBridge;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;


/**
 *
 * @author amyk
 */

public class FileTxLogImpl extends TxLog implements JMSBridgeStore, ObjectInputStreamCallback {

    private static final String _type = TxLog.FILETYPE;

    private static final String FILENAME_BASE = "txlog";

    private static final String FILENAME_JMSBRIDGES = "jmsbridges.list";

    private static final boolean DEFAULT_TXLOG_USE_MMAPPED_FILE = true;
    private static final long DEFAULT_TXLOG_SIZE = 1024*1000; //bytes 

    private long _logsize = DEFAULT_TXLOG_SIZE;
    private String _txlogdir = null;
    private String _txlogdirParent = null;
    private String _logsuffix = null;
    private File _backFile = null;

    private boolean _useMmappedFile = true;
    private PHashMap _gxidMap = null;
    private boolean _sync = false;

    private static final int DEFAULT_CLIENTDATA_SIZE = 16; 
    private int _clientDataSize = DEFAULT_CLIENTDATA_SIZE;

    private static JMSBridgeResources _jbr = JMSBridge.getJMSBridgeResources();

    public FileTxLogImpl() {}

    public String getType() {
        return _type;
    }

    // The setter methods must be called before init

    /**
	 * needed if use memory mapped file
     */
    public void setMaxBranches(int v) throws Exception {
        if (v < 0) throw new IllegalArgumentException("Invalid maximum branches "+v); 
        _clientDataSize = v;
    }

    public void setUseMmap(boolean b) {
        _useMmappedFile = b;
    }

    public void setSync(boolean b) {
        _sync = b;
    }

    public void setTxlogSuffix(String suffix) {
        _logsuffix = suffix;
    }

    public void setTxlogSize(String size) throws Exception {
        SizeString ss = new SizeString(size);
        if (ss.getBytes() <= 0) {
            throw new IllegalArgumentException("Illegal txlog file size "+size);
        }
        _logsize = ss.getBytes();
    }

    public void setTxlogDir(String d) throws Exception {
        if (d == null || d.trim().length() == 0) {
            throw new IllegalArgumentException("Invalid txlog directory "+d);
        }
        String dir = d.trim();
        File f = new File(dir);
        if (!f.exists()) {
            throw new IllegalArgumentException("txlog directory "+dir+" not exist");
        }
        if (!f.isDirectory()) {
            throw new IllegalArgumentException(""+dir+" not a directory for txnlog");
        }
        if (!f.canWrite()) {
            throw new IllegalArgumentException("txlog directory "+dir+" not writable");
        }
        _txlogdir = dir;
    }

    public void setTxlogDirParent(String d) throws Exception {
        if (d == null || d.trim().length() == 0) {
            throw new IllegalArgumentException("Invalid txlogDirParent directory "+d);
        }
        String dir = d.trim();
        File f = new File(dir);
        if (!f.exists()) {
            throw new IllegalArgumentException("txlogDirParent directory "+dir+" not exist");
        }
        if (!f.isDirectory()) {
            throw new IllegalArgumentException(""+dir+" not a directory for txnlogDirParent");
        }
        if (!f.canWrite()) {
            throw new IllegalArgumentException("txlogDirParent directory "+dir+" not writable");
        }
        _txlogdirParent = dir;
    }

    public void logGlobalDecision(LogRecord lr) throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: log global decision  "+lr);
        }
        String key = lr.getGlobalXid().toString();

        super.checkClosedAndSetInProgress();
        try {

        Object oldlr = _gxidMap.putIfAbsent(key, lr);

        if (oldlr != null) {
            String emsg = key+" already exist in txlog: "+oldlr;
            _logger.log(Level.SEVERE, emsg);
            throw new IllegalStateException(emsg);
        }
        if (_sync) _gxidMap.force(key);

        } finally {
        super.setInProgress(false);
        }
    }

    public LogRecord getLogRecord(GlobalXid gxid) throws Exception { 
        return getLogRecord(gxid.toString());
    }
    public LogRecord getLogRecord(String gxid) throws Exception { 
        String key = gxid;
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: get txlog log record: "+key);
        }

        super.checkClosedAndSetInProgress();
        try {

        LogRecord lr = (LogRecord)_gxidMap.get(key);
        return lr;
        } finally {
        super.setInProgress(false);
        }
    }

    /**
     * branch heuristic decision should be already set in lr
     */
    public void logHeuristicBranch(BranchXid bxid, LogRecord lr)
                                                throws Exception { 
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: log branch heuristic decision  "+lr);
        }
        String key = lr.getGlobalXid().toString();

        super.checkClosedAndSetInProgress();
        try {

        LogRecord oldlr = (LogRecord)_gxidMap.get(key);
        if (oldlr == null) {
            logGlobalDecision(lr);
            if (_sync) _gxidMap.force(key);
            return;
        }
        if (oldlr.getBranchDecision(bxid) == lr.getBranchDecision(bxid)) {
            return;
        }
        oldlr.setBranchDecision(bxid, lr.getBranchDecision(bxid));
        if (_useMmappedFile) {
            if (oldlr.getBranchCount() > _clientDataSize) {
                throw new IllegalArgumentException(
                "The number of branches exceeded maximum "+_clientDataSize+" allowed");
            } 
            byte[] oldcd = ((PHashMapMMF)_gxidMap).getClientData(key);
            oldlr.updateClientDataFromBranch(oldcd, bxid);
            ((PHashMapMMF)_gxidMap).putClientData(key, oldcd);
        } else {
            _gxidMap.put(key, oldlr);
        }
        if (_sync) _gxidMap.force(key);

        } finally {
        super.setInProgress(false);
        }
    }
    
    public void reap(String gxid) throws Exception {
        String key = gxid; 

        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: Remove "+key);
        }

        super.checkClosedAndSetInProgress();
        try {

        Object lr = _gxidMap.remove(key);
        if (lr == null) {
            String emsg = gxid+" not found in txlog";
            _logger.log(Level.SEVERE, emsg); 
            throw new IllegalArgumentException(emsg);
        }
        if (_sync) _gxidMap.force(key);

        } finally {
        super.setInProgress(false);
        }
    }

    public List<LogRecord> getAllLogRecords() throws Exception { 
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: get all log records");
        }

        super.checkClosedAndSetInProgress();
        try {

        ArrayList<LogRecord> list = new ArrayList<LogRecord>(_gxidMap.size());

        Iterator<Map.Entry> itr = _gxidMap.entrySet().iterator();
        Map.Entry entry = null;
        LogRecord lr = null;
        while (itr.hasNext()) {
            entry = itr.next();
            lr = (LogRecord)entry.getValue();
            list.add(lr);
        }
        return list;

        } finally {
        super.setInProgress(false);
        }
    }

    public List<String> getAllLogRecordKeys() throws Exception {
        if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "txlog: get all log record keys");
        }

        super.checkClosedAndSetInProgress();
        try {

        ArrayList<String> list = new ArrayList<String>(_gxidMap.keySet());
        return list;

        } finally {
        super.setInProgress(false);
        }
    }

    public ObjectInputStream getObjectInputStream(ByteArrayInputStream bis) 
    throws IOException {
        return new FilteringObjectInputStream(bis);
    }

    public void init(Properties props, boolean reset) throws Exception {
        if (_logger == null) {
            throw new IllegalStateException("No logger set"); 
        }

        super.init(props, reset);

        if (props != null) {
            Enumeration en = props.propertyNames();
            String name = null;
            String value = null;
            while (en.hasMoreElements()) {
                name = (String)en.nextElement();
                value = props.getProperty(name);
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_FILETXNLOG_SET_PROP, name+"="+value, _tmname));
                setProperty(name, value);
            }
        }
 
        if (_txlogdir == null) {
            throw new IllegalStateException("Property txlogDir not set"); 
        }

        String fname = (_logsuffix == null ? FILENAME_BASE : (FILENAME_BASE+"."+_logsuffix));

        if (reset) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_FILETXNLOG_INIT_WITH_RESET, fname));
            if (_txlogdirParent != null) {
                String fn = _txlogdirParent+File.separator+FILENAME_JMSBRIDGES;
                File f = new File(fn);
                if (f.exists()) {
                    if (!f.delete()) {
                        _logger.log(Level.WARNING, "Failed to delete file " + fn + " on reset");
                        File dfn = new File(_txlogdirParent, FILENAME_JMSBRIDGES + ".deleted");
                        if (!f.renameTo(dfn)) {
                            _logger.log(Level.WARNING, "Failed rename file "
                                 + fn + " to " + dfn + " after deletion failure");
                        }
                    }
                }
            }
        } else {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_FILETXNLOG_INIT, fname));
        }
        _backFile = new File(_txlogdir, fname);

        if (_useMmappedFile) {
            _gxidMap = new PHashMapMMF(_backFile, _logsize, 1024, false, reset, false, false);
            ((PHashMapMMF)_gxidMap).intClientData(_clientDataSize);
        } else {
            _gxidMap = new PHashMap(_backFile, _logsize, 1024, false, reset, false, false);
        }
        try {
            _gxidMap.load(this); 
            if (_clientDataSize > 0) {
                loadClientData();
            }
        } catch (PHashMapLoadException pe) {
            _logger.log(Level.WARNING, "Exception in loading txlog "+_backFile, pe);
            throw pe;

        }

        VRFileWarning w = _gxidMap.getWarning();
        if (w != null) {
            _logger.log(Level.WARNING, "Warning in loading txlog, possible loss of record", w);
        }

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_FILETXNLOG_LOADED, _backFile, String.valueOf(_gxidMap.size())));

    }

    private void setProperty(String key, String value)
                                    throws Exception {
        if (key.equals("txlogDir")) {
            setTxlogDir(value);
            return;
        }
        if (key.equals("txlogSuffix")) {
            setTxlogSuffix(value);
            return;
        }
        if (key.equals("txlogSize")) {
            setTxlogSize(value);
            return;
        }
        if (key.equals("txlogSync")) {
            setSync(Boolean.valueOf(value).booleanValue());
            return;
        }
        if (key.equals("txlogMmap")) {
            setUseMmap(Boolean.valueOf(value).booleanValue());
            return;
        }
        if (key.equals("txlogMaxBranches")) {
            setMaxBranches(Integer.parseInt(value));
            return;
        }
        if (key.equals("txlogDirParent")) {
            setTxlogDirParent(value);
            return;
        }
    }

    private void loadClientData() throws PHashMapLoadException {
        
        if (!_useMmappedFile) return;

        PHashMapLoadException loadException = null;

        Iterator itr = _gxidMap.entrySet().iterator();
        while (itr.hasNext()) {
            Throwable ex = null;
            Map.Entry entry = (Map.Entry)itr.next();
            Object key = entry.getKey();
            LogRecord value = (LogRecord)entry.getValue();
            int cnt = value.getBranchCount();
            if (cnt <= 0) continue; 
            byte[] cdata = null;
            try {
                 cdata = ((PHashMapMMF)_gxidMap).getClientData(key);
                 if (cdata != null && cdata.length > 0) {
                     value.updateBranchFromClientData(cdata);
                 }
            } catch (Throwable e) {
                ex = e;
            }

            if (ex != null) {
                PHashMapLoadException le = new PHashMapLoadException(
                    "Failed to load client data [cdata=" + Arrays.toString(cdata) + "]");
                le.setKey(key);
                le.setValue(value);
                le.setNextException(loadException);
                le.initCause(ex);
                loadException = le;
            }
        }

        if (loadException != null) {
            throw loadException;
        }
    }

    public void close() throws Exception {
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_FILETXNLOG_CLOSE, _backFile, String.valueOf(_gxidMap.size())));

        super.setClosedAndWait();
        super.close();
        if (_gxidMap != null) _gxidMap.close();
    }


    /***************************************************************
     * Methods for JMSBridgeStore Interface 
     *
     * to be used by imqdbmgr backup/restore JDBC JMSBridge store
     ***************************************************************/

    /**
     * Store a log record
     *
     * @param xid the global XID 
     * @param logRecord the log record data for the xid
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception DupKeyException if already exist 
     *            else Exception on error
     */
    public void storeTMLogRecord(String xid, byte[] logRecord,
                                 String name, boolean sync,
                                 java.util.logging.Logger logger_)
                                 throws DupKeyException, Exception {

         ObjectInputStream ois =  new FilteringObjectInputStream(
                                  new ByteArrayInputStream((byte[])logRecord));
         LogRecord lr = (LogRecord)ois.readObject();

         logGlobalDecision(lr); 
    }

    /**
     * Update a log record
     *
     * @param xid the global XID 
     * @param logRecord the new log record data for the xid
     * @param name the jmsbridge name
     * @param callback to obtain updated data if not null
     * @param addIfNotExist
     * @param sync - not used
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void updateTMLogRecord(String xid, byte[] logRecord, String name,
                                  UpdateOpaqueDataCallback callback,
                                  boolean addIfNotExist,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception {
        throw new UnsupportedOperationException("updateTMLogRecord");
    }

    /**
     * Remove a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void removeTMLogRecord(String xid, String name,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception {
        throw new UnsupportedOperationException("removeTMLogRecord");
    }
    /**
     * Get a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @return null if not found
     * @exception Exception if error
     */
    public byte[] getTMLogRecord(String xid, String name,
                                 java.util.logging.Logger logger_)
                                 throws Exception {
        LogRecord lr = getLogRecord(xid);
        if (lr != null) return lr.toBytes();
        return null;
    }


    /**
     * Get last update time of a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordUpdatedTime(String xid,  String name,
                                          java.util.logging.Logger logger_)
                                          throws KeyNotFoundException, Exception {
        throw new UnsupportedOperationException("getTMLogRecordUpdatedTime");
    }

    /**
     * Get a log record creation time
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordCreatedTime(String xid, String name,
                                          java.util.logging.Logger logger_)
                                          throws Exception {
        if (xid == null) throw new IllegalArgumentException("null xid");
        throw new UnsupportedOperationException("getTMLogRecordCreatedTime");
    }

    /**
     * Get all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null 
     * @return a list of log records
     * @exception Exception if error
     */
    public List getTMLogRecordsByName(String name, 
                                      java.util.logging.Logger logger_)
                                      throws Exception {
        throw new UnsupportedOperationException("getTMLogRecordsByName");
    }

    /**
     * Get keys for all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @return a list of keys
     * @exception Exception if error
     */
    public List<String> getTMLogRecordKeysByName(String name,
                                         java.util.logging.Logger logger_)
                                         throws Exception {
        if (!_jmsbridge.equals(name)) {
            throw new IllegalArgumentException(
            "Unexpected jmsbridge name "+name+" expected "+_jmsbridge);
        }
        return getAllLogRecordKeys();
    }


    /********************************************************
     * Methods used only under HA mode by JMS bridge
     ********************************************************/

    /**
     * Add a JMS Bridge 
     *
     * @param name jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @exception DupKeyException if already exist 
     *            else Exception on error
     */
    public void addJMSBridge(String name, boolean sync,
                             java.util.logging.Logger logger_)
                             throws DupKeyException, Exception {
        throw new UnsupportedOperationException("addJMSBridge");
    }

    /**
     * Get JMS bridges owned by this broker 
     *
     * @param name jmsbridge name
     * @param sync - not used
     * @param logger_ can be null 
     * @return a list of names
     * @exception Exception if error
     */
    public List getJMSBridges(java.util.logging.Logger logger_)
                             throws Exception {
        if (_txlogdirParent == null) {
            throw new UnsupportedOperationException("getJMSBridges: txlogDirParent property not available");
        }
        File dir =  new File(_txlogdirParent);
        if (!dir.exists()) {
            throw new IOException("Unexpected error: "+_txlogdirParent+" does not exist !");
        }
        File[] files = dir.listFiles();
        if (files == null) {
            throw new IOException("Can't list files in "+_txlogdirParent);
        }

        if (files.length == 0) return null;

        List bridges = new ArrayList();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                bridges.add(files[i].getName());
            }
        } 
        return bridges;
    }


    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return updated time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeUpdatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception {
        throw new UnsupportedOperationException("addJMSBridge");
    }

    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return created time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeCreatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception {
        throw new UnsupportedOperationException("addJMSBridge");
    }

    public void closeJMSBridgeStore() throws Exception {
        close();
    }
}
