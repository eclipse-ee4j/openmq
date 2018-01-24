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
 * @(#)SetupJDKHome.java	1.4 07/11/07
 */ 

import java.io.*;
import java.util.*;

/*
 * java SetupJDKHome -i <input file> -j <string containing JDK location>
 * Defaults:
 *	<input file>		/etc/imq/imqenv.conf
 *	<string>		IMQ_DEFAULT_JAVAHOME=/usr/jdk/latest
 *
 */
public class SetupJDKHome  {
    String			jdkHomeString	= null;
    String			inputFileName	= null;
    StringBuffer		buf = null;
    private static final String	IMQ_DEFAULT_JAVAHOME	= "IMQ_DEFAULT_JAVAHOME=/usr/jdk/latest";

    public SetupJDKHome(String inputFileName, String jdkHomeString)  {
        this.inputFileName = inputFileName;
        this.jdkHomeString = jdkHomeString;

	setDefaults();

	try  {
	    openFile();
	} catch(Exception e)  {
	    System.out.println("Problems opening file: " + e);
	    System.exit(1);
	}

	appendBuffer();

	try  {
	    writeNewFile();
	} catch(Exception e)  {
	    System.out.println("Problems writing file: " + e);
	    System.exit(1);
	}

    }

    private void openFile() throws FileNotFoundException, IOException {
        FileInputStream file = new FileInputStream (inputFileName);
        byte[] b = new byte[file.available ()];
        file.read( b );
        file.close();

        buf = new StringBuffer(new String(b));
	System.out.println("Done reading in file: " + inputFileName);
    }

    private void appendBuffer() {
	buf.append(jdkHomeString + "\n");
	System.out.println("Appending: " + jdkHomeString);
    }

    private void writeNewFile() throws FileNotFoundException, IOException  {
        FileOutputStream file = new FileOutputStream (inputFileName);
        byte[] b = buf.toString().getBytes();
        file.write( b );
        file.close();
	System.out.println("Done writing out file: " + inputFileName);
    }

    private void setDefaults()  {
	if (jdkHomeString == null)  {
	    jdkHomeString = IMQ_DEFAULT_JAVAHOME;
	}

	if (inputFileName == null)  {
	    inputFileName = "/etc/imq/imqenv.conf";
	}
    }

    public static void usage()  {
        usage(null, 0);
    }

    public static void usage(String msg)  {
        usage(msg, 0);
    }

    public static void usage(String msg, int exitCode)  {
	if (msg != null)  {
            System.out.println(msg);
	}
        System.out.println("Usage:");

        System.exit(exitCode);
    }

    public static void main(String[] args) {
	String jdkHomeString = null;
	String inputF = null;

	for (int i = 0; i < args.length; ++i)  {

	    if (args[i].equals("-i"))  {
		if (i+1 >= args.length)  {
		    usage("Path to input file not specified with -i", 1);
		}
		inputF = args[++i];
	    } else if (args[i].equals("-j"))  {
		if (i+1 >= args.length)  {
		    usage("String containing JDK location not specified with -j", 1);
		}
		jdkHomeString = args[++i];
	    } else  {
		usage();
	    }
	}

	SetupJDKHome rft = new SetupJDKHome(inputF, jdkHomeString);
    }
}
