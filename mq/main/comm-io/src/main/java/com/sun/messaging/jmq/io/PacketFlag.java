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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.io;

/**
 * This class defines the JMQ packet bit flags and provides some
 * convenience routines.
 */
public class PacketFlag {

    public static final int Q_FLAG =  0x0001;
    public static final int R_FLAG =  0x0002;
    public static final int P_FLAG =  0x0004;
    public static final int S_FLAG =  0x0008;
    public static final int A_FLAG =  0x0010;
    public static final int L_FLAG =  0x0020;
    public static final int F_FLAG =  0x0040;
    public static final int T_FLAG =  0x0080;
    public static final int C_FLAG =  0x0100;
    public static final int B_FLAG =  0x0200;
    public static final int Z_FLAG =  0x0400;
    public static final int I_FLAG =  0x0800;
    public static final int W_FLAG =  0x1000;

    /**
     * Return a human readable string describing the bits set in "flags"
     */
    public static String getString(int flags) {
	String s =
	    ((flags & A_FLAG) == A_FLAG ? "A" : "") +
	    ((flags & S_FLAG) == S_FLAG ? "S" : "") +
	    ((flags & P_FLAG) == P_FLAG ? "P" : "") +
	    ((flags & R_FLAG) == R_FLAG ? "R" : "") +
	    ((flags & Q_FLAG) == Q_FLAG ? "Q" : "") +
	    ((flags & L_FLAG) == L_FLAG ? "L" : "") +
	    ((flags & F_FLAG) == F_FLAG ? "F" : "") +
	    ((flags & T_FLAG) == T_FLAG ? "T" : "") +
	    ((flags & B_FLAG) == B_FLAG ? "B" : "") +
	    ((flags & Z_FLAG) == Z_FLAG ? "Z" : "") +
	    ((flags & C_FLAG) == C_FLAG ? "C" : "") +
	    ((flags & I_FLAG) == I_FLAG ? "I" : "") +
	    ((flags & W_FLAG) == W_FLAG ? "W" : "");

        return s;
    }

}
