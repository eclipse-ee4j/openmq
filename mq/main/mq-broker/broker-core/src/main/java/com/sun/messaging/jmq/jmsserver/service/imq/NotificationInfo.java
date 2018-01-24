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
 * @(#)NotificationInfo.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;


/**
 * interface which is used for thread models who need to know
 * when the thread is ready for an event
 */

public interface NotificationInfo
{
    public void setReadyToWrite(IMQConnection con, boolean ready);

    public void assigned(IMQConnection con, int events) throws IllegalAccessException;

    public void released(IMQConnection con, int events);

    public void destroy(String reason);

    public void dumpState();

    public String getStateInfo();
} 
/*
 * EOF
 */
