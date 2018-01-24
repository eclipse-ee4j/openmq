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

package com.sun.messaging.bridge.api;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.Properties;

/**
 * These utility methods are from jmsserver/config/
 */

public class BridgeUtil {

    /**
     *
     */
    public static List<String> getListProperty(String name, Properties props) {
        String value = props.getProperty(name);
        if (value == null) return new ArrayList<String>();
        return breakToList(value, ",");
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


    /**
     * Returns a list of property names that match specified name prefix.
     *
     * @param prefix The proerty name prefix
     * @return a list of all property names that match the name prefix
     */
    public static List<String> getPropertyNames(String prefix, Properties props) {
        ArrayList<String> list = new ArrayList<String>();
        Enumeration e = props.keys();
        while (e.hasMoreElements()) {
            String key = (String)e.nextElement();
            if (key.startsWith(prefix)) {
                list.add(key);
            }
        }
        return list;
    }

    public static String toString(String[] args) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < args.length; i++) {
           buf.append(args[i]);
           buf.append(" ");
        }
        return buf.toString();
    }
}
