/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2021, 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.io.Serial;
import javax.transaction.xa.Xid;

/**
 * The XID class provides an implementation of the X/Open transaction identifier it implements the
 * javax.transaction.xa.Xid interface.
 *
 * Taken from the RI
 *
 * @see javax.transaction.xa.Xid
 */
public class XidImpl implements Xid, java.io.Serializable {

    @Serial
    private static final long serialVersionUID = -8490751945727661234L;
    // -----------------------------------------------------------------------//
    // Data
    // -----------------------------------------------------------------------//
    protected int formatId; // Format identifier (-1) means null
    protected byte branchQualifier[];
    protected byte globalTxnId[];
    protected int gtLength;
    protected int bqLength;

    // -----------------------------------------------------------------------//
    // Constants //
    // -----------------------------------------------------------------------//

    /**
     * The maximum size of the global transaction identifier.
     */
    public static final int MAXGTXNSIZE = 64;

    /**
     * The maximum size of the branch qualifier.
     */
    public static final int MAXBQUALSIZE = 64;

    /**
     * Specified by X/Open Spec. as identifier for NULL Xid. Not mentioned in JTA.
     */
    public static final int NULL_XID = -1;

    /**
     * Standard Xid format. Will be used by JTA
     */
    public static final int OSICCR_XID = 0;

    /**
     * Constructs a new null XidImpl. After construction the data within the XidImpl should be initialized.
     */
    public XidImpl() {
        branchQualifier = new byte[MAXBQUALSIZE];
        globalTxnId = new byte[MAXGTXNSIZE];
        formatId = NULL_XID;
        bqLength = 0;
        gtLength = 0;
    }

    /**
     * Constructs an XidImpl using another XID as the source of data. This makes no assumptions about the actual XID
     * implementation. As such it only uses the XID interface methods.
     *
     * @param xid the Xid to initialize this XID from
     *
     */
    public XidImpl(Xid xid) {
        branchQualifier = new byte[MAXBQUALSIZE];
        globalTxnId = new byte[MAXGTXNSIZE];
        this.copy(xid);
    }

    /**
     * Initialize an XID using another XID as the source of data. This makes no assumptions about the actual XID
     * implementation. As such it only uses the XID interface methods.
     *
     * @param xid the Xid to initialize this XID from
     *
     */
    public void copy(Xid xid) {
        byte[] tmp;
        if ((xid == null) || (xid.getFormatId() == NULL_XID)) {
            formatId = NULL_XID;
            bqLength = 0;
            gtLength = 0;
            return;
        }

        formatId = xid.getFormatId();

        tmp = xid.getBranchQualifier();
        bqLength = (tmp.length > MAXBQUALSIZE) ? MAXBQUALSIZE : tmp.length;
        System.arraycopy(tmp, 0, branchQualifier, 0, bqLength);

        tmp = xid.getGlobalTransactionId();
        gtLength = (tmp.length > MAXGTXNSIZE) ? MAXGTXNSIZE : tmp.length;
        System.arraycopy(tmp, 0, globalTxnId, 0, gtLength);
    }

    /**
     * Determine whether or not two Xid's represent the same transaction. This makes no assumptions about the actual Xid
     * implementation. As such it only uses the Xid interface methods.
     *
     * @param obj the object to be compared with this Xid.
     *
     * @return Returns true of the supplied xid represents the same global transaction as this, otherwise returns false.
     */
    @Override
    public boolean equals(Object obj) {
        return this.isEqualTo((Xid) obj);
    }

    private boolean isEqualTo(Xid xid) {
        // If the the other xid is null or this one is uninitialized than the Xid's
        // are not equal. Since the other Xid may be a different implementation we
        // can't assume that the formatId has a special value of -1 if not initialized.
        if ((xid == null) || (formatId == NULL_XID)) {
            return false;
        }

        return (formatId == xid.getFormatId()) && this.isEqualGlobalTxnId(xid.getGlobalTransactionId())
                && this.isEqualBranchQualifier(xid.getBranchQualifier());
    }

    /**
     * Compute the hash code. It is necessary to override the Object hashcode() which uses addresses to hash. Xid's are more
     * like Strings in that we are interested in the contents of the Xid, not its object identity. This is because two Xid's
     * equal in value but different objects (i.e. different addresses) would generate different hashcodes.
     *
     * @return the computed hashcode
     */
    @Override
    public int hashCode() {
        int hash = 0;

        // Use the first and last byte of the transaction ID and branch
        // qualifier to make up a 4 byte hash code. . This creates a decent
        // hash.

        if (bqLength >= 2) {
            hash += branchQualifier[bqLength - 1] << 8;
        }
        if (bqLength >= 1) {
            hash += branchQualifier[0];
        }

        if (gtLength >= 2) {
            hash += globalTxnId[gtLength - 1] << 24;
        }
        if (gtLength >= 1) {
            hash += globalTxnId[0] << 16;
        }

        return hash;
    }

