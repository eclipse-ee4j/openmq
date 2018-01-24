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
 * @(#)MQResourceBundle.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.text.*;
import java.util.*;

/**
 * This class wraps a ResourceBundle and provides
 * several methods to support parameterized messages and 
 * prefix the message string with the key used to look up the message
 * (Useful if your application uses numeric keys that you want
 * to display with the message).
 *
 * Note: For parameterized messages, the message patterns should be
 *	 stored as String objects in the resource bundle.
 *
 * This class also performs EOL conversion so that newline termination
 * is correct on all platforms. The string is assumed to be stored in
 * the resource bundle with '\n' as the newline terminator. During runtime
 * when the string is fetched, all '\n' characters are converted to the
 * native EOL terminator. Converted strings are cached so we don't need
 * to perform the conversion every time.
 */

public class MQResourceBundle extends ResourceBundle
{
    public static final String NL = System.getProperty("line.separator", "\n");

    private ResourceBundle rb = null;
    private boolean convertEOL = false;        
    private static String UnixEOL = "\n";

    private HashMap cache = null;

    private static boolean DEBUG = false;

    public MQResourceBundle(ResourceBundle rb) {
        this.rb = rb;
        String s = System.getProperty("line.separator");

        // Messages are stored in the bundle with UNIX EOL termination ("\n").
        // Check if we need to convert these to the native platform's EOL.
        if (!s.equals(UnixEOL)) {
            convertEOL = true;
            cache = new HashMap();
            if (DEBUG) {
                System.out.println(this.getClass().getName() +
                    ": Will convert messages to use native EOL.");
            }
        }
    }

    /**
     * Get an object from this resource bundle. In general we just forward
     * this request to the wrapped ResourceBundle. But if we need to do
     * EOL conversion and the object we get is a string, then we need to
     * interpose. 
     *
     * We cache converted strings so we don't need to convert every time
     * we get a string.
     */
    public Object handleGetObject(String key) {
        if (convertEOL) {
            // For windows we want to perform proper EOL termination
            // This is particularly important for messages that get logged.
            // First check cache to see if we've converted string already.
            Object o = null;
            synchronized(cache) {
                o = cache.get(key);
                if (o == null) {
                    // Not in cache. Convert if string
                    o = rb.getObject(key);
                    if (o instanceof String) {
                        o = unix2native((String)o);
                        cache.put(key, o);
                    } 
                }
            }
            return o;
        } else {
            return rb.getObject(key);
        }
    }


    /**
     * Get formatted string with one argument.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	arg	Argument to the message pattern.
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getString(String key, Object arg)
	throws MissingResourceException
    {
	// the following check is needed because when the 
	// second argument is of type Object[], this method
	// is called instead of getString(String, Object[])
	if (arg instanceof Object[])
	    return MessageFormat.format(getString(key), (Object[])arg);
	else {
            Object[] args = {arg};

	    return MessageFormat.format(getString(key), args);
	}
    }

    /**
     * Get formatted string with two arguments.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	arg1	First argument to the message pattern.
     * @param	arg2	Second argument to the message pattern.
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getString(String key, Object arg1, Object arg2)
	throws MissingResourceException
    {
        Object[] args = {arg1, arg2};

	return MessageFormat.format(getString(key), args);
    }


    /**
     * Get formatted string with 3 or more arguments.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	args	Array of arugments to the message pattern
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getString(String key, Object[] args)
	throws MissingResourceException
    {
	return MessageFormat.format(getString(key), args);
    }



    /********************************************************************
     * The following methods mirror the ones above but prefix the
     * message String with the key String.
     ********************************************************************/


    /**
     * Get string. Prefix with key.
     *
     * @param	key	Key that identifies the localized message pattern
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getKString(String key)
	throws MissingResourceException
    {
	return "[" + key + "]: " + getString(key);
    }

    public String getKTString(String key)
	throws MissingResourceException
    {
	return "[" + key + "]: "+"["+Thread.currentThread()+"]"+ getString(key);
    }

    /**
     * Get formatted string with one argument. Prefix with key.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	arg	Argument to the message pattern.
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getKString(String key, Object arg)
	throws MissingResourceException
    {
	return "[" + key + "]: " + getString(key, arg);
    }

    /**
     * Get formatted string with two arguments. Prefix with key.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	arg1	First argument to the message pattern.
     * @param	arg2	Second argument to the message pattern.
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getKString(String key, Object arg1, Object arg2)
	throws MissingResourceException
    {
	return "[" + key + "]: " + getString(key, arg1, arg2);
    }

    public String getKTString(String key, Object arg1, Object arg2)
	throws MissingResourceException
    {
	return "[" + key + "]: "+"["+Thread.currentThread()+"]"+getString(key, arg1, arg2);
    }

    /**
     * Get formatted string with 3 or more arguments. Prefix with key.
     *
     * @param	key	Key that identifies the localized message pattern
     * @param	args	Array of arugments to the message pattern
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getKString(String key, Object[] args)
	throws MissingResourceException
    {
	return "[" + key + "]: " + getString(key, args);
    }

    public String getKTString(String key, Object[] args)
	throws MissingResourceException
    {
	return "[" + key + "]: "+"["+Thread.currentThread()+"]"+getString(key, args);
    }

    /**
     * Get Character. This is used primarily for menu item
     * mnemonics. The mnemonic is stored as a string e.g.
     *          "C"
     * This method fetches the string and returns it's
     * first character.
     *
     * @param   key     Key that identifies the localized message pattern
     * @return  the character corresponding to the passed key.
     * @exception MissingResourceException when the key is not found.
     */
    public char getChar(String key)
        throws MissingResourceException
    {
        String s = getString(key);
        char c;

        try  {
            c = s.charAt(0);
        } catch (Exception e)  {
            c = (char)0;
        }

        return (c);
    }

    /**
     * Get string. Suffix with a colon
     *
     * @param	key	Key that identifies the localized message pattern
     * @return	Formatted string.
     * @exception MissingResourceException when the key is not found.
     */
    public String getCString(String key)
	throws MissingResourceException
    {
	return getString(key) + ":";
    }


    /**
     * Converts the passed string from unix EOL termination to whatever
     * the native platforms eol termination is.
     */
    public static String unix2native(String s) {
        boolean converting = false;
        StringBuffer sb = null;
        String EOL = System.getProperty("line.separator");

        int start = 0;

        if (s == null) return "<null>";

        // If this platforms EOL termination is Unix style just return string
        if (EOL.equals("\n")) return s;

        // Search string for a unix newline
        for (int n = 0; n < s.length(); n++) {
            if (s.charAt(n) == '\n') {
                if (sb == null) {
                    // Defer creation of StrinBuffer until we know we need it
                    sb = new StringBuffer(2 * s.length());
                }
                sb.append(s.substring(start, n));
                sb.append(EOL);
                start = n + 1;
            }
        }

        if (sb != null) {
            // If we had to convert newlines, copy remainder of string
            if (start < s.length()) {
                sb.append(s.substring(start, s.length()));
            }
            return sb.toString();
        } else {
            // No newlines in string. Just return it.
            return s;
        }
    }

    public String toString() {

        StringBuffer sb = new StringBuffer();

        sb.append(this.getClass().getName() + ": convertEOL=" + convertEOL);

        if (convertEOL) {
            if (cache != null) {
                sb.append(" cache=" + cache.toString());
                sb.append("\n");
            }
        }
        sb.append(" resourceBundle=" + rb.toString());

        return sb.toString();
    }

    public Enumeration getKeys() {
        return rb.getKeys();
    }

    public Locale getLocale() {
        return rb.getLocale();
    }

}

