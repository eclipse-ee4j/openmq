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
 */ 

package com.sun.messaging.bridge.admin.bridgemgr;

import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;

/**
 * Event class indicating some actions related to
 * Bridge Management.
 *<P>
 * The fields of this event include the various pieces of information
 * needed for broker management tasks.
 */
public class BridgeMgrStatusEvent extends CommonCmdStatusEvent {

    /*******************************************************************************
     * BridgeMgrStatusEvent event types
     * use integers 5000 - 5999  
     *******************************************************************************/
    public enum Type {
        ;
        public final static int HELLO  = 5000;
        public final static int LIST   = 5001;
        public final static int START  = 5002;
        public final static int STOP   = 5003;
        public final static int RESUME = 5004;
        public final static int PAUSE  = 5005;
        public final static int DEBUG  = 5006;
    }

    private transient BridgeAdmin  ba;

    /**
     * @param source the object where the event originated
     * @type the event type
     */
    public BridgeMgrStatusEvent(Object source, int type) {
	super(source, type);
    }

    /**
     * @param source the object where the event originated
     * @type the event type
     */
    public BridgeMgrStatusEvent(Object source, BridgeAdmin ba, int type) {
	super(source, type);
	setBridgeAdmin(ba);
    }

    public void setBridgeAdmin(BridgeAdmin ba) {
	this.ba = ba;
    }
    public BridgeAdmin getBridgeAdmin() {
	return (ba);
    }
   
}
