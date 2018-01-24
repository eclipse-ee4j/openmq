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

package com.sun.messaging.bridge.admin.bridgemgr;

import java.util.Properties;
import java.util.Enumeration;

/**
 * This class encapsulates the information that the user
 * has provided to perform any JMQ Bridge Administration
 * task. It contains properties that describe:
 * <UL>
 * <LI>the type of command
 * <LI>the command argument, options
 * <LI>etc..
 * </UL>
 */

public class BridgeMgrProperties extends Properties
                      implements BridgeMgrOptions {
    
    public BridgeMgrProperties() {
        super();
    }

    /**
     * Returns the command string. e.g. <EM>stop</EM>.
     */
    public String getCommand() {
        return (getProperty(PropName.CMD));
    }

    /**
     * Returns the command argument string. e.g. <EM>bridge</EM>.
     */
    public String getCommandArg() {
	    return (getProperty(PropName.CMDARG));
    }

    /**
     */
    public String getBridgeType() {
	    return (getProperty(PropName.OPTION_BRIDGE_TYPE));
    }

    /**
     */
    public String getBridgeName() {
	    return (getProperty(PropName.OPTION_BRIDGE_NAME));
    }

    /**
     */
    public String getLinkName() {
	    return (getProperty(PropName.OPTION_LINK_NAME));
	}

    /**
     * Returns the broker host:port.
     */
    public String getBrokerHostPort() {
        return (getProperty(PropName.OPTION_BROKER_HOSTPORT));
    }

    /**
     */
    public String getAdminUserId() {
	    return (getProperty(PropName.OPTION_ADMIN_USERID));
    }

    /**
     */
    public String getAdminPasswd() {
	    return (getProperty(PropName.OPTION_ADMIN_PRIVATE_PASSWD));
	}

    /**
     * Returns the admin passfile (file containing admin password).
     */
    public String getAdminPassfile() {
	    return getProperty(PropName.OPTION_ADMIN_PASSFILE);
    }


    /**
     * Returns whether force mode was specified by the user.
     * Force mode is when no user interaction will be needed.
     *
     * @return true if force mode is set, false otherwise
     */
    public boolean forceModeSet() {
        String s = getProperty(PropName.OPTION_FORCE);

        if (s == null) return false;

        if (s.equalsIgnoreCase(Boolean.TRUE.toString())) {
           return true;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString())) {
           return  false;
	    }

        return false;
    }


    /**
     * Returns whether debug mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to get debug information.
     *
     * @return true if debug mode is set, false otherwise 
     */
    public boolean debugModeSet()  {
        String s = getProperty(PropName.OPTION_DEBUG);
        if (s == null) return false; 

        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return true;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return false;
        }
        return false;
    }

    /**
     * Returns whether no-check mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to force imqbridgemgr to accept undocumented
     * options to be set.
     *
     * @return true if no-check mode is set, false otherwise
     */
    public boolean noCheckModeSet()  {
        String s = getProperty(PropName.OPTION_NOCHECK);

        if (s == null) return false;

        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return true;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return false;
        }

	    return false;
    }

    /**
     * Returns whether admin debug mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to get debug information about the
     * admin connection made to the broker.
     *
     * @return  true if admin debug mode is set, false otherwise 
     */
    public boolean adminDebugModeSet()  {
        String s = getProperty(PropName.OPTION_ADMIN_DEBUG);

        if (s == null) return false;
        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return true;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return false;
        }

        return false;
    }

    /**
     */
    public boolean useSSLTransportSet()  {
        String s = getProperty(PropName.OPTION_SSL);

        if (s == null) return false;

        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return true;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return false;
        }

        return false;
    }

    /**
     * @return the receive timeout in seconds
     */
    public int getReceiveTimeout()  {
        String s = getProperty(PropName.OPTION_RECV_TIMEOUT);

        if (s == null)  return -1;

        int ret;
        try {
            ret = Integer.parseInt(s);
        } catch (NumberFormatException nfe)  {
	        ret = -1;
        }

        return ret;
    }

    /**
     * Returns the number of 'receive()' retries.
     */
    public int getNumRetries()  {
        String s = getProperty(PropName.OPTION_NUM_RETRIES);

        if (s == null) return -1;

        int ret;
        try {
	        ret = Integer.parseInt(s);
        } catch (NumberFormatException nfe)  {
	        ret = -1;
        }

        return ret;
    }

    /**
     * Returns a Properties object containing the system
     * properties to set.
     *
     * @return  A Properties object containing system properties
     *      to set
     */
    public Properties getSysProps()  {
    Properties  props = new Properties();
    String      targetAttrs = PropName.OPTION_SYS_PROPS + ".";
    int     targetAttrsLen = targetAttrs.length();

        for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
        String propName = (String)e.nextElement();

        if (propName.startsWith(targetAttrs))  {
            String newPropName, value;

            newPropName = propName.substring(targetAttrsLen);
            value = getProperty(propName);

            props.put(newPropName, value);
        }

        }

        return (props);
    }

    /**
     */
    public String getTargetName()  {
        return (getProperty(PropName.OPTION_TARGET_NAME));
    }


    /**
     * Returns a Properties object containing the properties
     * specified for the target object. The properties are
     * normalized.
     *
     * @return  A Properties object containing properties
     *      for the target object.
     */
    public Properties getTargetAttrs()  {
    Properties  props = new Properties();
    String      targetAttrs = PropName.OPTION_TARGET_ATTRS + ".";
    int     targetAttrsLen = targetAttrs.length();

        for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {

        String propName = (String)e.nextElement();

        if (propName.startsWith(targetAttrs))  {
            String newPropName, value;

            newPropName = propName.substring(targetAttrsLen);
            value = getProperty(propName);

            props.put(newPropName, value);
        }
        }

        return (props);
    }



}

