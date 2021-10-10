/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.io.txnlog.file;

import com.sun.messaging.jmq.io.txnlog.*;

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
 * @see TransactionLogRecord
 * @see FileTransactionLogWriter
 */

public class FileTransactionLogRecord implements TransactionLogRecord {

    private long timestamp = 0;

    private long sequence = 0;

    private int logType = -1;

    private byte[] logBody = null;

    private long cpSequence = -1;

    private Exception exception;

    private boolean isWritten;

    /**
     * Create a new entry to write to txn log. Timestamp, CP sequence, and entry sequence are set before write to the log
     * file.
     */
    public FileTransactionLogRecord() {
    }

    /**
     * Construct a FileTransactionLogRecord from an entry read from the log file.
     */
    public FileTransactionLogRecord(long timestamp, int type, long seq) {
        this.timestamp = timestamp;
        this.logType = type;
        this.sequence = seq;
    }

    /**
     * returns the timestamp of the entry/
     */
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long ts) {
        this.timestamp = ts;
    }

    /**
     * returns the sequence (combination of timestamp + sequence should be unique for a system)
     */
    @Override
    public long getSequence() {
        return sequence;
    }

    @Override
    public void setSequence(long seq) {
        this.sequence = seq;
    }

    /**
     * retrieves the type of the entry.
     *
     * @return an integer which matches to a type
     */
    @Override
    public int getType() {
        return logType;
    }

    @Override
    public void setType(int type) {
        this.logType = type;
    }

    /**
     * sets the formatted bytes for writing or sending.
     */
    @Override
    public void setBody(byte[] body) {
        this.logBody = body;
    }

    /**
     * retrieves the formatted bytes for writing or sending.
     */
    @Override
    public byte[] getBody() {
        return logBody;
    }

    /**
     * This is set by FileTransactionLogWriter after written to the txn log file.
     */
    @Override
    public void setCheckPointSequence(long cpseq) {
        this.cpSequence = cpseq;
    }

    /**
     * Get the cp seq number of this txn log record.
     */
    @Override
    public long getCheckPointSequence() {
        return this.cpSequence;
    }

    /**
     * Get the associated exception for this log entry.
     *
     * @return the body data of this entry.
     */
    @Override
    public Exception getException() {
        return exception;
    }

    /**
     * Set the associated exception for this log entry.
     *
     * @param exception the exception for this log entry.
     */
    @Override
    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * Get the written flag for this log entry.
     *
     * @return true if this record has been written to the log.
     */
    @Override
    public boolean isWritten() {
        return isWritten;
    }

    /**
     * Set the written flag for this log entry.
     *
     * @param flag the written flag for this log entry.
     */
    @Override
    public void setWritten(boolean flag) {
        isWritten = flag;
    }

    @Override
    public String toString() {
        return "CPSequence=" + cpSequence + ", Sequence=" + sequence + ", type=" + logType + ", timestamp=" + timestamp + ", body size=" + getBody().length;
    }
}
