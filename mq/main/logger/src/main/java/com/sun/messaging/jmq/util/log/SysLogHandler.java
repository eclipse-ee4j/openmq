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
 * @(#)SysLogHandler.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util.log;

import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import com.sun.messaging.jmq.resources.SharedResources;

/**
 * A LogHandler that logs to Unix syslog
 */
public class SysLogHandler extends Handler {

    /*
     * Apply to all instances of a SysLogHandler. This is due to
     * the way syslog works. It is designed for a process to have
     * one connection to syslogd.
     */
    static String ident = "SysLogHandler";
    static int     facility = SysLog.LOG_DAEMON;
    static int     logopt = (SysLog.LOG_PID | SysLog.LOG_CONS);

    private static final String PREFIX = "imq.log.syslog.";
    public static final String FACILITY = "facility";
    public static final String LOGPID = "logpid";
    public static final String LOGCONSOLE = "logconsole";
    public static final String IDENTITY = "identity";
    public static final String OUTPUT = "output";
    
    static boolean open = false;
    /**
     * Whether messages of the level Level.FORCE should be sent to this handler
     * This handler always ignores FORCE messages
     */
    private boolean allowForceMessage=false;

    public SysLogHandler() {
    	try {
			LogManager lm = LogManager.getLogManager();

			String property, facilityStr, logpidStr, logconsoleStr, identityStr, outputStr;

			property = PREFIX + FACILITY;
			facilityStr = lm.getProperty(property);
			property = PREFIX + LOGPID;
			logpidStr = lm.getProperty(property);
			property = PREFIX + LOGCONSOLE;
			logconsoleStr = lm.getProperty(property);
			property = PREFIX + IDENTITY;
			identityStr = lm.getProperty(property);
			property = PREFIX + OUTPUT;
			outputStr = lm.getProperty(property);
			
			configure(facilityStr, logpidStr, logconsoleStr, identityStr,outputStr);   
			
    	} catch (UnsatisfiedLinkError e) {
    		java.util.logging.Logger logger =
    		java.util.logging.Logger.getLogger(Logger.LOGGERNAME);
    		logger.log(Level.WARNING, SharedResources.getResources()
    				.getKString(SharedResources.W_LOGCHANNEL_DISABLED,
                  this.getClass().getName(), e.getMessage()));
    		open = false;
    	} catch (NoClassDefFoundError e) {
    		java.util.logging.Logger logger =
    		java.util.logging.Logger.getLogger(Logger.LOGGERNAME);
    		logger.log(Level.WARNING, SharedResources.getResources()
    				.getKString(SharedResources.W_LOGCHANNEL_DISABLED,
                  this.getClass().getName(), e.getMessage()));
    		open = false;
    	}
    }

    public SysLogHandler(Properties props) {
    	try {
    		String property, facilityStr, logpidStr, logconsoleStr, identityStr, outputStr;
    		property = PREFIX + FACILITY;
    		facilityStr = props.getProperty(property);
	    	property = PREFIX + LOGPID;
	    	logpidStr = props.getProperty(property);
	    	property = PREFIX + LOGCONSOLE;
	    	logconsoleStr = props.getProperty(property);
	    	property = PREFIX + IDENTITY;
	    	identityStr = props.getProperty(property);
	    	property = PREFIX + OUTPUT;
	    	outputStr = props.getProperty(property);
	    	
	    	configure(facilityStr, logpidStr, logconsoleStr, identityStr,outputStr);
	    	
    	} catch (UnsatisfiedLinkError e) {
    		java.util.logging.Logger logger =
    		java.util.logging.Logger.getLogger(Logger.LOGGERNAME);
    		logger.log(Level.WARNING, SharedResources.getResources()
    				.getKString(SharedResources.W_LOGCHANNEL_DISABLED,
                  this.getClass().getName(), e.getMessage()));
    		open = false;
    	} catch (NoClassDefFoundError e) {
    		java.util.logging.Logger logger =
    		java.util.logging.Logger.getLogger(Logger.LOGGERNAME);
    		logger.log(Level.WARNING, SharedResources.getResources()
    				.getKString(SharedResources.W_LOGCHANNEL_DISABLED,
                  this.getClass().getName(), e.getMessage()));
    		open = false;
    	}
    }
    
    public static void setFacility(int f) {
        facility = f;
    }

    
    /**
     * Configure SysLogHandler with the values contained in
     * the passed Properties for logging. Note that all SysLogHandlers
     * will be affected, not just this instance. The handler's properties are
     * prefixed with the specified prefix.
     * <P>
     * An example of valid properties are:
     * <PRE>
     * imq.log.syslog.facility=LOG_DAEMON
     * imq.log.syslog.logpid=true
     * imq.log.syslog.logconsole=false
     * imq.log.syslog.identity=imqbrokerd_imqbroker
     * </PRE>
     * In this case prefix would be "imq.log.syslog"
     *
     * @throws IllegalArgumentException if one or more property values are
     *                                  invalid. All valid properties will
     *					still be set.
     * @throws UnsatisfiedLinkError     if native code can't be loaded
     */
	public void configure(String facilityStr, String logpidStr,
			String logconsoleStr, String identityStr, String outputStr)
			throws IllegalArgumentException, UnsatisfiedLinkError {

	String error_msg = null;
	//LogManager lm = LogManager.getLogManager();
	
        synchronized (SysLogHandler.class) {

	if (facilityStr != null) {
	    try {
		//facility = parseFacility(value);
		setFacility(SysLog.LOG_DAEMON);
            } catch (Exception e) {
	        //error_msg = rb.getString(rb.W_BAD_NFORMAT, property, value);
	        error_msg = "Bad syslog facility " + PREFIX + FACILITY + "=" + facilityStr;
            }
	}

	if (logpidStr != null) {
            if (logpidStr.equalsIgnoreCase("true")) {
                logopt = logopt | SysLog.LOG_PID;
            }
	}

	if (logconsoleStr != null) {
            if (logconsoleStr.equalsIgnoreCase("true")) {
                logopt = logopt | SysLog.LOG_CONS;
            }
	}

	ident= identityStr;
    // Note: already resolved in logger old:ident = StringUtil.expandVariables(value, lm);

	if (outputStr != null) {
	    try {
	    	int configLevel = Logger.levelStrToInt(outputStr);
	    	Level levelSetByConfig = Logger.levelIntToJULLevel(configLevel);
	        this.setLevel(levelSetByConfig);
	    } catch (IllegalArgumentException e) {
	        error_msg = (error_msg != null ? error_msg + "\n" : "") +
	        PREFIX + OUTPUT + ": " + e.getMessage();
	    }
        } 

        if (open) {
            this.close();
        }
        // Causes prop changes to take effect and forces link error
        this.open();

        }
    }
    
    /**
     * Open handler
     */
    public void open() {
       
        synchronized(SysLogHandler.class) {
            if (!open) {
                SysLog.openlog(ident, logopt, facility);
                open = true;
            }
        }
    }

    /**
     * Close handler
     */
    @Override
    public void close() {
        synchronized(SysLogHandler.class) {
            if (open) {
                SysLog.closelog();
                open = false;
            }
        }
    }

    /**
     * Return a string description of this FileHandler. The descirption
     * is the class name followed by the path of the file we are logging to.
     */
    public String toString() {
	return this.getClass().getName() + ":" + ident;
    }

	@Override
	public void publish(LogRecord record) {
		// If log handler is not configured due to non solaris platform or other reasons
		// simply return.
		if(!open) return;
		
        int priority = SysLog.LOG_INFO;
        
		// ignore FORCE messages if we have explicitly been asked to ignore them
        // (note that isAllowForceMessage() will always return false for this class)
		if (Logger.levelJULLevelToInt(record.getLevel()) == Logger.FORCE
				&& !allowForceMessage) {
			return;
		}

		if (!isLoggable(record)) {
			return;
		}
		
		// TODO: Now with new logger format can change based on formatter used in logging 
		// configuration so we will leave time stamp from original logger message.
        /* Syslog adds its own timestamp, strip ours */
//        if (message.indexOf("[") == 0) {
//            message = message.substring(message.indexOf("]") + 1);
//        }

        // Map level to a syslog level
        switch (Logger.levelJULLevelToInt(record.getLevel())) {
            case Logger.ERROR:   priority = SysLog.LOG_ERR; break;
            case Logger.WARNING: priority = SysLog.LOG_WARNING; break;
            case Logger.INFO:    priority = SysLog.LOG_INFO; break;
            case Logger.DEBUG:   
            case Logger.DEBUGMED:
            case Logger.DEBUGHIGH: priority = SysLog.LOG_DEBUG; break;
        }

        SysLog.syslog(priority, record.getMessage());
	}

	@Override
	public void flush() {
		// Nothing to do.
	}
}
