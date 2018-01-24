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
 * @(#)TransactionUID.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.*;
import com.sun.messaging.jmq.util.UID;


/**
 * Transaction Unique Identifier. A globally unique identifier for
 * a transaction. 
 */
public class TransactionUID extends com.sun.messaging.jmq.util.UID
    implements Externalizable {

    public TransactionUID() {
        // Allocates a new id
        super();
    }

    public TransactionUID(long id) {
        // Wraps an existing id
        super(id);
    }

    public String toString() {
        return super.toString();
    }

    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {
        id = in.readLong();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(id);
    }
}
