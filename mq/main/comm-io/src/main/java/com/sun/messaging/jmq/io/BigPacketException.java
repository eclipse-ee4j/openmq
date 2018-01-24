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
 * @(#)BigPacketException.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.IOException;


/**
 * BigPacketException
 */
public class BigPacketException extends IOException {

    private int skipBytesRemaining = 0;

    public BigPacketException () {
        super();
    }

    public BigPacketException (String s) {
        super(s);
    }

    public void setSkipBytesRemaining(int v) {
        skipBytesRemaining = v;
    }

    public int getSkipBytesRemaining() {
        return skipBytesRemaining; 
    }
}
