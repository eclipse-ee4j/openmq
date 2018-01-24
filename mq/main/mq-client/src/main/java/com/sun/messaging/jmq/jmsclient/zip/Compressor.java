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
 * @(#)Compressor.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.zip;

import java.util.zip.*;
import java.io.*;

import com.sun.messaging.jmq.jmsclient.Debug;

public class Compressor {

    private Deflater deflater = new Deflater();

    //private Deflater noWrapDeflater = new Deflater (Deflater.DEFAULT_COMPRESSION, true);

    private static Compressor compressor = new Compressor();

    private boolean debug = Boolean.getBoolean("imq.zip.debug");

    private Compressor () {
    }

    public static Compressor getInstance() {
        return compressor;
    }

    public synchronized int
    compress (byte[] body, int offset, int length, OutputStream os) throws IOException {

        int compressedLength = -1;

        Deflater def = getDefaultDeflater();

        DeflaterOutputStream defos = new DeflaterOutputStream(os, def);

        defos.write(body, offset, length);

        defos.finish();

        int totalIn = def.getTotalIn();
        compressedLength = def.getTotalOut();

        if (debug) {
            Debug.getPrintStream().println
                ("**** compressor total in: " + totalIn);

            Debug.getPrintStream().println
                ("**** compressor total out: " + compressedLength);
        }

        def.reset();

        return compressedLength;
    }

    private Deflater getDeflater(boolean noWrap, int strategy, int level) {

        Deflater def = null;

        if ( noWrap ) {
            //def = noWrapDeflater;
            throw new RuntimeException ("No wrap deflater is not Unsupported.");
        } else {
            def = deflater;
        }

        def.setStrategy( strategy );

        def.setLevel( level );

        return def;
    }

    private Deflater getDefaultDeflater() {
        return getDeflater (false, Deflater.DEFAULT_STRATEGY, Deflater.DEFAULT_COMPRESSION);
    }
}
