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

/*
 */

package com.sun.messaging.jmq.jmsserver.cluster.api;

import java.io.*;
import java.util.Map;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * Interface to facilitate file transfer
 */
public interface FileTransferCallback {

    // module names
    String STORE = "store";

    /**
     * Get the file input stream for the file to be transfered
     *
     * @param filename the relative filename to be transfered
     * @throws BrokerException
     */
    FileInputStream getFileInputStream(String filename, BrokerAddress to, Map props) throws BrokerException;

    /**
     * Get the file output stream for file to be transfered over
     *
     * @param tmpfilename the relative temporary filename to be used during transfer
     * @param first file of the set of files transfering over
     * @throws BrokerException
     */
    FileOutputStream getFileOutputStream(String tmpfilename, String brokerID, String uuid, boolean firstOfSet, BrokerAddress from)
            throws BrokerException;

    /**
     * Called when the file has been successfully transfered over
     *
     * @param tmpfilename the temporary file name used
     * @param filename the real file name to be renamed to from tmpfilename
     * @param lastModTime the last modification time of the file
     * @param success whether the file transfer over is success
     * @param ex if success false, any exception
     */
    void doneTransfer(String tmpfilename, String filename, String brokerID, long lastModTime, boolean success, BrokerAddress from)
            throws BrokerException;

    /**
     * Called when the set of files have been successfully transfered over
     */
    void allDoneTransfer(String brokerID, String uuid, BrokerAddress from) throws BrokerException;
}
