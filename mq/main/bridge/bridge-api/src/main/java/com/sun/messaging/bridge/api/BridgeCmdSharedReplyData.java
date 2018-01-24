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

package com.sun.messaging.bridge.api;

import java.io.Serializable;
import com.sun.messaging.jmq.util.MultiColumnPrinter;

/**
 * This class is shared by imqbridgemgr, BridgeServiceManager
 * and individual services
 *
 * @author amyk 
 */

public class BridgeCmdSharedReplyData extends MultiColumnPrinter {

    public BridgeCmdSharedReplyData(int numCol, int gap, String border, int align, boolean sort) {
	super(numCol, gap, border, align, sort);
    }

    public BridgeCmdSharedReplyData(int numCol, int gap, String border, int align) {
	super(numCol, gap, border, align);
    }

    public BridgeCmdSharedReplyData(int numCol, int gap, String border) {
	super(numCol, gap, border);
    }

    public BridgeCmdSharedReplyData(int numCol, int gap) {
	super(numCol, gap);
    }

    public void doPrint(String str) {
	    throw new UnsupportedOperationException("BridgeCmdSharedReplyData.doPrint");
    }

    public void doPrintln(String str) {
	    throw new UnsupportedOperationException("BridgeCmdSharedReplyData.doPrint");
    }
}
