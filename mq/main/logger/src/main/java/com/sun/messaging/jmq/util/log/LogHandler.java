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
 * @(#)LogHandler.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.util.log;

import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Properties;
import com.sun.messaging.jmq.resources.SharedResources;
import org.jvnet.hk2.annotations.Contract;
import org.glassfish.hk2.api.PerLookup;


/**
 * Abstract class defining interface for a LogHandler. A LogHandler is
 * used by a Logger to publish log message to a logging device. A 
 * LogHandler could be implemented as a set of rolling files, or a 
 * simple output stream.
 */

@Contract
@PerLookup
public abstract class LogHandler {

    /**
     * The levels of messages this handler wants to accept.
     * Log levels are described in the Logger class.
     */
    public int levels = Logger.INFO | Logger.WARNING | Logger.ERROR |
                        Logger.FORCE;

    /* Our parent Logger */
    protected Logger logger = null;

    protected static final SharedResources rb = SharedResources.getResources();

    protected String name = null;
    
    /**
     * Whether messages of the level Level.FORCE should be sent to this handler
     */
    private boolean allowForceMessage=true;

    /**
     * Return whether messages of the level Level.FORCE may be sent to this handler
     * @return
     */
    public boolean isAllowForceMessage() {
		return allowForceMessage;
	}

    /**
     * Specify whether messages of the level Level.FORCE may be sent to this handler
     * @param allowForceMessage
     */
	protected void setAllowForceMessage(boolean allowForceMessage) {
		this.allowForceMessage = allowForceMessage;
	}

	/**
     * Convenience routine to have handler accept messages of all levels
     */
    public void acceptAllLevels() {
	levels = Logger.FORCE    |
                 Logger.ERROR	 |
    		 Logger.WARNING  |
    		 Logger.INFO     |
    		 Logger.DEBUG    |
    		 Logger.DEBUGMED |
    		 Logger.DEBUGHIGH;
    }

    /**
     * Set the log levels this handler will handle based on a String 
     * description. This is useful for setting the levels from a
     * property string.
     * 
     * @param levelList		A | seperated list of log levels this
     *				handler will accept. Valid values are
     *				"ALL", "NONE", "NOFORCE" or a list of one or more of
     *				ERROR, WARNING, INFO, DEBUG, DEBUGMED,
     *				and DEBUGHIGH
     *
     *	"NOFORCE" specifies that FORCE messages should not be sent to this handler;
     *   any that are sent to this handler will be ignored. You should typically
     *   use this in conjunction with a list of log levels that are accepted.
     */
	protected void setLevels(String levelList) throws IllegalArgumentException {

		String s;
		levels = 0;

		// All handlers will by default accept forced messages (override with NONE or NOFORCE)
		levels = Logger.FORCE;

		// Parse string and initialize levels bitmask.
		StringTokenizer token = new StringTokenizer(levelList, "|", false);
		while (token.hasMoreElements()) {
			s = token.nextToken();
			if (s.equals("ALL")) {
				acceptAllLevels();
				break;
			} else if (s.equals("NONE")) {
				levels = 0;
				break;
			} else if (s.equals("NOFORCE")) {
				setAllowForceMessage(false);
			} else {
				levels |= Logger.levelStrToInt(s);
			}
		}

	}

    /**
     * Perform basic initialization of the LogHandler
     *
     * @param parent	Logger parent of this LogHandler
     */
    public void init(Logger parent) {
	this.logger = parent;
        acceptAllLevels();
    }

    /**
     * Set the name of this handler
     *
     * @param name  Handler name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the name of this handler
     */
    public String getName() {
        return this.name;
    }

    /**
     * Flush handler
     */
    public void flush() {
        // No-op
    }

    abstract public void configure(Properties props, String prefix)
	throws IllegalArgumentException;

    /**
     * Publish string to log
     *
     * @param level	The log level
     * @param message	The message to publish to loggin device.
     */
    abstract public void publish(int level, String message) throws IOException;

    /**
     * Open handler
     */
    abstract public void open() throws IOException;

    /**
     * Close handler
     */
    abstract public void close();
    
}
