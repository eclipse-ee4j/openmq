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
 * @(#)UserMgrUtils.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.io.*;

import com.sun.messaging.jmq.util.Password;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/** 
 * This class contains utility methods used by jmqusermgr.
 */
public class UserMgrUtils implements UserMgrOptions  {

    /**
     * Constructor
     */
    public UserMgrUtils() {
    } 

    /**
     * Return user input. Throws an exception if an error occurred.
     */
    public static String getUserInput(UserMgrProperties userMgrProps,
			String question) throws UserMgrException  {
        return(getUserInput(userMgrProps, question, null));
    }

    /**
     * Return user input. Return defResponse if no response ("") was
     * given. Throws an exception if an error occurred.
     */
    public static String getUserInput(UserMgrProperties userMgrProps,
			String question, String defResponse) throws UserMgrException  {

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	    Output.stdOutPrint(question);
	    String s = in.readLine();

            if (s == null) {
                throw new EOFException("null");
            }
	    if (s.equals("") && (defResponse != null))  {
	        s = defResponse;
	    }
	    return(s);

        } catch (IOException ioex) {
	    UserMgrException ex = 
		new UserMgrException(UserMgrException.PROBLEM_GETTING_INPUT);
	    ex.setProperties(userMgrProps);
	    ex.setLinkedException(ioex);
	    
	    throw (ex);
        }
    }    

    /**
     * Return password input.  
     */
    public static String getPasswordInput(UserMgrProperties userMgrProps,
			String question) {

	Password pw = new Password();
    if (pw.echoPassword()) {
        Output.stdOutPrintln(Globals.getBrokerResources().
                getString(BrokerResources.W_ECHO_PASSWORD));
    }
	Output.stdOutPrint(question);
	return pw.getPassword();

    }

}    
