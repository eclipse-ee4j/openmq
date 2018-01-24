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
 * @(#)ServerSession.java	1.4 06/27/07
 */ 

package com.sun.jms.spi;

public interface ServerSession extends javax.jms.ServerSession {

    /**
     * Indicate that resources associated with ServerSession
     * are no longer needed.
     */
    void close();

    /**
     * Hook to enable container processing just prior to msg delivery.
     * For example, allow a transaction to be started just prior to msg
     * being delivered to the Session MessageListener.
     * @param msg Message that is about to be delivered.
     */
    void beforeMessageDelivery(javax.jms.Message msg);

    /**
     * Hook to enable container processing after msg delivery.
     * @param msg Message that was delivered.
     */
    void afterMessageDelivery(javax.jms.Message msg);
}

