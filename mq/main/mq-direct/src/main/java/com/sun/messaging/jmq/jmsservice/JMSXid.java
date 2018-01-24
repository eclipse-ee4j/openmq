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
 * @(#)JMSXid.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import javax.transaction.xa.Xid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  MQ implementation of javax.transaction.xa.Xid
 */
public class JMSXid {

    /** Counter used to generate component value for a unique Xid */
    static AtomicInteger sequence = new AtomicInteger(0);  
 
    /////////////////////////////////////////////////////////////////////////
    // Data                                                                //
    /////////////////////////////////////////////////////////////////////////
    protected int formatId;           // Format identifier (-1) means null
    protected byte branchQualifier[];
    protected byte globalTxnId[];    
    protected int gtLength;
    protected int bqLength;

    /////////////////////////////////////////////////////////////////////////
    // Constants                                                           //
    /////////////////////////////////////////////////////////////////////////
    /**
     *  The maximum size of the global transaction identifier.
     */
    static public  final int MAXGTXNSIZE = 64;

    /**
     *  The maximum size of the branch qualifier.
     */
    static public  final int MAXBQUALSIZE = 64;

    /**
     *  Null Xid specified by X/Open Spec. as identifier for NULL Xid.
     * `Not mentioned in JTA.
     */
    static public  final int NULL_XID = -1;

    /**
     * Standard Xid format. Will be used by JTA
     */
    static public  final int OSICCR_XID = 0;

    /** Construct a new JMSXid based on a static sequence */
    public JMSXid(boolean sequenced) {
	branchQualifier = new byte[MAXBQUALSIZE];
	globalTxnId = new byte[MAXGTXNSIZE];
        formatId = NULL_XID;
	bqLength = 0;
	gtLength = 0;
        
        String localHost;
        String globalString;
        String branchString;

        try {
            localHost = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            localHost = "localHost";
        }
        globalString = String.valueOf(System.currentTimeMillis()) +
                String.valueOf(sequence.getAndAdd(1));
        branchString = System.getProperty("user.name") + localHost;

        try {
            setGlobalTransactionId(globalString.getBytes("ASCII"));
            setBranchQualifier(branchString.getBytes("ASCII"));
            setFormatId(0);
        } catch (Exception e) {
            System.out.println(
                    "Exception:init:GlobalTransactionId/BranchQualifier=" + e);
        }
    }

    /**
     *  Construct a new null XidImpl
     *
     *  After construction the data within the XidImpl should be initialized.
     */
    public JMSXid() {
	branchQualifier = new byte[MAXBQUALSIZE];
	globalTxnId = new byte[MAXGTXNSIZE];
        formatId = NULL_XID;
	bqLength = 0;
	gtLength = 0;
    }

    /**
     *  Construct a JMSXid using another Xid as the source. This makes no 
     *  assumptions about the actual Xid implementation.
     *  As such it only uses the Xid interface methods.
     *
     *  @param from the Xid to initialize this XID from
     */
    public JMSXid(Xid xid) {
	branchQualifier = new byte[MAXBQUALSIZE];
	globalTxnId = new byte[MAXGTXNSIZE];
        this.copy(xid);
    }

    /**
     *  Initialize this JMSXid using another Xid as the source.
     *  This makes no assumptions about the actual Xid implementation.
     *  As such it only uses the Xid interface methods.
     *
     *  @param  xid The Xid from which to initialize this JMSXid
     *
     */
    public void copy(Xid xid) {
        byte[] tmp;
        if ((xid == null)||(xid.getFormatId()== NULL_XID))  {
	    this.formatId = NULL_XID;
	    this.bqLength = 0;
	    this.gtLength = 0;	    
            return;  
        }
		
        this.formatId = xid.getFormatId();

	tmp = xid.getBranchQualifier();	
	this.bqLength = (tmp.length > MAXBQUALSIZE)? MAXBQUALSIZE : tmp.length;
	System.arraycopy(tmp, 0, branchQualifier, 0, this.bqLength);

	tmp = xid.getGlobalTransactionId();
	this.gtLength = (tmp.length > MAXGTXNSIZE) ? MAXGTXNSIZE : tmp.length;
	System.arraycopy(tmp, 0, globalTxnId, 0, this.gtLength);
    }

    /**
     *  Determine whether or not two Xid's represent the same transaction.
     *  This makes no assumptions about the actual Xid implementation.
     *  As such it only uses the Xid interface methods.
     *
     *  @param  obj The object to be compared with this JMSXid.
     *
     *  @return {@code true} If the supplied object (Xid) represents the same
     *          global transaction as this;
     *          {@code false} otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Xid) {
            return this.equals((Xid)obj);
        }
        return false;
    }

    public boolean equals(Xid xid) {
	// If the the other xid is null or this one is uninitialized than the Xid's
	// are not equal. Since the other Xid may be a different implementation we 
	// can't assume that the formatId has a special value of -1 if not initialized. 
	if ((xid == null) || (this.formatId == NULL_XID)) return false;
	            
        return ((this.formatId == xid.getFormatId()) &&
                this.isEqualGlobalTxnId(xid.getGlobalTransactionId()) &&
                this.isEqualBranchQualifier(xid.getBranchQualifier()))
                ? true 
                : false;
    }

    /**
     *  Compute the hash code.  It is necessary to override the Object 
     *  hashcode() which uses addresses to hash. Xid's are more like Strings
     *  in that we are interested in the contents of the Xid, not its object 
     *  identity. This is because two Xid's equal in value but different objects
     *  (i.e. different addresses) would generate different hashcodes. 
     *
     *  @return The computed hashcode
     */
    public int hashCode() {
        int hash = 0;	
	
	// Use the first and last byte of the transaction ID and branch 
	// qualifier to make up a 4 byte hash code. . This creates a decent
	// hash.  
	
	if (this.bqLength >= 2 ) hash += this.branchQualifier[bqLength-1]<<8;
	if (this.bqLength >= 1 ) hash += this.branchQualifier[0];
	
	if (this.gtLength >= 2 ) hash += this.globalTxnId[gtLength-1]<<24;
	if (this.gtLength >= 1 ) hash += this.globalTxnId[0]<<16;

        return hash;
    }

    /**
     *  Return a string representing this JMSXid.
     *
     *  @return The string representation of this JMSXid
     */
    static private final String hextab= "0123456789ABCDEF";

    public String toLongString() {
        StringBuffer      data =  new StringBuffer(200); 
        int               i;
        int               value;

        data.append("{JMSXid:hash(" +
                this.hashCode() +
                ")fmt(" + this.formatId + ")bq(" );

        // Add branch qualifierConvert data string to hex
        for (i = 0; i < this.bqLength; i++) {       	
            value = branchQualifier[i] & 0xff;
            data.append("0x" +
                    hextab.charAt(value/16) + hextab.charAt(value&15));
            if (i != (this.bqLength-1)) data.append(",");
	}
        data.append(")gt(");

        // Add global transaction id
        for (i = 0; i < this.gtLength; i++) {        	
            value = this.globalTxnId[i] & 0xff;
            data.append("0x" + hextab.charAt(value/16) + hextab.charAt(value&15));
            if (i != (this.gtLength-1)) data.append(",");
	}
	data.append(")}");

        return new String(data);
    }

    /**
     *  Return a short string representing this JMSXid.
     *  Used for lookup and database key.
     *
     *  @return the string representation of this JMSXid
     */
    public String toString() {
        StringBuffer      data =  new StringBuffer(256); 
        int               i;
        int               value;
        
	if (this.formatId == NULL_XID) 
	    return "NULL_XID";
	
        // Add branch qualifier. Convert data string to hex
        for (i = 0; i < this.bqLength; i++) {       	
            value = this.branchQualifier[i] & 0xff;
            data.append(hextab.charAt(value/16));
            data.append(hextab.charAt(value&15));
	}

        // Add global transaction id
        for (i = 0; i < this.gtLength; i++) {        	
            value = this.globalTxnId[i] & 0xff;
            data.append(hextab.charAt(value/16));
            data.append(hextab.charAt(value&15));
 	}
        return new String(data);
    }

    /**
     * Returns the branch qualifier for this XID.
     *
     * @return the branch qualifier
     */
    public byte[] getBranchQualifier() {
        byte[] bq = new byte[this.bqLength];
        System.arraycopy(this.branchQualifier, 0, bq, 0, this.bqLength);
        return bq;
    }

    /**
     *  Set the branch qualifier for this JMSXid.
     *
     *  @param  bq  Byte array containing the branch qualifier to be set. If
     *  the size of the array exceeds MAXBQUALSIZE, only the first
     *  MAXBQUALSIZE elements of bq will be used.
     */
    public void setBranchQualifier(byte[] bq) {
        this.bqLength = (bq.length > MAXBQUALSIZE) ? MAXBQUALSIZE : bq.length;
        System.arraycopy(bq, 0, this.branchQualifier, 0, this.bqLength);
    }

    /**
     *  Obtain the format identifier part of the JMSXid.
     *
     *  @return Format identifier.
     */
    public int getFormatId() {
        return this.formatId;
    }

    /**
     *  Set the format identifier part of the JMSXid.
     *
     *  @param formatId The Format Identifier.
     */
    public void setFormatId(int formatId) {
        this.formatId = formatId;
        return;
    }

    /**
     *  Compare the input parameter with the branch qualifier for equality.
     *
     *  @return {@code true} If equal
     */
    public boolean isEqualBranchQualifier(byte[] bq) {

        if (bq == null) return ((this.bqLength == 0) ? true : false);

        if ( bq.length != this.bqLength)  return false;

        for (int i = 0; i < this.bqLength; i++) {
            if (bq[i] != this.branchQualifier[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     *  Compare the input parameter with the global transaction Id for equality.
     *
     *  @return {@code true} If equal
     */
    public boolean isEqualGlobalTxnId(byte[] gt) {

        if (gt == null) return ((this.gtLength == 0) ? true : false);

        if (gt.length != this.gtLength) return false;

        for (int i = 0; i < gtLength; i++) {
            if (gt[i] != globalTxnId[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     *  Return the global transaction identifier for this JMSXid.
     *
     *  @return The global transaction identifier
     */
    public byte[] getGlobalTransactionId() {
	byte[] gt = new byte[this.gtLength];
        System.arraycopy(this.globalTxnId, 0, gt, 0, this.gtLength);
        return gt;
    }

    /**
     *  Set the branch qualifier for this JMSXid.
     *
     *  @param  bq  The Byte array containing the branch qualifier to be set.
     *          If the size of the array exceeds MAXBQUALSIZE, only the first
     *          MAXBQUALSIZE elements of bq will be used.
     */
    public void setGlobalTransactionId(byte[] gt) {
        this.gtLength = (gt.length > MAXGTXNSIZE) ? MAXGTXNSIZE : gt.length;
        System.arraycopy(gt, 0, this.globalTxnId, 0, this.gtLength);
    }
}
