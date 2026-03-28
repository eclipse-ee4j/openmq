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

/**
 * Interface containing constants for command line options, property names and values for the JMS Object Administration
 * utility.
 */
public final class UserMgrOptions {
    private UserMgrOptions() {
        throw new UnsupportedOperationException();
    }

    /*
     * BEGIN OPTION NAMES
     */

    /**
     * Strings defining what the sub command names are
     */
    public static final String CMD_ADD = "add";
    public static final String CMD_DELETE = "delete";
    public static final String CMD_LIST = "list";
    public static final String CMD_UPDATE = "update";

    /*
     * Private sub commands - for testing purposes
     */
    public static final String CMD_EXISTS = ".exists";
    public static final String CMD_GETGROUP = ".getgroup";
    public static final String CMD_GETGROUPSIZE = ".getgroupsize";

    /*
     * More private sub commands
     */
    public static final String CMD_ENCODE = "encode";
    public static final String CMD_DECODE = "decode";

    /*
     * Options - jmqusermgr specific
     */
    public static final String OPTION_ACTIVE = "-a";
    public static final String OPTION_PASSWD = "-p";
    public static final String OPTION_ROLE = "-g";
    public static final String OPTION_USERNAME = "-u";
    public static final String OPTION_INSTANCE = "-i";
    public static final String OPTION_PASSFILE = "-passfile";
    public static final String OPTION_SRC = "-src";
    public static final String OPTION_TARGET = "-target";

    /*
     * Options - 'Standard'
     */
    public static final String OPTION_FORCE = "-f";
    public static final String OPTION_SILENTMODE = "-s";
    public static final String OPTION_CREATEMODE = "-c";
    public static final String OPTION_SHORT_HELP1 = "-h";
    public static final String OPTION_SHORT_HELP2 = "-help";
    public static final String OPTION_LONG_HELP1 = "-H";
    public static final String OPTION_LONG_HELP2 = "-Help";
    public static final String OPTION_VERSION1 = "-v";
    public static final String OPTION_VERSION2 = "-version";
    public static final String OPTION_SYSTEM_PROPERTY_PREFIX = "-D";

    /*
     * END OPTION NAMES
     */

    /*
     * BEGIN PROPERTY NAMES/VALUES
     */

    /**
     * Property name representing what command needs to be executed.
     */
    public static final String PROP_NAME_CMD = "cmdtype";

    /*
     * Property values for command types.
     */
    public static final String PROP_VALUE_CMD_ADD = CMD_ADD;
    public static final String PROP_VALUE_CMD_DELETE = CMD_DELETE;
    public static final String PROP_VALUE_CMD_LIST = CMD_LIST;
    public static final String PROP_VALUE_CMD_UPDATE = CMD_UPDATE;

    public static final String PROP_VALUE_CMD_EXISTS = CMD_EXISTS;
    public static final String PROP_VALUE_CMD_GETGROUP = CMD_GETGROUP;
    public static final String PROP_VALUE_CMD_GETGROUPSIZE = CMD_GETGROUPSIZE;

    public static final String PROP_VALUE_CMD_ENCODE = CMD_ENCODE;
    public static final String PROP_VALUE_CMD_DECODE = CMD_DECODE;

    public static final String PROP_NAME_OPTION_ACTIVE = "active";
    public static final String PROP_NAME_OPTION_PASSWD = "passwd";

    public static final String PROP_NAME_OPTION_ROLE = "role";

    public static final String PROP_NAME_OPTION_USERNAME = "username";

    public static final String PROP_NAME_OPTION_INSTANCE = "instance";

    public static final String PROP_NAME_OPTION_FORCE = "force";
    public static final String PROP_VALUE_OPTION_FORCE = "true";

    public static final String PROP_NAME_OPTION_SILENTMODE = "silent";
    public static final String PROP_VALUE_OPTION_SILENTMODE = "true";

    public static final String PROP_NAME_OPTION_CREATEMODE = "create";
    public static final String PROP_VALUE_OPTION_CREATEMODE = "false";

    public static final String PROP_NAME_OPTION_SRC = "src";
    public static final String PROP_NAME_OPTION_TARGET = "target";

    /*
     * Location of user repository e.g. /var/imq/instances/imqbroker/etc/passwd
     */
    public static final String PROP_NAME_PASSWORD_FILE = "pwfile";

    /*
     * Location/name of passfile - file containing user's password that is specified to imqusermgr.
     */
    public static final String PROP_NAME_OPTION_PASSFILE = "passfile";

    /*
     * END PROPERTY NAMES/VALUES
     */

    enum OPTION_ROLE_VALID_VALUES {
        PROP_VALUE_ROLE_ADMIN("admin"), PROP_VALUE_ROLE_USER("user"), PROP_VALUE_ROLE_ANON("anonymous");

        private String name;

        OPTION_ROLE_VALID_VALUES(String n) {
            this.name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /*
     * List of characters that cannot be used in usernames
     */
    enum OPTION_USERNAME_INVALID_CHARS {
        COLON(':'), STAR('*'), COMMA(','), LF('\n'), CR('\r');

        private char name;

        OPTION_USERNAME_INVALID_CHARS(char c) {
            this.name = c;
        }

        public char getChar() {
            return name;
        }
    }

    public static final String DEFAULT_ENCODE_PREFIX = ".encode";
    public static final String DEFAULT_DECODE_PREFIX = ".decode";
}
