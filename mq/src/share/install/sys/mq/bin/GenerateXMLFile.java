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

import java.io.*;
import java.util.*;
import java.util.regex.PatternSyntaxException;

/*
 * java GenerateXMLFile -i <input file> -o <output file> -t <token1=value1> -t <token2=value2>
 * Defaults:
 *	<input file>		/etc/imq/xml/com.sun.cmm.mq.xml
 *	<output file>		$cwd/com.sun.cmm.mq.xml
 *	<token>			_INSTALL_DATE_
 *	<value>			<current date in milliseconds>
 *
 */
public class GenerateXMLFile  {
    String 			token		= null;
    Hashtable<String, String>	tokens		= null;
    String			inputFileName	= null;
    String			outputFileName	= null;
    String buf = null;
    private static final String	INSTALL_DATE	= "_INSTALL_DATE_";

    public GenerateXMLFile(Hashtable<String, String> tokens, String inputFileName, 
						String outputFileName)  {
        this.tokens = tokens;
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;

	setDefaults();

	try  {
	    openFile();
	} catch(Exception e)  {
	    System.out.println("Problems opening file: " + e);
	    System.exit(1);
	}

	try  {
	    replaceToken();
	} catch(Exception e)  {
	    System.out.println("Problems replacing token: " + e);
	    System.exit(1);
	}

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

        buf = new String(b);
    }

    private void replaceToken() throws PatternSyntaxException {
        for (Enumeration e = tokens.keys(); e.hasMoreElements() ;) {
            String curToken = (String)e.nextElement(),
                   curValue = (String)tokens.get(curToken);
            buf = buf.replaceAll(curToken, curValue);
        }
    }

    private void writeNewFile() throws FileNotFoundException, IOException  {
        FileOutputStream file = new FileOutputStream (outputFileName);
        byte[] b = buf.getBytes();
        file.write( b );
        file.close();
	System.out.println("Done writing out file: ");
    }

    private void setDefaults()  {
	if (tokens == null)  {
	    tokens = new Hashtable<String, String>();
	}

	if (!tokens.containsKey(INSTALL_DATE))  {
	    Date d = new Date();
            System.out.println("Date used: " + d);
	    String replacementString = "" + (d.getTime());
            System.out.println("Replacement string : " + replacementString);

	    tokens.put(INSTALL_DATE, replacementString);
	}

	if (inputFileName == null)  {
	    inputFileName = "/etc/imq/xml/template/com.sun.cmm.mq.xml";
	}

	if (outputFileName == null)  {
	    outputFileName = "com.sun.cmm.mq.xml";
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
	Hashtable<String, String> cmdlineTokens = null;
	String inputF = null, outputF = null;

	for (int i = 0; i < args.length; ++i)  {

	    if (args[i].equals("-i"))  {
		if (i+1 >= args.length)  {
		    usage("Path to input file not specified with -i", 1);
		}
		inputF = args[++i];
	    } else if (args[i].equals("-o"))  {
		if (i+1 >= args.length)  {
		    usage("Path to output file not specified with -o", 1);
		}
		outputF = args[++i];
	    } else if (args[i].equals("-t"))  {
		if (i+1 >= args.length)  {
		    usage("token=value pair not specified with -t", 1);
		}
		String tokenValuePair = args[++i],
		    token = getToken(tokenValuePair),
		    value = getValue(tokenValuePair);
		
		if (cmdlineTokens == null)  {
		    cmdlineTokens = new Hashtable<String, String>();
		}
		cmdlineTokens.put(token, value);
	    } else  {
		usage();
	    }
	}

	GenerateXMLFile rft = new GenerateXMLFile(cmdlineTokens, inputF, outputF);
    }

    private static String getToken(String tokenValuePair)  {
	if (tokenValuePair == null)  {
	    return (null);
	}

	int index = tokenValuePair.indexOf("=");

	if (index > 0) {
	    if (index == 0)  {
		return ("");
	    } else  {
		return (tokenValuePair.substring(0, index));
	    }
	}

	return (null);
    }

    private static String getValue(String tokenValuePair)  {
	if (tokenValuePair == null)  {
	    return (null);
	}

	int index = tokenValuePair.indexOf("=");

	if (index > 0) {
	    if (tokenValuePair.length() == 1)  {
		return ("");
	    } else  {
		return (tokenValuePair.substring(index+1));
	    }
	}

	return (null);
    }

}
