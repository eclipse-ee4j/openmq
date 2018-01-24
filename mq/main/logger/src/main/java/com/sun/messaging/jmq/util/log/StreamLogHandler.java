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
 * @(#)StreamLogHandler.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.util.log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import com.sun.messaging.jmq.util.log.RollingFileOutputStream;

/**
 * A LogHandler that is implemented as a simple OutputStream
 * (For example System.err)
 */
public class StreamLogHandler extends LogHandler {

    private OutputStream os = null;

    public StreamLogHandler() {
    }

    /**
     * Configure the StreamLogHandler with the values contained in
     * the passed Properties object. This handler's properties are
     * prefixed with the specified prefix.
     * <P>
     * An example of valid properties are:
     * <PRE>
     * jmq.log.console.stream=ERR
     * jmq.log.console.output=ERROR|WARNING|INFO
     * </PRE>
     * In this case prefix would be "jmq.log.stream"
     *
     * @param props	Properties to get configuration information from
     * @param prefix	String that this handler's properties are prefixed with
     *
     * @throws IllegalArgumentException if one or more property values are
     *                                  invalid. All valid properties will
     *					still be set.
     */
    public void configure(Properties props, String prefix)
	throws IllegalArgumentException {

	String value = null;
	String property = null;
	String error_msg = null;
	long   bytes = 0L, secs = 0L;;

	prefix = prefix + ".";

	property = prefix + "stream";
	if ((value = props.getProperty(property)) != null) {
	    if (value.equals("ERR")) {
		setLogStream(System.err);
	    } else if (value.equals("OUT")) {
		setLogStream(System.out);
            } else {
	        error_msg = rb.getString(rb.W_BAD_LOGSTREAM, property, value);
            }
	}

	property = prefix + "output"; 
	if ((value = props.getProperty(property)) != null) {
	    try {
	        setLevels(value);
	    } catch (IllegalArgumentException e) {
	        error_msg = (error_msg != null ? error_msg + rb.NL : "") +
			property + ": " + e.getMessage();
	    }
        } 

	if (error_msg != null) {
	    throw new IllegalArgumentException(error_msg);

	}
    }

    public void setLogStream(OutputStream os) {
	close();
	this.os = os;
    }

    /**
     * Publish string to log
     *
     * @param level	Log level to use
     * @param message	Message to write to log file
     *
     */
	public void publish(int level, String message) throws IOException {

		// ignore FORCE messages if we have explicitly been asked to ignore them
		if (level == Logger.FORCE && !isAllowForceMessage()) {
			return;
		}

		if (os != null) {
			os.write(message.getBytes());
		}
	}

    /**
     * Open handler. This is a no-op. It is assumed the stream is already
     * opened.
     */
    public void open() throws IOException {
	return;
    }

    /**
     * Close handler. This just flushes the output stream.
     */
    public void close() {
	if (os != null) {
	    try {
	        os.flush();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * This just flushes the output stream.
     */
    public void flush() {
        if (os != null) {
            try {
                os.flush();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Return a string description of this FileHandler. The descirption
     * is the class name followed by the path of the file we are logging to.
     */
    public String toString() {
	return this.getClass().getName() + ":" + os.toString();
    }
}
