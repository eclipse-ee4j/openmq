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
 * @(#)JMQByteArrayOutputStream.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.ByteArrayOutputStream;

/**
 * This class extends ByteArrayOutputStream to provide the ability
 * to more directly manage the backing buffer to reduce the number of
 * memory allocations and data copies. In particular it supports
 * a constructor that accepts a byte array to use as the backing buffer
 * and an accessor to get the buffer back without making a copy.
 *
 * WARNING! Providing these hooks breaks the safe encapsulation that 
 * ByteArrayOutputStream provides, so be careful. In particular
 * please see the warning on the getBuf() method.
 */

public class JMQByteArrayOutputStream extends ByteArrayOutputStream {

    /**
     * Creates a new byte array output stream with the specified buffer
     * as the initial backing buffer. Note: the provided buffer may
     * be replaced with another buffer if the implementation finds it
     * necessary to do so (for example to increase the buffer size).
     * You should consider this constructor to be an optimization hint
     * and always use getBuf() to retrieve the backing buffer.
     * 
     * @param    size    the buffer to use as the backing buffer.
     */
    public JMQByteArrayOutputStream(byte newBuf[]) {
	// If we don't invoke a constructor the no-arg constructor is
	// invoked which allocates a 32 byte buffer we don't want;
	super(0);
	buf = newBuf;
    }

    /**
     * Returns a reference to the backing buffer
     *
     * @return    a reference to the backing buffer. WARNING! This is a 
     *            reference to the buffer -- not a copy. Note that 
     *		  ByteArrayOutputStream will reallocate this buffer if it
     *		  needs more space -- so the buffer returned by getBuf()
     *		  may not match the buffer provided in the constructor!
     */
    public byte[] getBuf() {
	return buf;
    }

    /**
     * Returns the number of valid bytes in the buffer.
     *
     * @return    the number of valid bytes in the buffer.
     */
    public int getCount() {
	return count;
    }
}
