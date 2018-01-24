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
 * @(#)VRecord.java	1.12 06/27/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * A VRecord encapsulates a variable sized record backed by a file.
 */
public abstract class VRecord {

    // Info from record header.
    protected int magic = VRFile.RECORD_MAGIC_NUMBER;	// Record's magic number
    protected short state;     // Record's state
    protected int capacity;    // Record buffer's size including header
    protected short cookie = VRFile.RESERVED_SHORT; // application cookie

    /*
     * Force any modifications made to the buffer to be written
     * to physical storage.
     */
    public abstract void force() throws IOException;
    public abstract void setCookie(short cookie) throws IOException;
    public abstract short getCookie();

    /**
     * Return the capacity of this VRecord.
     */
    public int getCapacity() {
	return capacity;
    }

    /**
     * Return the capacity for data (ignoring header) of this VRecord.
     */
    public int getDataCapacity() {
	return capacity - VRFile.RECORD_HEADER_SIZE;
    }

    abstract void free();

    abstract void allocate(short state);

    short getState() {
	return state;
    }

}


