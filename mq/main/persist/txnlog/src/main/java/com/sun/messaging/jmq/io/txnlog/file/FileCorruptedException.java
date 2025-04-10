/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.io.txnlog.file;

import java.io.IOException;
import java.io.Serial;

/**
 * Signals that the transaction log file is corrupted.
 *
 * This exception is thrown if the FileTransactionLogWriter detects that there is a file corruption in the transaction
 * log.
 */
public class FileCorruptedException extends IOException {

    @Serial
    private static final long serialVersionUID = 272929095176266560L;

    public FileCorruptedException() {
    }

    public FileCorruptedException(String s) {
        super(s);
    }

}
