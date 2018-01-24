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
 * @(#)TransactionUID.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.migration;

import java.io.*;
import com.sun.messaging.jmq.util.UID;


/**
 * Transaction Unique Identifier. A globally unique identifier for
 * a transaction. 
 *
 * Object format prior to 370 filestore, use for migration purpose only.
 * @see com.sun.messaging.jmq.jmsserver.data.TransactionUID
 */
public class TransactionUID extends com.sun.messaging.jmq.util.UID {

    // compatibility w/ 3.01, 3.5, 3.6
    static final long serialVersionUID = 3158474602500727000L;

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

    public Object readResolve() throws ObjectStreamException {
        // Replace w/ the new object
        Object obj = new com.sun.messaging.jmq.jmsserver.data.TransactionUID(id);
        return obj;
    }
}
