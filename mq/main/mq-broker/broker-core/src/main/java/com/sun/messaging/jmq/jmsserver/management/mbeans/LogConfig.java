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
 * @(#)LogConfig.java	1.13 06/28/07
 */

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Date;
import java.util.logging.FileHandler;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.util.log.FileLogHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;

import com.sun.messaging.jmq.jmsserver.management.util.LogUtil;

public class LogConfig extends MQMBeanReadWrite
					implements ConfigListener  {
    private Properties brokerProps = null;
    private static final String ROLLOVER_BYTES_PROP = "java.util.logging.FileHandler.limit";
    private static final String LOGLEVEL_PROP =".level";
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(LogAttributes.LEVEL,
					String.class.getName(),
					mbr.getString(mbr.I_LOG_ATTR_LEVEL),
					true,
					true,
					false),

	    new MBeanAttributeInfo(LogAttributes.ROLL_OVER_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_LOG_ATTR_ROLL_OVER_BYTES),
					true,
					true,
					false),

	    new MBeanAttributeInfo(LogAttributes.ROLL_OVER_SECS,
					Long.class.getName(),
					mbr.getString(mbr.I_LOG_ATTR_ROLL_OVER_SECS),
					true,
					true,
					false),

		new MBeanAttributeInfo(LogAttributes.LOG_DIRECTORY,
					String.class.getName(),
					mbr.getString(mbr.I_LOG_ATTR_LOG_DIRECTORY),
					true,
					false,
					false),

		new MBeanAttributeInfo(LogAttributes.LOG_FILE_NAME,
					String.class.getName(),
					mbr.getString(mbr.I_LOG_ATTR_LOG_FILE_NAME),
					true,
					false,
					false),
			};

    private static String[] attrChangeTypes = {
		    AttributeChangeNotification.ATTRIBUTE_CHANGE
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    attrChangeTypes,
		    AttributeChangeNotification.class.getName(),
	            mbr.getString(mbr.I_ATTR_CHANGE_NOTIFICATION)
		    )
		};

    public LogConfig()  {
	super();
	initProps();

	com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	cfg.addListener(LOGLEVEL_PROP, this);
	cfg.addListener(ROLLOVER_BYTES_PROP, this);
	cfg.addListener("imq.log.file.rolloversecs", this);
    }

    public void setLevel(String s) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty(LOGLEVEL_PROP,
		LogUtil.toInternalLogLevel(s));

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(LogAttributes.LEVEL, e);
	}
    }
    public String getLevel()  {
	String s = brokerProps.getProperty(LOGLEVEL_PROP);

	return(LogUtil.toExternalLogLevel(s));
    }

    public void setRolloverBytes(Long l) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty(ROLLOVER_BYTES_PROP, l.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(LogAttributes.ROLL_OVER_BYTES, e);
	}
    }
    public Long getRolloverBytes() throws MBeanException  {
	String s = brokerProps.getProperty(ROLLOVER_BYTES_PROP);
	Long l = null;

	try  {
	    l = new Long(s);
	} catch (Exception e)  {
	    handleGetterException(LogAttributes.ROLL_OVER_BYTES, e);
	}

	return (l);
    }

    public void setRolloverSecs(Long l) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.log.file.rolloversecs", l.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(LogAttributes.ROLL_OVER_SECS, e);
	}
    }
    public Long getRolloverSecs() throws MBeanException  {
	String s = brokerProps.getProperty(Globals.IMQ + ".log.file.rolloversecs");
	Long l = null;

	try  {
		if(s !=null && !s.trim().equals("")){
			l = new Long(s);
		} else {
			l = 0l;
		}
	} catch (Exception e)  {
	    handleGetterException(LogAttributes.ROLL_OVER_SECS, e);
	}

	return (l);
    }

    public String getLogDirectory() throws MBeanException {
    	return getLogFile().getParent();
    }

    public String getLogFileName() throws MBeanException {
    	return getLogFile().getName();
    }

    public String getMBeanName()  {
	return ("LogConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_LOG_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (null);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void validate(String name, String value)
            throws PropertyUpdateException {
    }

    public boolean update(String name, String value) {
	Object newVal, oldVal;

	/*
        System.err.println("### cl.update called: "
            + name
            + "="
            + value);
	*/

	if (name.equals(LOGLEVEL_PROP))  {
	    newVal = LogUtil.toExternalLogLevel(value);
	    oldVal = getLevel();
            notifyAttrChange(LogAttributes.LEVEL,
				newVal, oldVal);
	} else if (name.equals(ROLLOVER_BYTES_PROP))  {
	    try  {
	        newVal = Long.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + LogAttributes.ROLL_OVER_BYTES
		    + ": "
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getRolloverBytes();
	    } catch(Exception e)  {
		logProblemGettingOldVal(LogAttributes.ROLL_OVER_BYTES, e);
	        oldVal = null;
	    }

            notifyAttrChange(LogAttributes.ROLL_OVER_BYTES,
				newVal, oldVal);
	} else if (name.equals("imq.log.file.rolloversecs"))  {
	    try  {
	        newVal = Long.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + LogAttributes.ROLL_OVER_SECS
		    + ": "
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getRolloverSecs();
	    } catch(Exception e)  {
		logProblemGettingOldVal(LogAttributes.ROLL_OVER_SECS, e);
	        oldVal = null;
	    }

            notifyAttrChange(LogAttributes.ROLL_OVER_SECS,
				newVal, oldVal);
	}

        initProps();
        return true;
    }

    public void notifyAttrChange(String attrName, Object newVal, Object oldVal)  {
	sendNotification(
	    new AttributeChangeNotification(this, sequenceNumber++, new Date().getTime(),
	        "Attribute change", attrName, (newVal == null ? "" : newVal.getClass().getName()),
	        oldVal, newVal));
    }

    private void initProps() {
	brokerProps = Globals.getConfig().toProperties();
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());
    }

    /**
     * Read user configured log handler .pattern config string from config property
     * and generate File object from it after resolving pattern
     * @return File object for log file
     */
    private File getLogFile() throws MBeanException {
    	// Generate a log file from pattern.
 		try {
 			String pattern = brokerProps.getProperty(FileHandler.class.getName() + ".pattern");
 			File logFile = generate(pattern, 0, -1);
 			return logFile;
 		} catch (IOException e) {
 			throw new MBeanException(e);
 		}
    }
    
    // Generate a filename from a pattern.
    // Remarks: This method is taken from java.util.io.FileHandler.java to resolve pattern becuase it is not accessible
    private File generate(String pattern, int generation, int unique) throws IOException {
        File file = null;
        String word = "";
        int ix = 0;
        boolean sawg = false;
        boolean sawu = false;
        int count = 1;// no of files to use
        while (ix < pattern.length()) {
            char ch = pattern.charAt(ix);
            ix++;
            char ch2 = 0;
            if (ix < pattern.length()) {
                ch2 = Character.toLowerCase(pattern.charAt(ix));
            }
            if (ch == '/') {
                if (file == null) {
                    file = new File(word);
                } else {
                    file = new File(file, word);
                }
                word = "";
                continue;
            } else  if (ch == '%') {
                if (ch2 == 't') {
                    String tmpDir = System.getProperty("java.io.tmpdir");
                    if (tmpDir == null) {
                        tmpDir = System.getProperty("user.home");
                    }
                    file = new File(tmpDir);
                    ix++;
                    word = "";
                    continue;
                } else if (ch2 == 'h') {
                    file = new File(System.getProperty("user.home"));
                    if (isSetUID()) {
                        // Ok, we are in a set UID program.  For safety's sake
                        // we disallow attempts to open files relative to %h.
                        throw new IOException("can't use %h in set UID program");
                    }
                    ix++;
                    word = "";
                    continue;
                } else if (ch2 == 'g') {
                    word = word + generation;
                    sawg = true;
                    ix++;
                    continue;
                } else if (ch2 == 'u') {
                    word = word + unique;
                    sawu = true;
                    ix++;
                    continue;
                } else if (ch2 == '%') {
                    word = word + "%";
                    ix++;
                    continue;
                }
            }
            word = word + ch;
        }
        if (count > 1 && !sawg) {
            word = word + "." + generation;
        }
        if (unique > 0 && !sawu) {
            word = word + "." + unique;
        }
        if (word.length() > 0) {
            if (file == null) {
                file = new File(word);
            } else {
                file = new File(file, word);
            }
        }
        return file;
    }
    
    // Private native method to check if we are in a set UID program.
    private static native boolean isSetUID();
}
