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
 * @(#)PrefixMessages.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.util.test;

import java.text.*;
import java.util.*;

/**
 * This class has a method that will take the Object[][] from a ListResourceBundle
 * and appends the given string to all the strings in the messages (not the keys).
 *
 * Notes:
 *   - All of the objects need to be strings<p>
 */

public class PrefixMessages {
    public static Object[][] createPrefixedMessages(Object[][] old, String prefix) {
        Object[][] newContents = new Object[ old.length ][2];

        // loop through and reverse the contents
        for( int i = 0; i < old.length; i++ ) {
            try {
	        newContents[i][0] = old[i][0];
		String tmp = (String)old[i][1];

		newContents[i][1] = prefix + tmp;
            } catch (Exception e)  {
	        System.err.println("Problem encountered when generating new messages. Index="
				+ i
				+ ".\n"
				+ e.toString());

	        // just copy over the originals
	        newContents[i][0] = old[i][0];
	        newContents[i][1] = old[i][1];
	    }
        }

	return (newContents);
    }
}
