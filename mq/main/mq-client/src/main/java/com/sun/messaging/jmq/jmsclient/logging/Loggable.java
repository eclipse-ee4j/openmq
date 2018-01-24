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
 * @(#)Loggable.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.logging;

/**
 * MQ Loggable interface.
 * <p>
 * An exception can implement this interface to indicate that it is loggable.
 *
 * All JMSExceptions in com.sun.messaging.jms package implement this interface.
 * A Loggable exception thrown from ExceptionHandler.throwJMSException() can be
 * logged in the root logger name space.  The same exception will not be logged
 * again if ExceptionHandler.throwJMSException() is called more than once.
 *
 * XXX HAWK -- add comment here.
 */
public interface Loggable {

    /**
     * set state to true if this object is logged.
     * @param state boolean
     */
    public void setLogState (boolean state);

    /**
     * get logging state of this object.
     * @return boolean true if this object is logged.
     */
    public boolean getLogState();

}
