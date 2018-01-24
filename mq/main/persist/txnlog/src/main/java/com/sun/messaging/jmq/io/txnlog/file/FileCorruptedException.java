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
 * @(#)FileCorruptedException.java	1.2 06/29/07
 */ 

package com.sun.messaging.jmq.io.txnlog.file;

import java.io.IOException;

/**
 * Signals that the transaction log file is corrupted.
 *
 * This exception is thrown if the FileTransactionLogWriter detects
 * that there is a file corruption in the transaction log.
 */
public class FileCorruptedException  extends IOException {
    
    /** Creates a new instance of FileCorruptedException */
    public FileCorruptedException() {
        super();
    }
    
    public FileCorruptedException(String s) {
        super (s);
    }
    
}
