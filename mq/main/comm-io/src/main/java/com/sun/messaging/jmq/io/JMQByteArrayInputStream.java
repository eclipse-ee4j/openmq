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
 * @(#)JMQByteArrayInputStream.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.ByteArrayInputStream;

/**
 * This class extends ByteArrayInputStream and provides the ability
 * to get the "count" and "pos" variables.
 */

public class JMQByteArrayInputStream extends ByteArrayInputStream {


    /**
     * Same as constructor for ByteArrayInputStream.
     */
    public JMQByteArrayInputStream(byte buf[]) {
	super(buf);
    }

    /**
     * Same as constructor for ByteArrayInputStream.
     */
    public JMQByteArrayInputStream(byte buf[], int offset, int length) {
	super(buf, offset, length);
    }

    /**
     * Get the current position in the buffer.
     *
     * @return    the index of the next character to read from the buffer
     */
    public int getPos() {
	return pos;
    }

    /**
     * Get the number of valid bytes in the buffer.
     *
     * @return    the number of valid bytes in the buffer
     */
    public int getCount() {
	return count;
    }

}
