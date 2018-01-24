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
 * @(#)TransactionAcknowledgement.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.migration;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import java.io.*;

/**
 * Acknowledgement for transactions. 
 *
 * Object format prior to 370 filestore, use for migration purpose only.
 * @see com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement
 */
public class TransactionAcknowledgement implements Serializable
{
    // compatibility w/ 3.0.1
    static final long serialVersionUID = 1518763750089861353L;

    private static Logger logger = Globals.getLogger();

    transient SysMessageID sysid = null;
    ConsumerUID iid = null;

    // sid is the stored UID associated w/ the ack
    // at this point, we really just want the stored uid
    // BUT in the future we may want the original UID
    // Since we kept iid in the past, we still keep it
    // for support
    ConsumerUID sid = null;

    // default construct for uninitialized object
    public TransactionAcknowledgement() {
    }

    /**
     * Construct the acknowledgement with the specified sysid and iid.
     * @param sysid	message system id
     * @param iid	interest id
     */
    public TransactionAcknowledgement(SysMessageID sysid, ConsumerUID iid,
            ConsumerUID sid) {
        this.sysid = sysid;
        this.iid = iid;
        this.sid = sid;
    }

    /**
     * @return the interest id
     */
    public ConsumerUID getConsumerUID() {
        return iid;
    }

    /**
     * @return the stored interest id
     */
    public ConsumerUID getStoredConsumerUID() {
        return sid;
    }

    /**
     * @return the message system id
     */
    public SysMessageID getSysMessageID() {
	return sysid;
    }

    /**
     * Returns a hash code value for this object.
     * ?? just added the hashCode of sysid and iid together ??
     */
    public int hashCode() {
	return sysid.hashCode() + iid.hashCode();
    }

    // just compare the hashcode
    public boolean equals(Object o) {
	if ((o instanceof TransactionAcknowledgement) &&
	    (hashCode() == o.hashCode())) {
	    return true;
	} else {
	    return false;
	}
    }

    public String toString() {
	return "[" + sysid.toString() + "]" + iid.toString() + ":"
             + sid.toString();
    }

    // for serializing the object
    private void writeObject(ObjectOutputStream s) throws IOException {
	sysid.writeID(new DataOutputStream(s));
	s.writeObject(iid);
	s.writeObject(sid);
    }

    // for serializing the object
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException {

	sysid = new SysMessageID();
	sysid.readID(new DataInputStream(s));
	iid = (ConsumerUID)s.readObject();
        try {
	    sid = (ConsumerUID)s.readObject();
        } catch (Exception ex) { // deal w/ missing field in 3.0.1
            logger.log(Logger.DEBUG,"ReadObject: old transaction format");
            sid = iid;
        }
    }

    public Object readResolve() throws ObjectStreamException {
        // Replace w/ the new object
        Object obj = new 
            com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement(
                sysid, iid, sid);
        return obj;
    }
}
