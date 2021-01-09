/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * @(#)TransactionLogRecord.java	1.2 06/29/07
 */

package com.sun.messaging.jmq.io.txnlog;

/**
 * Encapsulates a log entry. Used by the TransactionLogWriter to store and retrieve as a transaction record.
 * <p>
 * Format of data for writing (48 byte header) is specified as follows:
 * <p>
 * 1. Record Magic # (int, 0-3)
 * <p>
 * 2. Record Type (int, 4-7)
 * <p>
 * 3. Record Body Size (int, 8-11)
 * <p>
 * 4. Timestamp (long, 12-19)
 * <p>
 * 5. Record Sequence Number (long, 20-28)
 * <p>
 * 6. Check Point Sequence (long, 29-36)
 * <p>
 * 7. Record Body Check Sum (long, 37-44)
 * <p>
 * 8. Record Header Reserve (int, 45-48)
 * <p>
 * 9. Record body data (byte[], size defined in #3 above)
 *
 * <p>
 *
 * @see TransactionLogWriter
 */

public interface TransactionLogRecord {

    /**
     * Get the timestamp of the log record. This value is set by the TransactionLogWriter when writing the entry to the log
     * file.
     *
     * @see #setTimestamp
     * @return the timestamp that this record is created.
     */
    long getTimestamp();

    /**
     * Set the timestamp for this log record. This value is set by the TransactionLogWriter when writing the entry to the
     * log file.
     *
     * @see #getTimestamp
     */
    void setTimestamp(long timestamp);

    /**
     * Get the sequence number of the log record.
     *
     * @return the sequence number of this log record.
     */
    long getSequence();

    /**
     * Set the sequence number for this log record. This value is set by the TransactionLogWriter when writing the entry to
     * the log file.
     *
     * @param sequenceNumber the number assigned to this log entry.
     */
    void setSequence(long sequenceNumber);

    /**
     * Get the check point sequence number. The value is assigned by the TransactionLogWriter right before
     * TransactionLogWriter.write() returns.
     * <p>
     * All records added after a check point contains the same check point sequence number until a new checkpoint is called.
     * <p>
     *
     * @see TransactionLogWriter#write
     *
     * @return the assigned check point sequence.
     *
     */
    long getCheckPointSequence();

    /**
     * Set the specified check point sequence to the log entry.
     * <p>
     * Each log record is assigned the same check point sequence number until a new checkpoint is called.
     * <p>
     *
     * @see TransactionLogWriter#write
     */
    void setCheckPointSequence(long cpSequence);

    /**
     * Get the record type of the log entry.
     *
     * @see TransactionLogType
     * @return the type of the log record.
     */
    int getType();

    /**
     * Set the entry type of this log record.
     *
     * @param type the transaction type of this log entry.
     */
    void setType(int type);

    /**
     * Set the log record body bytes to this log record.
     */
    void setBody(byte[] body);

    /**
     * Get the log record body bytes from this log entry.
     *
     * @return the body data of this entry.
     */
    byte[] getBody();

    /**
     * Get the associated exception for this log entry.
     *
     * @return the body data of this entry.
     */
    Exception getException();

    /**
     * Set the associated exception for this log entry.
     *
     * @param exception the exception for this log entry.
     */
    void setException(Exception exception);

    /**
     * Get the written flag for this log entry.
     *
     * @return true if this record has been written to the log.
     */
    boolean isWritten();

    /**
     * Set the written flag for this log entry.
     *
     * @param flag the written flag for this log entry.
     */
    void setWritten(boolean flag);

}
