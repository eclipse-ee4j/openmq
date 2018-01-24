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
 * @(#)TransactionAcknowledgement.java	1.15 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import java.io.*;

/**
 * Acknowledgement for transactions.
 */

public class TransactionAcknowledgement implements Externalizable
{
    transient private Logger logger = Globals.getLogger();

    SysMessageID sysid = null;
    ConsumerUID iid = null;

    static final int MSG_PENDING=0;

    // ok if a message is in MSG_COMPLETE it does not need to
    // be redelivered
    static final int MSG_COMPLETE=1;
    transient int state=MSG_PENDING;

    transient boolean shouldStore = true;

    public void setMsgComplete() {
        state=MSG_COMPLETE;
    }
    public boolean getMsgComplete() {
        return state == MSG_COMPLETE;
    }

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

    public boolean shouldStore()
    {
        return shouldStore; 
    }

    public void setShouldStore(boolean b) {
        shouldStore = b;
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

    
    
    public boolean equals(Object o) {
		if (o instanceof TransactionAcknowledgement) {
			TransactionAcknowledgement that = (TransactionAcknowledgement) o;
			if (this.sysid.equals(that.sysid) && this.iid.equals(that.iid)) {
				return true;
			}
		}
		return false;
	}

    public String toString() {
	return "[" + sysid.toString() + "]" + iid.toString() + ":"
             + sid.toString();
    }

    // for serializing the object
    public void writeExternal(ObjectOutput out) throws IOException {
	sysid.writeID(out);
	out.writeObject(iid);
	out.writeObject(sid);
    }

    // for serializing the object
    public void readExternal(ObjectInput in)
	throws IOException, ClassNotFoundException {

	sysid = new SysMessageID();
	sysid.readID(in);
	iid = (ConsumerUID)in.readObject();
        try {
	    sid = (ConsumerUID)in.readObject();
        } catch (Exception ex) { // deal w/ missing field in 3.0.1
            logger.log(Logger.DEBUG,
                "TransactionAcknowledgement.readExternal(): old transaction format");
            sid = iid;
        }
    }
}


