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
 * @(#)TransactionLogWriter.java	1.2 06/29/07
 */ 

package com.sun.messaging.jmq.io.txnlog;

import java.io.IOException;
import java.util.Iterator;

/**
 * Used to log events on the persistent file store for replay 
 * after a crash. This class limits the need to sync() the filesystem.
 * <p>
 * When a broker starts-up, an instance of this class is instantiated
 * (FileTransationLogWriter).  The broker must set a 
 * <code>CheckPointListener</code> before it can write transaction 
 * log records to the log file.
 * <p>
 * When a broker starts-up, it MUST check if it was shutdown gracefully.  
 * It does this by calling the playBackRequired() method.  If it returns true, 
 * the broker MUST call the iterator() method to play back all the records
 * from the last check point.
 *<p>
 * After play back, broker MUST call the reset() method to clear the log
 * state before it can write any new log entry to the transaction log.
 * 
 * @see com.sun.messaging.jmq.io.txnlog.file.FileTransactionLogWriter
 * @see CheckPointListener
 * @see TransactionLogRecord
 * 
 */
public interface TransactionLogWriter {

    /**
     * sets the point where a check point automatically
     * occurs.  When reached the size of the specified position,
     * the <code>CheckPointListener</code>will be called.
     * @see CheckPointListener
     * @see #setCheckPointListener(CheckPointListener)
     */
    public void setCheckpointSize(long bytes);
    
    /**
     * Set the check point offset.
     * <p>
     * When the (maxSize-offset) is reached, a checkpoint
     * is triggerred and the log file is rewinded.
     * <p>
     * The default value is set to 500k.
     *
     * @see #setMaximumSize
     * @see #setCheckpointSize
     */
    public void setCheckPointOffset (long offset);


    /**
     * sets the maximum size of the transaction log. When the 
     * (maximum-offset) is reached, a checkpoint is triggerred 
     * and the log file is rewinded.
     * <p>
     * The default offset is 500k.
     * <p>
     * If the log file exists, setting a size larger than the 
     * current size may affect the transaction performance
     * until a checkpoint is triggerred.
     * 
     * @see #setCheckpointSize
     * @see #setCheckPointOffset
     */
    public void setMaximumSize(long bytes);

    /**
     * Registeres a checkpoint listener. The listener is
     * triggered when the TransactionLogWriter reached its
     * check point location.  
     *
     * @see CheckPointListener
     * @see #checkpoint();
     */
    public void setCheckPointListener(CheckPointListener cb);

    /**
     * Write a transaction log record to the log file.  Each transaction is logged
     * and synced to the disk.
     * 
     * @param entry theTransaction Log Record to be written and synced to the file. 
     * @throws IOException if encountered any I/O errors when writing to the disk.
     */
    public void write(TransactionLogRecord entry) throws IOException;

    /**
     * Indicate that broker message store is synced with the transaction log.
     *
     * @return the last entry written and synced to the txn log when check point is called.
     * @throws IOException if encountered any I/O errors when performing check point.
     */
    public  TransactionLogRecord checkpoint() throws IOException;

    /**
     * iterates all log entries from the last checkpoint.
     * <p>
     * When a broker starts-up, it MUST check if it was shutdown gracefully.
     * It does this by calling the playBackRequired() method.  If it returns true,
     * the broker MUST call the iterator() method to play back all the records
     * from the last check point.
     *<p>
     * After play back, broker MUST call the reset() method to clear the log
     * state before it can write any new log entry to the transaction log.
     * <p>
     * @see #playBackRequired()
     * @see #reset
     * @return an iterator of log entries from the last check point.
     * @throws IOException if encountered any I/O errors when reading the log entries.
     */
    public Iterator iterator() throws IOException;
    
    /**
     * Reset the transaction log file.
     * <p>
     * When a broker starts-up, it MUST check if it was shutdown gracefully.
     * It does this by calling the playBackRequired() method.  If it returns true,
     * the broker MUST call the iterator() method to play back all the records
     * from the last check point.
     *<p>
     * After play back, broker MUST call the reset() method to clear the log
     * state before it can write any new log entry to the transaction log.
     *
     * @see #playBackRequired()
     * @see #iterator()
     * @throws IOException if encountered any I/O errors.
     */
    public void reset() throws IOException;
    
    /**
     * Get the last entry of the transaction log.
     * <p>
     * This API only works when the log writer is in 
     * writing/operational mode.  When broker starts up, 
     * it MUST use the API <code>playBackRequired</code>
     * to check if a playback is required.
     * <p>
     * Broker MUST NOT use this API to get the last entry
     * at the starts up time.
     * 
     * @see #playBackRequired
     * @see #iterator   
     */
    public TransactionLogRecord getLastEntry();
    
    /**
     * Get a new instance of TransactionLogRecord object.
     * 
     * @return a new instance of TransactionLogRecord object.  This can be used to set 
     * values of a transaction and write to the log file.
     */
    public TransactionLogRecord newTransactionLogRecord();
    
    /**
     * Close the transaction log file. 
     * If normal=true, this function is the same as close().
     * Otherwise file is closed without being marked as synced.
     * 
     * @see #checkpoint
     *  @throws IOException if encountered any I/O errors.
     */
    public void close(boolean normal) throws IOException;
    
    /**
     * Close the transaction log file.  For a normal shutdown, broker
     * should sync its message store and then call this method.
     * <p>
     * The transaction log file is marked as synced (check point) 
     * when this method is returned.
     * 
     * @see #checkpoint
     *  @throws IOException if encountered any I/O errors.
     */
    public void close() throws IOException;
    
    /**
     * Check if playback is required.
     * <p>
     * When a broker starts-up, it MUST check if it was shutdown gracefully.
     * It does this by calling the playBackRequired() method.  If it returns true,
     * the broker MUST call the iterator() method to play back all the records
     * from the last check point.
     *<p>
     * After play back, broker MUST call the reset() method to clear the log
     * state before it can write any new log entry to the transaction log.
     *
     * @see #reset()
     * @see #iterator()
     * @throws IOException if encountered any I/O errors.
     */
    public boolean playBackRequired();
}

