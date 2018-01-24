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

package com.sun.messaging.bridge.admin.bridgemgr;

import com.sun.messaging.jmq.admin.apps.broker.CommonCmdException;

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

public class BridgeMgrException extends CommonCmdException  {

    /********************************************************
     * use integer 5000 -5999 
     *******************************************************/
    protected static final int BRIDGE_NAME_NOT_SPEC                = 5000;
    protected static final int LINK_NAME_NOT_SPEC                  = 5001;
    protected static final int LINK_OPTION_NOT_ALLOWED_FOR_CMDARG  = 5002;
 
    public BridgeMgrException() {
        super();
    }

    /** 
     * @param  type       type of exception 
     **/
    public BridgeMgrException(int type) {
        super(type);
    }

    /** 
     *
     * @param  reason        a description of the exception
     **/
    public BridgeMgrException(String reason) {
        super(reason);
    }

}
