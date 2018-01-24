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
 * @(#)SizeString.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.*;


/**
 * This is an object which represents a String which represents
 * bytes in the format of:
 *
 *    #[bkm] where:
 *     128 -> 128 Kbytes
 *     128b -> 128 bytes
 *     128k -> 128 kbytes
 *     128m -> 128 Mbytes
 */ 
public class SizeString implements Serializable
{
    private static final long K = 1024;
    private static final long M = 1024*1024;
    private static final long B = 1;

    String str = null;
    long bytes = 0;
    public SizeString(String str)
        throws NumberFormatException
    {
        setString(str);
    }

    public SizeString()
        throws NumberFormatException
    {
        setString("0b");
    }

    public SizeString(long newKbytes)
    {
        setKBytes(newKbytes);
    }


    public void setString(String setstr)
        throws NumberFormatException
    {
        this.str = setstr;
        long multiplier = B;
        if (str == null) {
            this.str = null;
            bytes = 0;
            return;
        }
        if (Character.isLetter(setstr.charAt(str.length() -1))) {
            char multchar = setstr.charAt(str.length() -1);
            setstr = str.substring(0,str.length() -1);
            switch (multchar) {
                case 'm':
                case 'M':
                    multiplier = M;
                    break;

                case 'k':
                case 'K':
                    multiplier = K;
                    break;

                case 'b':
                case 'B':
                    multiplier = B;
                    break;

                default:
                    throw new NumberFormatException("Unknown size " + multchar);
             }
        }
        int val = Integer.parseInt(setstr);
        bytes = val * multiplier;
 
    }

    public String getString()
    {
        return str;
    }

    public String getByteString()
    {
        return bytes + "b";
    }

    public String getKByteString()
    {
        return getKBytes() + "K";
    }

    public String getMByteString()
    {
        return getMBytes() + "M";
    }

    public void setKBytes(long newKbytes) {
        this.str = String.valueOf(newKbytes) + "K";
        bytes = newKbytes*K;
    }

    public void setMBytes(long newMbytes) {
        this.str = String.valueOf(newMbytes) + "M";
        bytes = newMbytes*M;
    }

    public void setBytes(long newbytes) {
        this.str = String.valueOf(newbytes) + "b";
        bytes = newbytes*B;
    }

    public long getBytes() {
        return bytes;
    }

    public long getKBytes() {
        return (bytes == 0) ? 0 : bytes/K;
    }

    public long getMBytes() {
        return (bytes == 0) ? 0 : bytes/M;
    }

    public String toString() {
        return getString();
    }
    
    public static void main(String args[])
    {
        try {
	    System.err.println("## 100b");
            System.err.println((new SizeString("100b")).toString());
            System.err.println((new SizeString("100b")).getByteString());
            System.err.println((new SizeString("100b")).getKByteString());
            System.err.println((new SizeString("100b")).getMByteString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
         try {
	    System.err.println("## 100k");
            System.err.println((new SizeString("100k")).toString());
            System.err.println((new SizeString("100k")).getByteString());
            System.err.println((new SizeString("100k")).getKByteString());
            System.err.println((new SizeString("100k")).getMByteString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
	    System.err.println("## 100m");
            System.err.println((new SizeString("100m")).toString());
            System.err.println((new SizeString("100m")).getByteString());
            System.err.println((new SizeString("100m")).getKByteString());
            System.err.println((new SizeString("100m")).getMByteString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            (new SizeString("100B")).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
         try {
            (new SizeString("100K")).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            (new SizeString("100M")).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
          try {
            (new SizeString("100")).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
           try {
            (new SizeString("100L")).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
       
    }
}