    /**
     * Return a string representing this XID.
     *
     * @return the string representation of this XID
     */
    private static final String hextab = "0123456789ABCDEF";

    public String toLongString() {
        StringBuilder data = new StringBuilder(200);
        int i;
        int value;

        data.append("{XID:hash(").append(this.hashCode()).append(")fmt(").append(formatId).append(")bq(");

        // Add branch qualifierConvert data string to hex
        for (i = 0; i < bqLength; i++) {
            value = branchQualifier[i] & 0xff;
            data.append("0x").append(hextab.charAt(value / 16)).append(hextab.charAt(value & 15));
            if (i != (bqLength - 1)) {
                data.append(',');
            }
        }
        data.append(")gt(");

        // Add global transaction id
        for (i = 0; i < gtLength; i++) {
            value = globalTxnId[i] & 0xff;
            data.append("0x").append(hextab.charAt(value / 16)).append(hextab.charAt(value & 15));
            if (i != (gtLength - 1)) {
                data.append(',');
            }
        }
        data.append(")}");

        return new String(data);
    }

    /**
     * Return a short string representing this XID. Used for lookup and database key.
     *
     * @return the string representation of this XID
     */

    @Override
    public String toString() {
        StringBuilder data = new StringBuilder(256);
        int i;
        int value;

        if (formatId == NULL_XID) {
            return "NULL_XID";
        }

        // Add branch qualifier. Convert data string to hex
        for (i = 0; i < bqLength; i++) {
            value = branchQualifier[i] & 0xff;
            data.append(hextab.charAt(value / 16));
            data.append(hextab.charAt(value & 15));
        }

        // Add global transaction id
        for (i = 0; i < gtLength; i++) {
            value = globalTxnId[i] & 0xff;
            data.append(hextab.charAt(value / 16));
            data.append(hextab.charAt(value & 15));
        }
        return new String(data);
    }

    /**
     * Returns the branch qualifier for this XID.
     *
     * @return the branch qualifier
     */
    @Override
    public byte[] getBranchQualifier() {
        byte[] bq = new byte[bqLength];
        System.arraycopy(branchQualifier, 0, bq, 0, bqLength);
        return bq;
    }

    /**
     * Set the branch qualifier for this XID.
     *
     * @param bq Byte array containing the branch qualifier to be set. If the size of the array exceeds MAXBQUALSIZE, only
     * the first MAXBQUALSIZE elements of bq will be used.
     */
    public void setBranchQualifier(byte[] bq) {
        bqLength = (bq.length > MAXBQUALSIZE) ? MAXBQUALSIZE : bq.length;
        System.arraycopy(bq, 0, branchQualifier, 0, bqLength);
    }

    /**
     * Obtain the format identifier part of the XID.
     *
     * @return Format identifier.
     */
    @Override
    public int getFormatId() {
        return formatId;
    }

    /**
     * Set the format identifier part of the XID.
     *
     * @param formatId Format identifier.
     */
    public void setFormatId(int formatId) {
        this.formatId = formatId;
        return;
    }

    /**
     * Compares the input parameter with the branch qualifier for equality.
     *
     * @return true if equal
     */
    public boolean isEqualBranchQualifier(byte[] bq) {

        if (bq == null) {
            return bqLength == 0;
        }

        if (bq.length != bqLength) {
            return false;
        }

        for (int i = 0; i < bqLength; i++) {
            if (bq[i] != branchQualifier[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the input parameter with the global transaction Id for equality.
     *
     * @return true if equal
     */
    public boolean isEqualGlobalTxnId(byte[] gt) {

        if (gt == null) {
            return gtLength == 0;
        }

        if (gt.length != gtLength) {
            return false;
        }

        for (int i = 0; i < gtLength; i++) {
            if (gt[i] != globalTxnId[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the global transaction identifier for this XID.
     *
     * @return the global transaction identifier
     */
    @Override
    public byte[] getGlobalTransactionId() {
        byte[] gt = new byte[gtLength];
        System.arraycopy(globalTxnId, 0, gt, 0, gtLength);
        return gt;
    }

    public void setGlobalTransactionId(byte[] gt) {
        gtLength = (gt.length > MAXGTXNSIZE) ? MAXGTXNSIZE : gt.length;
        System.arraycopy(gt, 0, globalTxnId, 0, gtLength);
    }

}
