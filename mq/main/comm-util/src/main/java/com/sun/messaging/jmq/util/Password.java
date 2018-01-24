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
 * @(#)Password.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.*;
import java.util.Arrays;
import java.lang.reflect.Method;

public class Password  { 

    private static boolean DEBUG = Boolean.getBoolean(
            "imq.debug.com.sun.messaging.jmq.util.Password");
    private static boolean useNative = false;

    private static final String library ="imqutil";
    private native String getHiddenPassword();

    public Password() {
    }

    public boolean echoPassword() {
        return (!hasJavaConsole() && !useNative); 
    }

    private boolean hasJavaConsole() {
        try {
            //Class consolec =  Class.forName("java.io.Console");
            Class.forName("java.io.Console");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private String getPasswordFromJavaConsole() {
        if (DEBUG) {
            System.err.println("use java.io.Console");
        }
        try {
            Class consolec =  Class.forName("java.io.Console");
            Method sysm = System.class.getMethod("console", (Class[])null);
            Method consolem = consolec.getMethod("readPassword", (Class[])null);
            Object console = sysm.invoke(null, (Object[])null);
            if (console == null) {
                throw new Exception("Console not available");
            }
            char[] password = (char[])consolem.invoke(console, (Object[])null);
            if (password == null) {
                return null;
            } 
            String pw = new String(password);
            Arrays.fill(password, ' ');
            password = null;
            return pw;
        } catch (Throwable e) {
            if (DEBUG) e.printStackTrace();
            return null;
        }
    }

    private String getClearTextPassword()  {
	String s = null;

	try  {
	    BufferedReader in;

	    in = new BufferedReader(new InputStreamReader(System.in));
	    s = in.readLine();
	} catch (IOException exc)  {
	    System.err.println("Caught exception when reading passwd: " + exc);
	}

	return (s);
    }

    // We should call this guy, since no one else needs to know
    // that this call is system-dependent.
    public String getPassword() {
        if (hasJavaConsole()) {
            return getPasswordFromJavaConsole();
        } 
        if (useNative) {
            return getHiddenPassword();
        }
        return getClearTextPassword();
    }

    static  {
        try {
    	    System.loadLibrary(library);
            useNative = true;
	} catch (Throwable ex) {
            useNative = false;
	}
    }


    public static void main(String[] args)  {
	Password pw;
	boolean	 clearText = false;
	boolean	 normal = false;

	if (args.length > 0)  {
	    if (args[0].equalsIgnoreCase("-c"))  {
		clearText = true;
	    }
	    if (args[0].equalsIgnoreCase("-n"))  {
		normal = true;
	    }
	}

	pw = new Password();

	System.out.print("Enter password: ");
	String s;

	if (normal)
	    s = pw.getPassword();
	else if (clearText)
	    s = pw.getClearTextPassword();
	else
	    s = pw.getHiddenPassword();

	System.err.println("");
	System.out.println("Password enterd is: " + s);
    }

}
