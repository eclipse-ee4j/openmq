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
public interface UserMgrOptions {

    /*
     * BEGIN OPTION NAMES
     */

    /**
     * Strings defining what the sub command names are
     */
    String CMD_ADD = "add";
    String CMD_DELETE = "delete";
    String CMD_LIST = "list";
    String CMD_UPDATE = "update";

    /*
     * Private sub commands - for testing purposes
     */
    String CMD_EXISTS = ".exists";
    String CMD_GETGROUP = ".getgroup";
    String CMD_GETGROUPSIZE = ".getgroupsize";

    /*
     * More private sub commands
     */
    String CMD_ENCODE = "encode";
    String CMD_DECODE = "decode";

    /*
     * Options - jmqusermgr specific
     */
    String OPTION_ACTIVE = "-a";
    String OPTION_PASSWD = "-p";
    String OPTION_ROLE = "-g";
    String OPTION_USERNAME = "-u";
    String OPTION_INSTANCE = "-i";
    String OPTION_PASSFILE = "-passfile";
    String OPTION_SRC = "-src";
    String OPTION_TARGET = "-target";

    /*
     * Options - 'Standard'
     */
    String OPTION_FORCE = "-f";
    String OPTION_SILENTMODE = "-s";
    String OPTION_CREATEMODE = "-c";
    String OPTION_SHORT_HELP1 = "-h";
    String OPTION_SHORT_HELP2 = "-help";
    String OPTION_LONG_HELP1 = "-H";
    String OPTION_LONG_HELP2 = "-Help";
    String OPTION_VERSION1 = "-v";
    String OPTION_VERSION2 = "-version";
    String OPTION_SYSTEM_PROPERTY_PREFIX = "-D";

    /*
     * END OPTION NAMES
     */

    /*
     * BEGIN PROPERTY NAMES/VALUES
     */

    /**
     * Property name representing what command needs to be executed.
     */
    String PROP_NAME_CMD = "cmdtype";

    /*
     * Property values for command types.
     */
    String PROP_VALUE_CMD_ADD = CMD_ADD;
    String PROP_VALUE_CMD_DELETE = CMD_DELETE;
    String PROP_VALUE_CMD_LIST = CMD_LIST;
    String PROP_VALUE_CMD_UPDATE = CMD_UPDATE;

    String PROP_VALUE_CMD_EXISTS = CMD_EXISTS;
    String PROP_VALUE_CMD_GETGROUP = CMD_GETGROUP;
    String PROP_VALUE_CMD_GETGROUPSIZE = CMD_GETGROUPSIZE;

    String PROP_VALUE_CMD_ENCODE = CMD_ENCODE;
    String PROP_VALUE_CMD_DECODE = CMD_DECODE;

    String PROP_NAME_OPTION_ACTIVE = "active";
    String PROP_NAME_OPTION_PASSWD = "passwd";

    String PROP_NAME_OPTION_ROLE = "role";

    String PROP_NAME_OPTION_USERNAME = "username";

    String PROP_NAME_OPTION_INSTANCE = "instance";

    String PROP_NAME_OPTION_FORCE = "force";
    String PROP_VALUE_OPTION_FORCE = "true";

    String PROP_NAME_OPTION_SILENTMODE = "silent";
    String PROP_VALUE_OPTION_SILENTMODE = "true";

    String PROP_NAME_OPTION_CREATEMODE = "create";
    String PROP_VALUE_OPTION_CREATEMODE = "false";

    String PROP_NAME_OPTION_SRC = "src";
    String PROP_NAME_OPTION_TARGET = "target";

    /*
     * Location of user repository e.g. /var/imq/instances/imqbroker/etc/passwd
     */
    String PROP_NAME_PASSWORD_FILE = "pwfile";

    /*
     * Location/name of passfile - file containing user's password that is specified to imqusermgr.
     */
    String PROP_NAME_OPTION_PASSFILE = "passfile";

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
        COLON(':'), START('*'), COMMA(','), LF('\n'), CR('\r');

        private char name;

        OPTION_USERNAME_INVALID_CHARS(char c) {
            this.name = c;
        }

        public char getChar() {
            return name;
        }
    }

    String DEFAULT_ENCODE_PREFIX = ".encode";
    String DEFAULT_DECODE_PREFIX = ".decode";
}
