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
 * @(#)Decompressor.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.zip;

import java.util.zip.*;

import javax.jms.*;

import com.sun.messaging.jmq.jmsclient.Debug;

public class Decompressor {

    private Inflater inflater = new Inflater(false);

    private static Decompressor decompressor = new Decompressor();

    private boolean debug = Boolean.getBoolean("imq.zip.debug");

    private Decompressor () {
    }

    public static Decompressor getInstance() {
        return decompressor;
    }

    public synchronized void
    decompress (byte[] zipBody, byte[] unzipBody) throws JMSException {

        Inflater inf = getDefaultInflater();

        try {
            inf.setInput( zipBody );

            int uncompressedSize = inf.inflate( unzipBody );

            if ( uncompressedSize != unzipBody.length ) {
                //This should never happen!
                throw new JMSException (
                "Error occurred in decompression. unzip size: " +
                uncompressedSize + " expected size: " + unzipBody.length);
            }

            if ( debug ) {
                Debug.getPrintStream().println
                    ("*** decompressor zip size: " + zipBody.length);
                Debug.getPrintStream().println
                    ("*** decompressor unzip size: " + uncompressedSize);
            }

        } catch (DataFormatException e) {
            JMSException jmse = new JMSException (e.toString());
            jmse.setLinkedException(e);
            throw jmse;
        } finally {
            inf.reset();
        }
    }

    private Inflater getDefaultInflater() {
        return this.inflater;
    }

}
