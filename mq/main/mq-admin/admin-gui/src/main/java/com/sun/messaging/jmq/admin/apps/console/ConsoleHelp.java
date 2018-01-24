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
 * @(#)ConsoleHelp.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.io.File;
import java.net.URL;


import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;

import com.sun.messaging.jmq.admin.util.Globals;


/** 
 * This class initializes all the help objects used by the JMQ admin
 * console.
 *
 */
public class ConsoleHelp {

    /*
     * Image file names.
     *
     * File names are relative to
     * JMQ_HOME/lib/help
     */
    private static String hsURLStrs[] = {
	    "Master.hs",				/* CONSOLE_HELP */
    };

    /*
     * Indices for help sets
     */
    public final static int CONSOLE_HELP 			= 0;




    static HelpBroker 	hb[];
    static HelpSet 	hs[];
    static CSH.DisplayHelpFromSource hl[];

    private static boolean	helpLoaded = false;
    private static Exception	helpLoadException = null;

    public static boolean helpLoaded() {
	return (helpLoaded);
    }

    public static Exception getHelpLoadException() {
	return (helpLoadException);
    }

    public static void loadHelp() {
	int		hsTotal;

	if (helpLoaded)
	    return;

	hsTotal = hsURLStrs.length;

	try {
	    Class c;

	    c = Class.forName("javax.help.HelpBroker");
	    c = Class.forName("javax.help.HelpSet");
	    c = Class.forName("javax.help.CSH");
	} catch (Exception e)  {
	    helpLoadException = e;
	    return;
	}

	hb = new HelpBroker[ hsTotal ];
	hs = new HelpSet[ hsTotal ];
	hl = new CSH.DisplayHelpFromSource[ hsTotal ];

	/*
	System.out.println("Loading Help ...");
	*/

	/*
         * File names are relative to
         * JMQ_HOME/lib which should already be
	 * in the CLASSPATH.
	 */

	for (int i = 0; i < hsTotal; ++i)  {
	    String fileName;

	    fileName = hsURLStrs[i];

	    /*
            System.err.println("loading: " + fileName);
	    */

	    try {
	        URL hsURL = HelpSet.findHelpSet(null, fileName);
		if (hsURL == null)  {
		    return;
		}
	        hs[i] = new HelpSet(null, hsURL);
	        hb[i] = hs[i].createHelpBroker();
	        hl[i] = new CSH.DisplayHelpFromSource(hb[i]);

	    } catch (Exception e) {
		System.err.println("HelpSet " + fileName + " not found.");
		return;
	    }
	}

	helpLoaded = true;
    }
}
