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
 * @(#)Logger.java	1.37 06/29/07
 */ 

package com.sun.messaging.jmq.util.log;

import java.util.Date;
import java.util.Properties;
import java.util.MissingResourceException;
import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.text.MessageFormat;

import com.sun.messaging.jmq.util.LoggerWrapper;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.resources.SharedResources;
import com.sun.messaging.jmq.util.MQResourceBundle;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * Logger is an interface to a set of LogHandlers (logging devices).
 * LogHandlers are added using setLogHandlers(). Once the log handlers
 * are set you log messages to them using the Logger's various
 * log() and logStack() methods. You may specify a resource bundle for
 * Logger to use via the setResourceBundle() method. If a resource
 * bundle is set Logger will attempt to lookup up messages in it using
 * the string passed to the log() method as a key. If the lookup fails
 * then the string is assumed to be the text to log, and not a key.
 *
 * Logger defines six log levels in the following descending order:
 * ERROR, WARNING, INFO, DEBUG, DEBUGMED, DEBUGHIGH. By default
 * Logger will only display INFO messages and above. Logger's 'level'
 * field is public so that code can efficiently compare it
 * if they want to avoid the overhead of a method call. Note that
 * in addition to Logger's log level, each LogHandler may selectively
 * choose which messages to log by specifying a bit-mask for the
 * log levels they want messages for.
 *
 * When a Logger is in a closed() state, any messages logged to it
 * are deferred in a buffer. When the logger is opened the deferred
 * messages are logged.
 */

public class Logger implements LoggerWrapper {

    // Resource bundle to look up logged messages
    private MQResourceBundle rb = null;

    // standard java util logger
    private static java.util.logging.Logger newLogger = null;
    public static final Level EMERGENCY = ForceLogLevel.FORCE;
    public static final String GFLOGFILEKEY = "com.sun.enterprise.server.logging.GFFileHandler.file";
    public static final String JULLOGFILEKEY = "java.util.logging.FileHandler.pattern";
    // Key for System log handler identity prop
    public static final String SYSLOGHANDLRIDENTKEY = "imq.log.syslog.identity";
    // new logger name
    public static final String LOGGERNAME = "imq.log.Logger";
    // List of logging handlers to send messages to
    private LogHandler[] handlers = null;

    // Buffer to hold messages that are logged when Logger is closed
    private Vector deferBuffer = null;
    private boolean closed = false;

    // Full path to "home" directory for Logger. Any relative paths
    // used by logger will be relative to this directory.
    String logHome = null;

    String propPrefix = "";

    // Current logging level
    public int level = INFO;

    /**
     * Log levels. We use bit values so that LogHandlers can selectively
     * choose which level of messages to log.
     */
    public static final int FORCE         = 0x0040; // Should always be greatest
    public static final int ERROR         = 0x0020;
    public static final int WARNING       = 0x0010;
    public static final int INFO          = 0x0008;
    public static final int DEBUG         = 0x0004;
    public static final int DEBUGMED      = 0x0002;
    public static final int DEBUGHIGH     = 0x0001;

    public static final int OFF           = Integer.MAX_VALUE;

    /**
     * A hack. These are the properties that may be dynamically
     * updated. Logger really shouldn't know about handler
     * specific properties.
     */
    private static final String LOGLEVEL_PROP      = ".level";
    private static final String TIMEZONE_PROP      = "log.timezone";
    private static final String ROLLOVERSECS_PROP  = "log.file.rolloversecs";
    private static final String ROLLOVERBYTES_PROP = "java.util.logging.FileHandler.limit";

    // Resource bundle for the Logging code to use to display it's error
    // messages.
    private static SharedResources myrb = SharedResources.getResources();

    // Format of date string prepended to each message
    private SimpleDateFormat df =
	new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss z");

    /**
     * Constructor for Logger.
     *
     * @param logHome	Home directory for this Logger. Any relative paths
     * 		  	used by this Logger will be relative to logHome.
     */
	public Logger(String logHome) {
		this.logHome = logHome;
		this.handlers = new LogHandler[1];
		// Create standard JUL logger for later handoff
		newLogger = java.util.logging.Logger
				.getLogger(LOGGERNAME);
	}

    public void useMilliseconds() {
        df = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss:SS z");
    }

    public int getLevel() {
        return level;
    }

    /**
     * Get the name of the properties that can be dynamically updated.
     * These are prefixed with whatever prefix string was passed to configure()
     */
    public String[] getUpdateableProperties() {

        String[] v = new String[4];

        v[0] = propPrefix + "." + LOGLEVEL_PROP;
        v[1] = propPrefix + "." + ROLLOVERSECS_PROP;
        v[2] = propPrefix + "." + ROLLOVERBYTES_PROP;
        v[3] = propPrefix + "." + TIMEZONE_PROP;

        return v;
    }

    /**
     * Dynamically update a property. 
     */
    public synchronized void updateProperty(String name, String value)
        throws IllegalArgumentException {

        if (name == null || value == null || value.equals("")) {
            return;
        }

        // Skip over <prefix>.
        name = name.substring(propPrefix.length() + 1);

        long n = -1;

        if (name.equals(LOGLEVEL_PROP)) {
	    this.level = levelStrToInt(value);
	    Level julLevel = levelIntToJULLevel(level);
	    newLogger.setLevel(julLevel);
        } else if (name.equals(TIMEZONE_PROP)) {
            if (value.length() > 0) {
                df.setTimeZone(TimeZone.getTimeZone(value));
            }
        } else if (name.equals(ROLLOVERSECS_PROP) ||
                   name.equals(ROLLOVERBYTES_PROP)) {
            // Hack! Logger should not know about these properties
            try {
                n = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    myrb.getString(myrb.W_BAD_NFORMAT, name, value));
            }
            java.util.logging.Handler [] handlers = null;
        	if(newLogger.getUseParentHandlers()) {
        		handlers = newLogger.getParent().getHandlers();
        	} else {
        		handlers = newLogger.getHandlers();
        	}
        	FileHandler flh = null;
        	for(java.util.logging.Handler handler:handlers){
        		if(handler instanceof FileHandler) {
        			flh = (FileHandler)handler;
        			break;
        		}
        	}
        	
            if (flh == null) {
                return;
            }

	    /*
             * As of MQ 3.5, the value that is used to mean 'unlimited'
             * is -1 (although 0 is still supported). To support -1 as
             * an additional way of specifying unlimited, this method will
             * detect if -1 was set for rolloverbytes or rolloversecs
             * (these are the properties that are relevant here), and
             * pass on the value '0' to the method setRolloverLimits().
             *
	     */
	    if (n == -1)  {
	        n = 0L;
	    }

            if (name.equals(ROLLOVERSECS_PROP)) {
                //TODO not supported by jul filehandler flh.setRolloverLimits(-1, n);
            } else {
                // TODO not supported by jul FileHandler to set it programatically. flh.setRolloverLimits(n, -1);
            }
        } else {
            throw new IllegalArgumentException(
                myrb.getString(myrb.X_BAD_PROPERTY, name));
        }
    }

    /**
     * Find the handler with the specified name
    private synchronized LogHandler findHandler(String name) {

        if (handlers == null) {
            return null;
        }

        // Return the handler that matches the passed name
	for (int n = 0; n < handlers.length; n++) {
	    if (handlers[n] != null && name.equals(handlers[n].getName())) {
                return handlers[n];
            }
	}
        return null;
    }
    */

    /**
     * @param inProcess true if caller is running in someone else's JVM 
     * @param jmsraManaged true if caller's lifecycle is managed by JMSRA
     *
     * Configure the logger based on the configuration in the 
     * passed properties. The properties and their syntax is TBD
     */
    public synchronized void configure(Properties props, String prefix, 
                                       boolean inProcess, boolean jmsraManaged,
                                       ServiceLocator habitat) {

        propPrefix = prefix;

	if (props == null) {
	    return;
        }

//	close();

	String property = null, value = null;  
	Level julLevel = null;
	property = prefix+".log."+ "level";
	value = props.getProperty(property);
	if (value != null && !value.trim().equals("")) {
		try {
			this.level = levelStrToInt(value);
			julLevel = levelIntToJULLevel(this.level);
		} catch (IllegalArgumentException e) {
			this.level = INFO;
			julLevel = null;
			this.log(WARNING,
				myrb.getKString(myrb.W_BAD_LOGLEVELSTR, property, value));
		}
	}
    
	property = ".level";
	value = props.getProperty(property);
	if (value != null && !value.trim().equals("")) {
		try {
			julLevel = Level.parse(value);
			this.level = levelJULLevelToInt(julLevel);
		} catch (IllegalArgumentException e) {
			this.level = INFO;
			julLevel = null;
			this.log(WARNING,
				myrb.getKString(myrb.W_BAD_LOGLEVELSTR, property, value));
		}
	}
	if (julLevel != null) {
		newLogger.setLevel(julLevel);
	}	

	// load properties to nucleus when we are not running inside nucleus
	// This is only until we completely moved to nucleus env
	loadPropsToNucleusLogging(props, habitat,inProcess, jmsraManaged);
	// Correct ErrorManager system out with stacktrace to just message.
	java.util.logging.Handler [] handlers = null;
	if(newLogger.getUseParentHandlers()) {
		handlers = newLogger.getParent().getHandlers();
	} else {
		handlers = newLogger.getHandlers();
	}
	
	for(final java.util.logging.Handler handler:handlers){ 
		handler.setErrorManager(
				new ErrorManager(){
					private boolean reported = false;
					
					/**
				     * The error method is called when a Handler failure occurs.
				     * We will override this to print just an error message instead of 
				     * full stacktrace printing.
				     * Main reason: JIRA 145
				     * <p>
				     * This method may be overriden in subclasses.  The default
				     * behavior in this base class is that the first call is
				     * reported to System.err, and subsequent calls are ignored.
				     *
				     * @param msg    a descriptive string (may be null)
				     * @param ex     an exception (may be null)
				     * @param code   an error code defined in ErrorManager
				     */
					@Override
				    public synchronized void error(String msg, Exception ex, int code) {
					if (reported) {
					    // We only report the first error, to avoid clogging
					    // the screen.
					    return;
					}	
					reported = true;
					String text = "java.util.logging.Logger Trouble logging:";
					if (msg != null) {
					    text = text + ": " + msg;
					}
					System.err.println(text+ex.getClass().getName());
//					if (ex != null && handler.getLevel().equals(Level.FINEST)) {
//					    ex.printStackTrace();
//					}
				    }
				}
		);
	}
//	if(newLogger != null) return;
//	
//	String property, value;
//
//	prefix = prefix + ".log.";
//
//	// Get the log level
//	property = prefix + "level";
//	value = props.getProperty(property);
//	if (value != null && !value.equals("")) {
//	    try {
//		this.level = levelStrToInt(value);
//	    } catch (IllegalArgumentException e) {
//		this.log(WARNING,
//		    myrb.getKString(myrb.W_BAD_LOGLEVELSTR, property, value));
//	    }
//	}
//
//	property = prefix + "timezone";
//	value = props.getProperty(property);
//        if (value != null && !value.equals("")) {
//            df.setTimeZone(TimeZone.getTimeZone(value));
//        }
//
//	// Get list of LogHandlers from the properties
//	property = prefix + "handlers";
//	value = props.getProperty(property);
//	if (value == null || value.equals("")) {
//	    this.log(ERROR, myrb.getKString(myrb.E_NO_LOGHANDLERLIST,
//		     property));
//	    return;
//	}
//
//	String handlerName;
//	String handlerClass;
//
//	StringTokenizer token = new StringTokenizer(value, ",", false);
//	int nHandlers = token.countTokens();
//	LogHandler[] handlers = new LogHandler[nHandlers];
//
//	// Loop through each LogHandler and instantiate the class
//	for (int n = 0; n < nHandlers; n++) {
//	    handlerName  = token.nextToken();
//	    property = prefix +  handlerName + ".class";
//	    handlerClass = props.getProperty(property);
//            if (handlerClass == null || handlerClass.equals("")) {
//		this.log(ERROR, myrb.getKString(myrb.E_NO_LOGHANDLER,
//			 property));
//		continue;
//	    }
//
//	    Class t;
//	    try {
//                if (habitat != null) {
//                    handlers[n] = (LogHandler)habitat.getByType(handlerClass);
//                    if (handlers[n] == null) {
//		        t = Class.forName(handlerClass);
//	                handlers[n] = (LogHandler)t.newInstance();
//                    }
//                } else {
//		    t = Class.forName(handlerClass);
//	            handlers[n] = (LogHandler)t.newInstance();
//                }
//	    } catch (ClassNotFoundException e) {
//		this.log(ERROR, myrb.getKString(myrb.E_BAD_LOGHANDLERCLASS,
//					    e.toString()));
//		continue;
//            } catch (IllegalAccessException e) {
//		this.log(ERROR, myrb.getKString(myrb.E_BAD_LOGHANDLERCLASS,
//					    e.toString()));
//		continue;
//            } catch (InstantiationException e) {
//		this.log(ERROR, myrb.getKString(myrb.E_BAD_LOGHANDLERCLASS,
//					    e.toString()));
//		continue;
//	    }
//	    handlers[n].init(this);
//	    handlers[n].setName(handlerName);
//
//	    try {
//	        // Configure LogHandler class based on properties
//	        handlers[n].configure(props, prefix + handlerName);
//	    } catch (IllegalArgumentException e) {
//		this.log(WARNING, myrb.getKString(myrb.W_BAD_LOGCONFIG,
//					e.toString()));
//	    } catch (UnsatisfiedLinkError e) {
//		this.log(WARNING, myrb.getKString(myrb.W_LOGCHANNEL_DISABLED,
//                                          handlerName, e.getMessage()));
//                handlers[n] = null;
//            
//        
//	    } catch (java.lang.NoClassDefFoundError err) {
//	    	this.log(WARNING, myrb.getKString(myrb.W_LOGCHANNEL_DISABLED,
//                    handlerName, err.getMessage()));
//	    	handlers[n] = null;
//	    }
//	}
//
//	try {		
//            setLogHandlers(handlers);
//        } catch (IOException e) {
//	    // Not I18N because this is a programming error
//            System.err.println("Could not set handlers: " + e);
//        }
    }

    /**
	 * Load properties file to nucleus logging framework. This will allow us
	 * to use properties file that is JMS specific instead of nucleus logging.properties
	 ***/
    private void loadPropsToNucleusLogging(Properties props, ServiceLocator habitat,boolean inProcess, boolean jmsraManaged) {
    	// If we are running inside nucleus we will be using properties from nucleus logging.properties
    	if(habitat != null) return;
    	
    	// Following is a workaround to avoid system out message for "Bad level value for property: sun.os.patch.level"
    	final String propToBeRemoved = "sun.os.patch.level";
    	if(props.containsKey(propToBeRemoved)) {
    		props.remove(propToBeRemoved);
    	}
    	// This means let's feed logging framework paths and other logging properties for standalone env
    	// If we don't have glasshfish install root property set formatter does system out "installRoot null" msg 
    	// that breaks some of ourtests as well. This is a workaround only for outside nucleus env case.
    	Properties p = System.getProperties();
    	String installRoot = StringUtil.expandVariables("${imq.instanceshome}${/}${imq.instancename}", props);
        if(!(inProcess && jmsraManaged) && System.getProperty("com.sun.aas.installRoot") == null) {
    	    p.put("com.sun.aas.installRoot", installRoot);
        }
    	String key = JULLOGFILEKEY;
    	String origValue = null;
    	String expandedValue = null;
    	if ((origValue = props.getProperty(key)) != null) {
    		// Expand possible variables like ${jmq.varhome} and update value
    		expandedValue = StringUtil.expandVariables(origValue, props);
    		props.put(key, expandedValue);
    		this.publish(DEBUG, "File logger log file location property key="
    				+ key + ", value=" + expandedValue);
    		// Create log dir as logging framework will fail creating file otherwise
    		File d = new File(expandedValue);
    		if (!d.getParentFile().exists()) {
    			boolean rcode = d.getParentFile().mkdir();

    			if (rcode == false) {
    				publish(ERROR,
    						myrb.getString(myrb.X_DIR_CREATE, d.getParentFile().getAbsolutePath()));
    			}
    		}
    	}
    	key = GFLOGFILEKEY;
    	origValue = null;
    	expandedValue = null;
    	if ((origValue = props.getProperty(key)) != null) {
    		// Expand possible variables like ${jmq.varhome} and update value
    		expandedValue = StringUtil.expandVariables(origValue, props);
    		props.put(key, expandedValue);
    		this.publish(DEBUG, "File logger log file location property key="
    				+ key + ", value=" + expandedValue);
    		// Create log dir as logging framework will fail creating file otherwise
    		File d = new File(expandedValue);
    		if (!d.getParentFile().exists()) {
    			boolean rcode = d.getParentFile().mkdir();

    			if (rcode == false) {
    				publish(ERROR,
    						myrb.getString(myrb.X_DIR_CREATE, d.getParentFile().getAbsolutePath()));
    			}
    		}
    	}
    	key = SYSLOGHANDLRIDENTKEY;
    	origValue = null;
    	expandedValue = null;
    	if ((origValue = props.getProperty(key)) != null) {
    		// Expand possible variables like ${jmq.varhome} and update value
    		expandedValue = StringUtil.expandVariables(origValue, props);
    		props.put(key, expandedValue);
    		this.publish(DEBUG, "System loghandler identity property key="
    				+ key + ", value=" + expandedValue);
    	}

    	ByteArrayOutputStream propsStream = new ByteArrayOutputStream();
    	// If Embeded RA Broker configure properties as added handler for jms logger and do not over load logger properties
    	// To avoid disturbing glassfish/application server logging properties
    	if(inProcess && jmsraManaged) {
    		manuallyConfigureLogging(props);
	    	return;
    	}
    	
    	try {
    		props.store(propsStream, "Loading into stream to pass on to nucleus logging framework");
    		LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(propsStream.toByteArray()));
    	} catch(IOException e) {
    		this.log(ERROR, "Pushing jms properties file to logging framework failed", e);
    	}
    }
    
    /**
     * Instead of using LogManager to configure logging framework do it manually
     * This is mainly due to embeded RA broker case where we do not have luxary 
     * to overwrite application server logging properties that will be overwritten
     * if broker properties are loaded to LogManager
     * @param props broker configuration properties
     */
    private void manuallyConfigureLogging(Properties props) {
    	newLogger.setUseParentHandlers(false);
    	String[] handlerClasses = parseClassNames(props.getProperty("handlers"));
    	
		for (String handlerClass : handlerClasses) {
			Handler newHandler = null;
			try {
                                Class clz = getClass().getClassLoader().loadClass(handlerClass);
				if(clz.equals(FileHandler.class)) {
					String pattern = props.containsKey(handlerClass + ".pattern")?
							props.getProperty(handlerClass + ".pattern"):null;
					int limit = props.containsKey(handlerClass + ".limit")?
							Integer.parseInt(props.getProperty(handlerClass + ".limit")):0; 
					if (limit < 0) {
						limit = 0;
					}
					
					int count = props.containsKey(handlerClass + ".count")?
							Integer.parseInt(props.getProperty(handlerClass + ".count")):1; 
					if (count <= 0) {
						count = 1;
					}		
						        		
					boolean append = props.containsKey(handlerClass + ".append")?
							Boolean.parseBoolean(props.getProperty(handlerClass + ".append")):false; 
					newHandler = new FileHandler(pattern, limit, count, append);
					// Set default formatter to glassfish formatter and not xml 
					newHandler.setFormatter(new com.sun.messaging.jmq.util.log.UniformLogFormatter());
				} else if(clz.equals(SysLogHandler.class)) {
					newHandler = new SysLogHandler(props);
				} else {
					newHandler = (java.util.logging.Handler) clz.newInstance();
				}
				try {
                    // Check if there is a property defining the
                    // this handler's level.
                    String levs = props.getProperty(handlerClass + ".level");
                    if (levs != null) {
                    	newHandler.setLevel(Level.parse(levs));
                    }
                    
                    configureCommonHandler(newHandler, props);
                    
                } catch (Exception ex) {
                    System.err.println("Can't set level for " + handlerClass);
                    // Probably a bad level. Drop through.
                }
				newLogger.addHandler(newHandler);
			} catch (Exception ex) {
				System.err.println("Can't load config class \""
						+ handlerClass + "\"");
				System.err.println("" + ex);
				// ex.printStackTrace();
			}
		}
    }
    
    // Helper method to configure Common LogHandler outside LogManager
    private void configureCommonHandler(Handler handler, Properties props){
    	String baseProp = handler.getClass().getName();
    	String filterStr = props.getProperty(baseProp + ".filter");
    	Filter filter = null;
		try {
			if (filterStr != null) {
				Class clz = ClassLoader.getSystemClassLoader()
						.loadClass(filterStr);
				filter = (Filter) clz.newInstance();
				handler.setFilter(filter);
			}
		} catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
    	
    	String formatterStr = props.getProperty(baseProp + ".formatter");
    	Formatter formatter = getFormatter(formatterStr);
    	if(formatter != null) {
    		handler.setFormatter(formatter);
    	}
    }    
    
    // Get formatter object from string configuration
    private Formatter getFormatter(String formatterString) {
    	Formatter formatter = null;
    	try {
    	    if (formatterString != null) {
    		Class clz = ClassLoader.getSystemClassLoader().loadClass(formatterString);
    		formatter = (Formatter) clz.newInstance();
    	    }
    	} catch (Exception ex) {
    	    // We got one of a variety of exceptions in creating the
    	    // class or creating an instance.
    	    // Drop through.
    	}
    	
    	return formatter;
    }
    
	// get a list of whitespace separated classnames from a property.
	private String[] parseClassNames(String classNamePropertyValue) {
		if (classNamePropertyValue == null) {
			return new String[0];
		}
		classNamePropertyValue = classNamePropertyValue.trim();
		int ix = 0;
		Vector<String> result = new Vector<String>();
		while (ix < classNamePropertyValue.length()) {
			int end = ix;
			while (end < classNamePropertyValue.length()) {
				if (Character.isWhitespace(classNamePropertyValue.charAt(end))) {
					break;
				}
				if (classNamePropertyValue.charAt(end) == ',') {
					break;
				}
				end++;
			}
			String word = classNamePropertyValue.substring(ix, end);
			ix = end + 1;
			word = word.trim();
			if (word.length() == 0) {
				continue;
			}
			result.add(word);
		}
		return result.toArray(new String[result.size()]);
	}
    
    /**
     * Convert a string representation of a log level to its integer value.
     *
     * @param levelStr	Log level string. One of: ERROR, WARNING, INFO, DEBUG
     *                  DEBUGMED, DEBUGHIGH
     */
    public static int levelStrToInt(String levelStr)
	throws IllegalArgumentException {

	if (levelStr.equals("FORCE")) {
	    return Logger.FORCE;
	} else if (levelStr.equals("ERROR")) {
	    return Logger.ERROR;
	} else if (levelStr.equals("WARNING")) {
	    return Logger.WARNING;
	} else if (levelStr.equals("INFO")) {
	    return Logger.INFO;
	} else if (levelStr.equals("DEBUG")) {
	    return Logger.DEBUG;
	} else if (levelStr.equals("DEBUGMED")) {
	    return Logger.DEBUGMED;
	} else if (levelStr.equals("DEBUGHIGH")) {
	    return Logger.DEBUGHIGH;
	} else if (levelStr.equals("NONE")) {
	    return Logger.OFF;
	} else {
	    throw new IllegalArgumentException(
                myrb.getString(myrb.W_BAD_LOGLEVELSTR, levelStr));
	}
    }

    /**
     * Convert an int representation of a log level to its string value.
     *
     * @param level	Log level int. One of: ERROR, WARNING, INFO, DEBUG
     *                  DEBUGMED, DEBUGHIGH
     */
    public static String levelIntToStr(int level)
	throws IllegalArgumentException {

        switch (level) {
        case FORCE:
            return "FORCE";
        case ERROR:
            return "ERROR";
        case WARNING:
            return "WARNING";
        case INFO:
            return "INFO";
        case DEBUG:
            return "DEBUG";
        case DEBUGMED:
            return "DEBUGMED";
        case DEBUGHIGH:
            return "DEBUGHIGH";
        default:
        	final String errmsg = "Conversion from custom log level int " + level + " to String failed";
	    throw new IllegalArgumentException(errmsg);
	}
    }

	/**
	 * Convert an JUL int representation of a log level to its string value.
	 * 
	 * @param level
	 *            Log level int. 
	 *            One of:ALL,FORCE,SEVERE,WARNING,INFO,CONFIG,FINE,FINER,FINEST, OFF
	 */
	public static String jullevelIntToStr(int level)
			throws IllegalArgumentException {

		if (level == Level.ALL.intValue()) {
			return Level.ALL.getName();
		} else if (level == EMERGENCY.intValue()) {
			return EMERGENCY.getName();
		} else if (level == Level.SEVERE.intValue()) {
			return Level.SEVERE.getName();
		} else if (level == Level.WARNING.intValue()) {
			return Level.WARNING.getName();
		} else if (level == Level.INFO.intValue()) {
			return Level.INFO.getName();
		} else if (level == Level.CONFIG.intValue()) {
			return Level.CONFIG.getName();
		} else if (level == Level.FINE.intValue()) {
			return Level.FINE.getName();
		} else if (level == Level.FINER.intValue()) {
			return Level.FINER.getName();
		} else if (level == Level.FINEST.intValue()) {
			return Level.FINEST.getName();
		} else if (level == Level.OFF.intValue()) {
			return Level.OFF.getName();
		} else {
			final String errmsg = "Conversion from JUL log level int " + level
					+ " to String failed";
			throw new IllegalArgumentException(errmsg);
		}
	}    
    
    /**
     * Convert an int representation of a log level to its equivalent java.util.logging.LogLevel.
     *
     * @param level	Log level int. One of: ERROR, WARNING, INFO, DEBUG
     *                  DEBUGMED, DEBUGHIGH
     */
    public static Level levelIntToJULLevel(int level)
    throws IllegalArgumentException {

    	switch (level) {
    	case FORCE:
    		return EMERGENCY;// TODO: Need to wait for nucleus team to add custom log level
    	case ERROR:
    		return Level.SEVERE;
    	case WARNING:
    		return Level.WARNING;
    	case INFO:
    		return Level.INFO;
    	case DEBUG:
    		return Level.FINE;
    	case DEBUGMED:
    		return Level.FINER;
    	case DEBUGHIGH:
    		return Level.FINEST;
    	case OFF:
    		return Level.OFF;
    	default:
    		final String errmsg = "Conversion from " + level + " to JULLevel failed";
    		newLogger.log(java.util.logging.Level.SEVERE, errmsg);
    		throw new IllegalArgumentException(errmsg);
    	}
    }

    /**
     * Convert an java.util.logging.LogLevel representation of a log level to its equivalent int value.
     *
     * @param level	Log level int. One of: ERROR, WARNING, INFO, DEBUG
     *                  DEBUGMED, DEBUGHIGH
     */
    public static int levelJULLevelToInt(Level level)
	throws IllegalArgumentException {

        if(level.equals(EMERGENCY))
            return FORCE;// TODO: Need to wait for nucleus team to add custom log level
        else if(level.equals(Level.SEVERE))
            return ERROR;
        else if(level.equals(Level.WARNING))
            return WARNING;
        else if(level.equals(Level.INFO))
            return INFO;
        else if(level.equals(Level.FINE))
            return DEBUG;
        else if(level.equals(Level.FINER))
            return DEBUGMED;
        else if(level.equals(Level.FINEST))
            return DEBUGHIGH;
        else if(level.equals(Level.OFF))
            return OFF;       
        else {
        	final String errmsg = "Conversion from " + level + " to int failed";
        	newLogger.log(Level.SEVERE, errmsg);
        	throw new IllegalArgumentException(errmsg);
        }
    }
    
    /**
     * Set the resource bundle used to lookup strings logged via
     * the log() and logStack() methods
     *
     * @param rb	The resource bundle used to lookup logged strings.
     */
    public void setResourceBundle(MQResourceBundle rb) {
	this.rb = rb;
    }

    /**
     * Specify the set of LogHandlers to use. The Logger must be in 
     * the closed state to perform this operation. If it isn't this
     * method throws an IOException.
     *
     * @param newhandlers	Array of LogHandlers to use
     *
     * @throws IOException
    private synchronized void setLogHandlers(LogHandler[] newhandlers)
	   throws IOException {

    	if (!closed) {
	    // This is a programming error and is therefore not I18N'd
	    throw new IOException(
		"Logger must be closed before setting handlers");
        }

    	handlers = newhandlers;
		// Set these handlers to standard java util logger
		for (LogHandler mqHandler : handlers) {
			if (mqHandler == null)
				continue;
		}
    }
    */

    /**
     * Close all LogHandlers
     */
    public synchronized void close() {
    	// Close handlers
    	java.util.logging.Handler [] handlers = null;
    	if(newLogger.getUseParentHandlers()) {
    		handlers = newLogger.getParent().getHandlers();
    	} else {
    		handlers = newLogger.getHandlers();
    	}
    	
    	if(handlers != null) {
    		for(final java.util.logging.Handler handler:handlers){ 
    			handler.close();
    		}	
    	}

    	closed = true;
    }

