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
 * @(#)AckEntryNotFoundException.java	1.2 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util;

import java.util.ArrayList;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;

public class AckEntryNotFoundException extends BrokerException {

    ArrayList[] aes = null;

    public AckEntryNotFoundException(String msg) {
        super(msg, Status.NOT_FOUND);
        aes = new ArrayList[2];
        aes[0] = new  ArrayList();
        aes[1] = new ArrayList();
    }

    public void addAckEntry(SysMessageID sysid, Object cuid) {
        aes[0].add(sysid); 
        aes[1].add(cuid);
    }

    public ArrayList[] getAckEntries() {
        return aes;
    }

}
