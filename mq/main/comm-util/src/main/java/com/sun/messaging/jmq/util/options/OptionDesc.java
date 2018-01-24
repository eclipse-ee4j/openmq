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
 * @(#)OptionDesc.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.util.options;

/**
 * This class describes a command line option:
 *
 * <UL>
 * <LI>it's type
 * <LI>the actual option string
 * <LI>it's base property
 * <LI>it's value (if type is OptionType.OPTION_VALUE_HARDCODED)
 * </UL>
 *
 * @see		OptionType
 */
public class OptionDesc {
    /**
     * The type of the option, as defined by the interface OptionType.
     * The valid values are:
     * <UL>
     * <LI>OPTION_VALUE_HARDCODED
     * <LI>OPTION_VALUE_NEXT_ARG
     * <LI>OPTION_VALUE_NEXT_ARG_RES
     * </UL>
     *
     * @see	OptionType
     */
    public int		type;

    /**
     * The actual option, for example <EM>-a</EM>
     */
    public String	option;

    /**
     * The property name that will be associated with this
     * option. This property name may be a basename for
     * the actual property used.
     *
     * @see  
     * com.sun.messaging.jmq.admin.util.OptionType#OPTION_VALUE_NEXT_ARG_RES
     */
    public String	baseProperty;

    public String	nameValuePair = null;

    /**
     * Flag indicating whether this option should be parsed, but
     * not processed (i.e. stored in properties database).
     */
    public boolean	ignore = false;

    /**
     * Value of the property for this option. See
     * OptionType.OPTION_VALUE_HARDCODED.
     *
     * @see	OptionType
     */
    public String	value;
    
    public OptionDesc(String option, int type, String baseProp, String value)  {
	this(option, type, baseProp, value, null, false);
    }

    public OptionDesc(String option, int type, String baseProp, String value,
				String nameValuePair)  {
	this(option, type, baseProp, value, nameValuePair, false);
    }


    public OptionDesc(String option, int type, String baseProp,
				String value, boolean ignore)  {
	this(option, type, baseProp, value, null, ignore);
    }

    public OptionDesc(String option, int type, String baseProp,
				String value, String nameValuePair,
				boolean ignore)  {
	this.type = type;
	this.option = option;
	this.baseProperty = baseProp;
	this.value = value;
	this.nameValuePair = nameValuePair;
	this.ignore = ignore;
    }
}