//    We can not reopen JUL log handler once it is closed based on current knowledge
//    /**
//     * Open all LogHandlers. If a handler fails to open and message
//     * is written to System.err and the handler is removed from the 
//     * list of LogHandlers.
//     *
//     * If there are any messages in the defer buffer they are flushed
//     * out after the handlers are opened.
//     */
//    public synchronized void open() {
//
//	// Open handlers
//	for (int n = 0; n < handlers.length; n++) {
//	    if (handlers[n] != null) {
//		try {
//	            handlers[n].open();
//		} catch (IOException e) {
//		    this.log(ERROR, myrb.getKString(myrb.E_BAD_LOGDEVICE,
//				       handlers[n].toString(),
//				       e));
//		    handlers[n] = null;
//		}
//	    }
//        }
//
//	closed = false;
//
//	// Flush any defered messages to handlers
//	flushDeferBuffer();
//    }

    /**
     * Flush all LogHandlers.
     */
    public void flush() {
	// Flush handlers
//	if (handlers != null) {
//	    for (int n = 0; n < handlers.length; n++) {
//		if (handlers[n] != null) {
//		    handlers[n].flush();
//		    handlers[n].getStdHandler().flush();
//		}
//            }
//	}
    }

    /**
     * Publish a formatted message to all LogHandlers. If the Logger is
     * closed, then the message is held in a buffer until the Logger is
     * opened.
     *
     * @param level	Log level. Message will be logged to a LogHandler
     *			if the handler accepts this level of message. If
     *                  level is FORCE then message will be published
     *                  to all LogHandlers unless a handlers has its log
     *                  level set to NONE.
     *
     * @param message	The string to log. It is assumed that this 
     *                  string is already formatted
     *
     */
    public void publish(int level, String message) {

		// If the Logger is closed we defer the message into a buffer
		// until the Logger is opened again
		if (closed) {
			if (newLogger.getHandlers() == null
					|| newLogger.getHandlers().length == 0) {
				defer(level, message);
				return;
			} else {
				flushDeferBuffer();
				closed = false;
			}
		}

	boolean loggedOnce = false;
        LogHandler handler = null;
    
        // Delegate logging calls to new standard JUL logger
        try {
        	newLogger.log(levelIntToJULLevel(level), message);
        } catch(Exception e) {
        	// We got in trouble logging message so let's put it in defer area until it is resolved
        	defer(level, message);
        	closed = true;
        }
        loggedOnce = true;
	// The message has already been formatted. We just need to
	// send it to each LogHandler
//	for (int n = 0; n < handlers.length; n++) {
//            try {
//                handler = handlers[n];
//            } catch (Exception e) {
//                // If list was changed out from under us (unlikely)
//                // just skip handler
//                handler = null;
//            }
//	    // Check if handler wants this level of message
//	    if (handler != null &&
//                handler.levels != 0 &&
//                ((level == FORCE && handler.isAllowForceMessage()) || (level != FORCE && (level & handler.levels) != 0))) {
//		try {
//		    handler.publish(level, message);
//		    loggedOnce = true;
//		} catch (IOException e) {
//		    System.err.println(myrb.getKString(myrb.E_LOGMESSAGE,
//				handler.toString(), e));
//		}
//            }
//	}

	// Don't let message go into black hole.
	if (!loggedOnce) {
	    System.err.println(message);
	}
    }

    /**
     * Defers a message into a buffer until the buffer is flushed
     * to the handlers. When a Logger is closed, any messages logged
     * are defered until the buffer is opened
     */
    private synchronized void defer(int level, String message) {

	if (deferBuffer == null) {
	    deferBuffer = new Vector(32);
        }

	// Add message to buffer. We must preserve the level too.
	LogRecord dr = new LogRecord(level, message);
	deferBuffer.addElement((Object)dr);
    }

    /**
     * Flushes deferred messages out of buffer and releases the buffer
     */
    private synchronized void flushDeferBuffer() {

	if (deferBuffer == null) {
            return;
        }

	// Publish all messages in buffer
	LogRecord df = null;
        for (Enumeration e = deferBuffer.elements(); e.hasMoreElements(); ) {
	    df = (LogRecord)e.nextElement();
	    publish(df.level, df.message);
        }

	// Clear buffer and discard it
	deferBuffer.clear();
	deferBuffer = null;
    }

    /**
     * Format a log message.
     *
     * @param level	Log level. Used to generate a possible prefix
     *			like "ERROR" or "WARNING"
     *
     * @param key	Key to use to find message in resource bundle.
     *                  If message is not localized then the key is just
     *                  the raw message to display.
     *
     * @param args	Optional args to message. Null if no args
     *
     * @param ex	Optional throwable to display description of. Null
     *			if no throwable.
     *
     * @param printStack True to print stack trace of throwable. False to
     *			 not print a stack trace. If True "ex" must not be
     *			 null.
     *
     */
    public String format(
	int level,
	String key,
	Object[] args,
	Throwable ex,
	boolean printStack) {

	StringBuffer sb = new StringBuffer(80);

//	// Append date and time
//	sb.append("[");
//	sb.append(df.format(new Date()));
//	sb.append("] ");

	// Append prefix if any
	switch (level) {
	case ERROR:   sb.append(myrb.getString(myrb.M_ERROR)); break;
	case WARNING: sb.append(myrb.getString(myrb.M_WARNING)); break;
	default: break;
	}

	// Get message from resource bundle.
	String message = null;

	if (key == null) key = "";

	if (rb != null) {
	    try {
	        if (args == null) {
		    message = rb.getKString(key);
	        } else {
		    message = rb.getKString(key, args);
	        }
            } catch (MissingResourceException e) {
	        // Message is not localized.
	        message = null;
            }
        }

	// If message was not localized use key as message
	if (message == null) {
	    if (args == null) {
	        message = key;
	    } else {
		message = MessageFormat.format(key, args);
	    }
        }

	sb.append(message);

	// If there is a throwable, append its description
	if (ex != null) {

	    sb.append(":" + myrb.NL);

	    // If requested, append full stack trace
	    if (printStack) {
		// No way to get trace it as a String so
		// we use a ByteArrayOutputStream to capture it.
		ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
		PrintWriter pw = new PrintWriter(bos);
		ex.printStackTrace(pw);
		pw.flush();
		pw.close();
		sb.append(bos.toString());
		try {
		    bos.close();
		} catch (IOException e) {
		}
	    } else {
		// Just append description
	        sb.append(ex + myrb.NL);
	    }
        } else {
	    sb.append(myrb.NL);
        }

	return sb.toString();
    }

    /**
     ************************************************************************
     * The following are variations of the base log() and logStack() methods.
     * log() logs a message, logStack() includes the stack backtrace of a
     * passed Throwable. Since these routines may perform the resource bundle
     * lookup, we provide variations to pass arguments for the message
     * string. That gives use 12 variations: 4 each of log,
     * log with Throwable, and logStack with Throwable.
     ***********************************************************************/

    /**
     * Log a message
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     */
    public void log(int level, String msg) {
	if (level < this.level) {
            return;
        }
	publish(level, format(level, msg, null, null, false));
    }

    /**
     * Log a message with one argument
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg	The argument to substitute into msg.
     */
    public void log(int level, String msg, Object arg) {
	if (level < this.level) {
            return;
        }

	// the following check is needed because when the
	// third argument is of type Object[], this method
	// is called instead of log(level, String, Object[])
	if (arg instanceof Object[]) {
	    publish(level, format(level, msg, (Object[])arg, null, false));
	} else {
	    Object[] args = {arg};
	    publish(level, format(level, msg, args, null, false));
        }
    }

    /**
     * Log a message with two arguments
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg1	The first argument to substitute into msg.
     *
     * @param arg2	The second argument to substitute into msg.
     */
    public void log(int level, String msg, Object arg1, Object arg2) {
	if (level < this.level) {
            return;
        }
	Object[] args = {arg1, arg2};
	publish(level, format(level, msg, args, null, false));
    }

    /**
     * Log a message with an array of arguments
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param args	Array of arguments to substitute into msg.
     */
    public void log(int level, String msg, Object[] args) {
	if (level < this.level) {
            return;
        }
	publish(level, format(level, msg, args, null, false));
    }

    /**
     * Log a message and a throwable
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param ex	Throwable to log description of.
     */
    public void log(int level, String msg, Throwable ex) {
	if (level < this.level) {
            return;
        }

        // If we are at a DEBUG level, log throwable stack
        boolean logStack = (this.level <= DEBUG);
        publish(level, format(level, msg, null, ex, logStack));
    }

    /**
     * Log a message with one argument and a throwable
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg	The argument to substitute into msg.
     *
     * @param ex	Throwable to log description of.
     */
    public void log(int level, String msg, Object arg, Throwable ex) {
	if (level < this.level) {
            return;
        }

        // If we are at a DEBUG level, log throwable stack
        boolean logStack = (this.level <= DEBUG);

	// the following check is needed because when the
	// third argument is of type Object[], this method
	// is called instead of log(level, String, Object[])
	if (arg instanceof Object[]) {
	    publish(level, format(level, msg, (Object[])arg, ex, logStack));
	} else {
	    Object[] args = {arg};
	    publish(level, format(level, msg, args, ex, logStack));
        }
    }

    /**
     * Log a message with two arguments and a throwable
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg1	The first argument to substitute into msg.
     *
     * @param arg2	The second argument to substitute into msg.
     *
     * @param ex	Throwable to log description of.
     */
    public void log(int level, String msg, Object arg1, Object arg2, 
		    Throwable ex) {
	if (level < this.level) {
            return;
        }
        // If we are at a DEBUG level, log throwable stack
        boolean logStack = (this.level <= DEBUG);
	Object[] args = {arg1, arg2};
	publish(level, format(level, msg, args, ex, logStack));
    }

    /**
     * Log a message with an array of arguments and a throwable
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param args	Array of arguments to substitute into msg.
     */
    public void log(int level, String msg, Object[] args, Throwable ex) {
	if (level < this.level) {
            return;
        }
        // If we are at a DEBUG level, log throwable stack
        boolean logStack = (this.level <= DEBUG);
	publish(level, format(level, msg, args, ex, logStack));
    }

    /**
     * Log a message and a throwable. Include stack backtrace.
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param ex	Throwable to log description of.
     */
    public void logStack(int level, String msg, Throwable ex) {
	if (level < this.level) {
            return;
        }
	publish(level, format(level, msg, null, ex, true));
    }

    /**
     * Log a message with one argument and a throwable.
     * Include stack backtrace.
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg	The argument to substitute into msg.
     *
     * @param ex	Throwable to log description of.
     */
    public void logStack(int level, String msg, Object arg, Throwable ex) {
	if (level < this.level) {
            return;
        }

	// the following check is needed because when the
	// third argument is of type Object[], this method
	// is called instead of logStack(level, String, Object[])
	if (arg instanceof Object[]) {
	    publish(level, format(level, msg, (Object[])arg, ex, true));
	} else {
	    Object[] args = {arg};
	    publish(level, format(level, msg, args, ex, true));
        }
    }

    /**
     * Log a message with two arguments and a throwable
     * Include stack backtrace.
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg1	The first argument to substitute into msg.
     *
     * @param arg2	The second argument to substitute into msg.
     *
     * @param ex	Throwable to log description of.
     */
    public void logStack(int level, String msg, Object arg1, Object arg2, 
		    Throwable ex) {
	if (level < this.level) {
            return;
        }
	Object[] args = {arg1, arg2};
	publish(level, format(level, msg, args, ex, true));
    }

    /**
     * Log a message with an array of arguments and a throwable
     * Include stack backtrace.
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param args	Array of arguments to substitute into msg.
     */
    public void logStack(int level, String msg, Object[] args, Throwable ex) {
	if (level < this.level) {
            return;
        }
	publish(level, format(level, msg, args, ex, true));
    }


    /**
     * Log a message with arguments and force it to appear to all handlers
     * that do not have their log level set to NONE.
     *
     * @param level	Log level. Used to format the message (ie put
     *                  "ERROR" in front of errors). Message will go to
     *                  all log handlers not matter what this level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param args	Array of arguments to substitute into msg.
     */
    public void logToAll(int level, String msg, Object[] args) {
	publish(FORCE, format(level, msg, args, null, false));
    }

    /**
     * Log a message with arguments and force it to appear to all handlers
     * that do not have their log level set to NONE.
     *
     * @param level	Log level. Used to format the message (ie put
     *                  "ERROR" in front of errors). Message will go to
     *                  all log handlers not matter what this level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     */
    public void logToAll(int level, String msg) {
	publish(FORCE, format(level, msg, null, null, false));
    }

    /**
     * Log a message with arguments and force it to appear to all handlers
     * that do not have their log level set to NONE.
     *
     * @param level	Log level. Used to format the message (ie put
     *                  "ERROR" in front of errors). Message will go to
     *                  all log handlers not matter what this level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg1	The first argument to substitute into msg.
     *
     * @param arg2	The second argument to substitute into msg.
     */
    public void logToAll(int level, String msg, Object arg1, Object arg2) {
	Object[] args = {arg1, arg2};
	publish(FORCE, format(level, msg, args, null, false));
    }

    /**
     * Log a message with one argument and force it to appear to all handlers
     * that do not have their log level set to NONE.
     *
     * @param level	Log level. Message will be logged if this level
     *			is greater or equal to the current log level.
     *
     * @param msg	The message to log. This will first be tried
     *			as a key to look up the localized string in the
     *			current resource bundle. If that fails then the
     *			msg String will be used directly.
     *
     * @param arg	The argument to substitute into msg.
     */
    public void logToAll(int level, String msg, Object arg) {
	// the following check is needed because when the
	// third argument is of type Object[], this method
	// is called instead of log(level, String, Object[])
	if (arg instanceof Object[]) {
	    publish(FORCE, format(level, msg, (Object[])arg, null, false));
	} else {
	    Object[] args = {arg};
	    publish(FORCE, format(level, msg, args, null, false));
        }
    }

    /******************************************************************
     * implement LoggerWrapper interface
     *******************************************************************/

    public void logInfo(String msg, Throwable t) {
        if (t == null) {
            log(INFO, msg);
        } else {
            logStack(INFO, msg, t);
        }
    }

    public void logWarn(String msg, Throwable t) {
        if (t == null) {
            log(WARNING, msg);
        } else {
            logStack(WARNING, msg, t);
        }
    }

    public void logSevere(String msg, Throwable t) {
        if (t == null) {
            log(ERROR, msg);
        } else {
            logStack(ERROR, msg, t);
        }
    }

    public void logFine(String msg, Throwable t) {
        if (t == null) {
            log(DEBUG, msg);
        } else {
            logStack(DEBUG, msg, t);
        }
    }

    public void logFinest(String msg, Throwable t) {
        if (t == null) {
            log(DEBUGHIGH, msg);
        } else {
            logStack(DEBUGHIGH, msg, t);
        }
    }

    public boolean isFineLoggable() {
        return (getLevel() <= Logger.DEBUG);
    }

    public boolean isFinestLoggable() {
        return (getLevel() <= Logger.DEBUGHIGH);
    }
}

class JMSLogManager extends LogManager {
	private static JMSLogManager manager = new JMSLogManager();;
	protected JMSLogManager(){
		super();
	}
	
	/**
     * Return the global LogManager object.
     */
    public static LogManager getLogManager() {
        return manager;
    }
}

class LogRecord {
    public int level;
    public String message;

    public LogRecord(int level, String message) {
	this.level = level;
	this.message = message;
    }
}

final class ForceLogLevel extends Level {
	private static String defaultBundle = "sun.util.logging.resources.logging";
	public static final Level FORCE = new ForceLogLevel("FORCE", 1100, defaultBundle);
	
	protected ForceLogLevel(String name, int value,
			String resourceBundleName) {
		super(name, value, resourceBundleName);
	}

	protected ForceLogLevel(String name, int value) {
		super(name, value);
	}
}

