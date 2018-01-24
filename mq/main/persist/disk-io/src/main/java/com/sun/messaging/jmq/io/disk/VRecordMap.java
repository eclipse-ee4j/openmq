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
 * @(#)VRecordMap.java	1.6 06/27/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * A VRecordMap encapsulates a slice of mapped buffer allocated by VRFileMap.
 */
public class VRecordMap extends VRecord {

    private static boolean DEBUG = Boolean.getBoolean("vrfile.debug");


    private VRFileMap vrfile;
    private ByteBuffer bbuf;
    private ByteBuffer databuf; // slice after the header

    private MappedByteBuffer parent;

    // instantiate with an existing record (sanity checked by caller)
    VRecordMap(VRFileMap v, MappedByteBuffer p, ByteBuffer buf) {
	vrfile = v;
	parent = p;
	bbuf = buf;

	// read header
	magic = bbuf.getInt();
	capacity = bbuf.getInt();
	state = bbuf.getShort();
	cookie = bbuf.getShort();

	bbuf.limit(capacity);
	bbuf.position(VRFile.RECORD_HEADER_SIZE);
	databuf = bbuf.slice();
    }

    // instantiate with an uninitialized record
    VRecordMap(VRFileMap v, MappedByteBuffer p, ByteBuffer buf, int size) {
	vrfile = v;
	parent = p;
	bbuf = buf;

	capacity = size;
	state = VRFile.STATE_ALLOCATED;

	// write header
	bbuf.putInt(magic);
	bbuf.putInt(capacity);
	bbuf.putShort(state);
	bbuf.putShort(cookie);

	bbuf.limit(capacity);
	bbuf.position(VRFile.RECORD_HEADER_SIZE);
	databuf = bbuf.slice();
    }

    /**
     * Get the record buffer. Its 'capacity' may be larger than what
     * was requested. Its 'limit' will match what was requested.
     * Whatever is written to the buffer may be written to the backing
     * file, but is not guaranteed to be until force() is called
     * or the VRfile is closed.
     */
    public ByteBuffer getBuffer() {
	return databuf;
    }

    /*
     * Force any modifications made to the buffer to be written
     * to physical storage.
     */
    public void force() throws IOException {
	if (DEBUG) {
	    System.out.println("will do force on "+parent);
	}

	parent.force();
    }

    public void setCookie(short c) throws IOException {
	this.cookie = c;
	bbuf.putShort(VRFile.RECORD_COOKIE_OFFSET, cookie);

	if (vrfile.getSafe()) {
	    force();
	}
    }

    public short getCookie() {
	return cookie;
    }

    public String toString() {
	return "VRecordMap: "+bbuf.toString();
    }

    MappedByteBuffer getParent() {
	return parent;
    }

    void free() {
	state = VRFile.STATE_FREE;
	bbuf.putShort(VRFile.RECORD_STATE_OFFSET, state);
	bbuf.putShort(VRFile.RECORD_COOKIE_OFFSET, VRFile.RESERVED_SHORT);
	databuf.rewind();
    }

    void allocate(short s) {
	state = s;
	bbuf.putShort(VRFile.RECORD_STATE_OFFSET, state);
    }
}


