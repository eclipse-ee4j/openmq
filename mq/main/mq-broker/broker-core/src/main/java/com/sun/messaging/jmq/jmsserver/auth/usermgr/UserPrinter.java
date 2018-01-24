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
 * @(#)UserPrinter.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import com.sun.messaging.jmq.util.MultiColumnPrinter;

/*
 * This class is for printing the user database in neat
 * collumns as in:
 * <PRE>
 *
 * --------------------------------------
 * User Name    Role         Active state
 * --------------------------------------
 * admin        admin        true
 * guest        anonymous    true
 *
 * </PRE>
 */
public class UserPrinter extends MultiColumnPrinter {

    public UserPrinter(int numCol, int gap, String border) {
	super(numCol, gap, border);
    }

    public UserPrinter(int numCol, int gap) {
	super(numCol, gap);
    }

    public void doPrint(String str) {
        Output.stdOutPrint(str);
    }

    public void doPrintln(String str) {
        Output.stdOutPrintln(str);
    }
}
