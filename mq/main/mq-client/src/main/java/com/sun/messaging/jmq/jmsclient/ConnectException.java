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
 * @(#)ConnectException.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import com.sun.messaging.jms.JMSException;
import java.io.*;
import javax.jms.*;

/**
 * This is the connection failure exception thrown by the
 * ConnectionInitiator class when more than one broker addresses are
 * used. It encapsulates the last transport exception (cause) for each
 * address in the list.
 */
public class ConnectException extends com.sun.messaging.jms.JMSException {
	private Exception[] elist = null;
    private String[] alist = null;

    public ConnectException(String reason, String errorCode,
        Exception[] elist, String[] alist) {
        super(reason, errorCode);
        this.elist = elist;
        this.alist = alist;
    }

    /**
     * Print all the linked exceptions.
     */
    public
    void printStackTrace(PrintStream s) {
        super.printStackTrace(s);

        if (elist == null)
            return;

        for (int i = 0; i < elist.length; i++) {
            s.println("\n###### Connect exception for : " + alist[i]);
            elist[i].printStackTrace(s);
        }
    }

    /**
     * Print all the linked exceptions.
     */
    public
    void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);

        if (elist == null)
            return;

        for (int i = 0; i < elist.length; i++) {
            s.println("\n###### Connect exception for : " + alist[i]);
            elist[i].printStackTrace(s);
        }
    }
}
/*
 * EOF
 */
