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
 * @(#)MemoryCallback.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.memory;


/**
 * this class is used by the Client->Broker or
 * broker->broker flow control to request notification
 * when messages can resume
 *
 * Any client interested in receiving notifications when
 * either memory levels have changed or a specific level
 * of free memory should implement this method and register
 * using the <I>MemoryManager:registerMemoryCallback</I> method.
 * When memory levels change, updateMemory will be called.
 * <P>
 * To receive callbacks when a specific amount of memory is
 * available OR resume should be called, register the Callback
 * with <I>MemoryManager:notifyWhenAvailable()</I>.
 *
 * @see MemoryManager
 */

public interface MemoryCallback
{
    /**
     * called in respones to a notifyWhenAvailable request
     *
     * @param cnt value of JMQSize at this time
     * @param memory value of JMQBytes at this time
     * @param max value of JMQMaxMsgBytes
     */
    public void resumeMemory(int cnt, long memory, long max);

    /**
     * called when the memory level has been changed
     * because of a state change (e.g. green -> yellow)
     *
     * @param cnt value of JMQSize at this time
     * @param memory value of JMQBytes at this time
     * @param max value of JMQMaxMsgBytes
     */
    public void updateMemory(int cnt, long memory, long max);
}
/*
 * EOF
 */
