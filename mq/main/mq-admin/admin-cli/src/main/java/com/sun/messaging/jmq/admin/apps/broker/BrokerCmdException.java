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
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

/**
 * This exception is thrown when problems are
 * encountered when validating the information
 * that is provided to execute commands. Examples
 * of errors include:
 * <UL>
 * <LI>bad command type
 * <LI>missing mandatory values
 * </UL>
 *
 * <P>
 * The information that is provided by the user is encapsulated
 * in a BrokerCmdProperties object. This exception will
 * contain a BrokerCmdProperties object to encapsulate
 * the erroneous information.
 **/

public class BrokerCmdException extends CommonCmdException  {

    /****************************************************************************
     * use integer 0 -1000 to avoid overlap with super class and other subclasses
     ****************************************************************************/

    public static final int     TARGET_NAME_NOT_SPEC    = 2;
    public static final int     DEST_NAME_NOT_SPEC  = 5;
    public static final int     TARGET_ATTRS_NOT_SPEC   = 6;
    public static final int     DEST_TYPE_NOT_SPEC  = 7;
    public static final int     FLAVOUR_TYPE_INVALID    = 8;
    public static final int     INVALID_DEST_TYPE   = 10;
    public static final int     CLIENT_ID_NOT_SPEC  = 11;
    public static final int     BAD_ATTR_SPEC_CREATE_DST_QUEUE  = 12;
    public static final int     BAD_ATTR_SPEC_CREATE_DST_TOPIC  = 13;
    public static final int     BAD_ATTR_SPEC_UPDATE_BKR    = 14;
    public static final int     BAD_ATTR_SPEC_UPDATE_DST_QUEUE  = 15;
    public static final int     BAD_ATTR_SPEC_UPDATE_DST_TOPIC  = 16;
    public static final int     BAD_ATTR_SPEC_UPDATE_SVC    = 17;
    public static final int     INVALID_BOOLEAN_VALUE      = 18;
    public static final int     INVALID_LOG_LEVEL_VALUE = 19;
    public static final int     INVALID_METRIC_INTERVAL = 20;
    public static final int     INVALID_METRIC_TYPE = 21;
    public static final int     INVALID_BYTE_VALUE         = 22;
    public static final int     BAD_ATTR_SPEC_GETATTR = 24;
    public static final int     SINGLE_TARGET_ATTR_NOT_SPEC = 25;
    public static final int     BAD_ATTR_SPEC_PAUSE_DST     = 26;
    public static final int     INVALID_PAUSE_TYPE      = 27;
    public static final int     INVALID_METRIC_DST_TYPE     = 28;
    public static final int     INVALID_METRIC_SAMPLES      = 29;
    public static final int     INVALID_LIMIT_BEHAV_VALUE   = 31;
    public static final int     DST_QDP_VALUE_INVALID       = 32;
    public static final int     BKR_QDP_VALUE_INVALID       = 33;
    public static final int     INVALID_RESET_TYPE      = 36;
    public static final int     MSG_ID_NOT_SPEC         = 37;
    public static final int     UPDATE_DST_ATTR_SPEC_CREATE_ONLY_QUEUE  = 38;
    public static final int     UPDATE_DST_ATTR_SPEC_CREATE_ONLY_TOPIC  = 39;
    public static final int     BAD_ATTR_SPEC_CHANGEMASTER  = 40;



    public BrokerCmdException() {
        super();
    }

    /** 
     * Constructs an BrokerCmdException with type
     *
     * @param  type       type of exception 
     **/
    public BrokerCmdException(int type) {
        super(type);
    }

    /** 
     * Constructs an BrokerCmdException with reason
     *
     * @param  reason        a description of the exception
     **/
    public BrokerCmdException(String reason) {
        super(reason);
    }

}
