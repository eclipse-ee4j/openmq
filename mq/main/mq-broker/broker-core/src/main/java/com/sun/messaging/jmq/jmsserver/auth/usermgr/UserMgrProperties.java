/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.io.Serial;
import java.util.Properties;

/**
 * This class encapsulates the information that the user has provided to perform any JMQ Broker Administration task. It
 * contains properties that describe:
 * <UL>
 * <LI>the type of command
 * <LI>the command argument
 * <LI>the destination type
 * <LI>the target name
 * <LI>the target attributes
 * <LI>etc..
 * </UL>
 *
 * This class has a number of convenience methods to extract the information above. Currently, each of these methods has
 * a get() version.
 *
 * @see BrokerCmdOptions
 */
public class UserMgrProperties extends Properties implements UserMgrOptions {

    @Serial
    private static final long serialVersionUID = -7346038143570040359L;

    /**
     * Returns the command string. e.g. <EM>list</EM>.
     *
     * @return The command string
     */
    public String getCommand() {
        return getProperty(PROP_NAME_CMD);
    }

    /**
     * Returns the old/current user password.
     *
     * @return The old/current user password.
     */
    public Boolean isActive() {
        String s = getActiveValue();

        if (s == null) {
            return null;
        }

        if (s.equalsIgnoreCase(Boolean.TRUE.toString())) {
            return Boolean.TRUE;
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString())) {
            return Boolean.FALSE;
        }

        return Boolean.FALSE;
    }

    public String getActiveValue() {
        String s = getProperty(PROP_NAME_OPTION_ACTIVE);

        return (s);
    }

    public void setActiveValue(String s) {
        setProperty(PROP_NAME_OPTION_ACTIVE, s);
    }

    /**
     * Returns the user password.
     *
     * @return The user password.
     */
    public String getPassword() {
        return getProperty(PROP_NAME_OPTION_PASSWD);
    }

    /**
     * Sets the user password.
     *
     * @param password The user password.
     */
    public void setPassword(String password) {
        setProperty(PROP_NAME_OPTION_PASSWD, password);
    }

    /**
     * Returns the user role.
     *
     * @return The user role.
     */
    public String getRole() {
        return getProperty(PROP_NAME_OPTION_ROLE);
    }

    /**
     * Returns the user name.
     *
     * @return The user name.
     */
    public String getUserName() {
        return (getProperty(PROP_NAME_OPTION_USERNAME));
    }

    /**
     * Sets the user name.
     *
     * @param username The user name.
     */
    public void setUserName(String username) {
        setProperty(PROP_NAME_OPTION_USERNAME, username);
    }

    /**
     * Returns the instance name.
     *
     * @return The instance name.
     */
    public String getInstance() {
        return getProperty(PROP_NAME_OPTION_INSTANCE);
    }

    /**
     * Sets the instance name.
     *
     * @param instance The instance name.
     */
    public void setInstance(String instance) {
        setProperty(PROP_NAME_OPTION_INSTANCE, instance);
    }

    /**
     * Returns the path name of the password file
     *
     * @return The path name of the password file.
     */
    public String getPasswordFile() {
        return getProperty(PROP_NAME_PASSWORD_FILE);
    }

    /**
     * Sets the path name of the password file
     *
     * @param pwfile The path name of the password file.
     */
    public void setPasswordFile(String pwfile) {
        setProperty(PROP_NAME_PASSWORD_FILE, pwfile);
    }

    /**
     * Returns the path name of the passfile
     *
     * @return The path name of the passfile.
     */
    public String getPassfile() {
        return getProperty(PROP_NAME_OPTION_PASSFILE);
    }

    /**
     * Returns whether force mode was specified by the user. Force mode is when no user interaction will be needed. i.e. if
     * storing an object, and an object with the same lookup name already exists, no overwrite confirmation will be asked,
     * the object is overwritten.
     *
     * @return true if force mode is set, false if force mode was not set.
     */
    public boolean forceModeSet() {
        String s = getProperty(PROP_NAME_OPTION_FORCE);

        if (s == null) {
            return (false);
        }

        if (s.equalsIgnoreCase(Boolean.TRUE.toString())) {
            return (true);
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString())) {
            return (false);
        }

        return (false);
    }

    /**
     * Returns the path name of the src file (for encode/decode)
     *
     * @return The path name of the src file (for encode/decode).
     */
    public String getSrc() {
        return getProperty(PROP_NAME_OPTION_SRC);
    }

    /**
     * Returns the path name of the target file (for encode/decode)
     *
     * @return The path name of the target file (for encode/decode).
     */
    public String getTarget() {
        return getProperty(PROP_NAME_OPTION_TARGET);
    }

}
