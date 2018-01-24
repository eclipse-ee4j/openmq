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
 * @(#)OptionType.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.util.options;

/**
 * This interface defines constants for command line option types.
 *
 * <P>
 * Every command line option has a property name and value
 * associated with it.
 *
 * <P>
 * The different option types vary depending on where their
 * property values come from and the format of the value.
 *
 * @see		OptionDesc
 */
public interface OptionType {

    /**
     * Options that have a hardcoded value. The value will not be
     * specified on the command line. Their value will come from 
     * the OptionDesc class. Examples:
     * <UL>
     * <LI>jmqobjmgr -a
     * <LI>jmqobjmgr -d
     * <LI>jmqobjmgr -f
     * </UL>
     */
    public static int	OPTION_VALUE_HARDCODED		= 1;

    /**
     * Options that have a value specified on the command
     * line. The value is the very next argument on the
     * command line. Examples:
     * <UL>
     * <LI>jmqobjmgr -t qf
     * <LI>jmqobjmgr -i "com.sun.jndi.ldap.LdapCtxFactory"
     * </UL>
     *
     */
    public static int	OPTION_VALUE_NEXT_ARG		= 2;

    /**
     * Options that have a value specified on the command
     * line. The value has a name/value pair format:
     *		<EM>name=value</EM>
     * and is the very next argument on the command line.
     * The property that will represent this option will
     * be the concatenation of the base property for this
     * option (see OptionDesc class) and the <EM>name</EM>
     * portion of the name/value pair.
     * Examples:
     * <UL>
     * <LI>jmqobjmgr -o "foo=bar"
     * </UL>
     * In this example, if the base property for <EM>-o</EM> is
     * <EM>obj.attrs</EM>, the relevant property and value here
     * will be: <EM>obj.attrs.foo=bar</EM>
     *
     */
    public static int	OPTION_VALUE_NEXT_ARG_RES	= 3;

    /**
     * Options that have a value specified on the command
     * line. The value has a name/value pair format:
     *		<EM>name=value</EM>
     * and is appended(ie is a suffix to) the option.
     *
     * The property that will represent this option will
     * be the concatenation of the base property for this
     * option (see OptionDesc class) and the <EM>name</EM>
     * portion of the name/value pair.
     * Examples:
     * <UL>
     * <LI>imqcmd -Dfoo=bar
     * </UL>
     * In this example, if the base property for <EM>-D</EM> is
     * <EM>sys.props</EM>, the relevant property and value here
     * will be: <EM>sys.props.foo=bar</EM>
     */
    public static int	OPTION_VALUE_SUFFIX_RES	= 4;
}
