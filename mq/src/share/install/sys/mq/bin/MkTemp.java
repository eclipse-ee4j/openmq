/*
 * Copyright (c) 2009, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;

/*
 * Java application to create temp files via java.io.File.createTempFile()
 *
 * java MkTemp [-prefix <tmpfile prefix>] [-suffix <tmpfile suffix>]
 * Defaults:
 *	<tmpfile prefix>	mqinstaller
 *	<tmpfile suffix>	null
 *
 */
public class MkTemp  {

    private String prefix	= "mqinstaller";
    private String suffix	= null;

    public MkTemp(String[] args)  {
	parseArgs(args);
    }

    public int doWork()  {
	int ret = 0;
	
	try  {
	    File tmpFile = File.createTempFile(prefix, suffix);
	    String tmpFilePath = tmpFile.getAbsolutePath();
	    System.out.println(tmpFilePath);
	} catch(IOException ioe)  {
	    System.err.println("Failed to create temp file: " + ioe);
	    ret = 1;
	}

	return (ret);
    }

    private void parseArgs(String[] args)  {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals("-prefix"))  {
		if (++i >= args.length)  {
		    usage();
		}
		prefix = args[i];
	    } else if (args[i].equals("-suffix"))  {
		if (++i >= args.length)  {
		    usage();
		}
		suffix = args[i];
	    } else if (args[i].equals("-h"))  {
		usage();
	    } else  {
		usage();
	    }
	}

    }

    public void usage()  {
	usage(null);
    }

    public void usage(String msg)  {
	if (msg != null)  {
            System.out.println(msg);
	}

        System.out.println("Usage: java " 
		+ getClass().getName()
		+ " [-prefix <tmpfile prefix>] [-suffix <tmpfile suffix>]");

	System.exit(1);
    }
  
    public static void main(String[] args) {
	MkTemp mt = new MkTemp(args);
	int ret = mt.doWork();

	System.exit(ret);
    }

}
