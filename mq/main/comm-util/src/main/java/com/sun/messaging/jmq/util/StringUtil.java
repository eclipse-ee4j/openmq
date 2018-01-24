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
 * @(#)StringUtil.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Properties;


/**
 * StringUtil
 */
public class StringUtil {

    /**
     * Expand property variables a string with the
     * corresponding values in a Properties instance. A property
     * variable has the form ${some.property}. The variable
     * ${/} is shorthand for ${file.separator}. So if the string contains
     * ${jmq.varhome}${/}${jmq.instancename}${/}store then it would be
     * expanded to something like /var/opt/SUNWjmq/jmqbroker/store
     * ${NL} is shorthand for ${line.separator}
     *
     * If there are no variables in 'str' then you will get back your
     * original string.
     *
     * @param str   The string to expand variables in
     * @param props The Properties object to extract variables from
     *
     * @returns A string with all variables expanded
     */
    public static String expandVariables(String str, Properties props) {

	if (str == null) return str;

	String vname, value = null;

	int len = str.length();
	StringBuffer sbuf = null;

	int current, vstart, vend;
	current = vstart = vend = 0;

	while (current < len) {
	    // Locate the start of a variable
	    if ((vstart = str.indexOf('$', current)) == -1) {

                if (sbuf != null) {
	            // No more variables. Copy remainder of string and stop
	            sbuf.append(str.substring(current, len));
                }
		break;
	    }

	    if (str.charAt(vstart + 1) == '{') {
		// We have a variable start. Find the end
	        if ((vend = str.indexOf('}', vstart + 1)) == -1) {

                  if (sbuf != null) {
		    //No end. No more vars. Copy remainder of string and stop
		    sbuf.append(str.substring(current, len));
                  }
		  break;
	        }
	    }

            // Looks like we are expanding variables. Allocate buffer
            // if we haven't already.
            if (sbuf == null) {
	        sbuf = new StringBuffer(2 * len);
            }

	    // ${jmq.home}
	    // ^         ^-vend
	    // +- vstart
            if (vend > vstart) {
	        vname = str.substring(vstart + 2, vend);
            } else {
                // Variable is malformed.
                vname = null;
                vend = vstart;
            }

	    // Get variable value
            if (vname == null) {
                value = null;
	    } else if (vname.equals("/")) {
		value = System.getProperty("file.separator");
            } else if (vname.equals("NL")) {
		value = System.getProperty("line.separator");
            } else if (props != null) {
	        value = props.getProperty(vname);
            }

	    // Copy over stuff before variable
	    sbuf.append(str.substring(current, vstart));

	    // Copy variable
	    if (value == null) {
		// No value. Just duplicate variable name and move on
		sbuf.append(str.substring(vstart, vend + 1));
            } else {
		// Good variable. Copy value
		sbuf.append(value);
            }

	    // Advance current pointer past variable and continue
	    current = vend + 1;
	}

        if (sbuf != null) {
	    return sbuf.toString();
        } else {
            // If we never expanded variables we can return the original string
            return str;
        }
    }


    /**
     * Convert a string of "key1=val1, key2=val2, .." to Properties
     */
    public static Properties toProperties(String keyvalPairs) {
        return toProperties(keyvalPairs, null); 
    }

    public static Properties toProperties(String keyvalPairs, Properties props) {
        return toProperties(keyvalPairs, ",", props);
    }

    public static Properties toProperties(String keyvalPairs, String separator, Properties p) {
        Properties props =  p;
        if (props == null) props = new Properties();
        if (keyvalPairs == null) return props;

        List<String> pairs = breakToList(keyvalPairs, separator);
        for (String pair : pairs) {
            List<String> l = breakToList(pair, "=");
            if (l.size() != 2 ) {
                throw new IllegalArgumentException("Invalid property element: "+pair);
            }
            props.setProperty(l.get(0), l.get(1));
        }
        return props;
    }

    public static List<String> breakToList(String value, String separator) {
        StringTokenizer token = new StringTokenizer(value, separator, false);
        List<String> retv = new ArrayList<String>();
        while (token.hasMoreElements()) {
            String newtoken = token.nextToken();
            newtoken = newtoken.trim();
            int start = 0;
            while (start < newtoken.length()) {
                if (!Character.isSpaceChar(newtoken.charAt(start)))
                    break;
                start ++;
            }
            if (start > 0)
                newtoken = newtoken.substring(start+1);
            if (newtoken.trim().length() > 0)
                retv.add(newtoken.trim());
        }
        return retv;
    }

}

