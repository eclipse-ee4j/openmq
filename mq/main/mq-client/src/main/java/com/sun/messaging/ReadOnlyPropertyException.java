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
 * @(#)ReadOnlyPropertyException.java	1.4 06/28/07
 */ 

package com.sun.messaging;

import javax.jms.JMSException;

/**
 * A <code>ReadOnlyPropertyException</code> is thrown when an attempt is
 * made to modify a JMQ <code>AdministeredObject</code> that has been
 * set to read only.
 *
 * @see         javax.jms.ConnectionFactory javax.jms.ConnectionFactory
 * @see         com.sun.messaging.AdministeredObject#setReadOnly() com.sun.messaging.AdministeredObject.setReadOnly()
 */
public class ReadOnlyPropertyException extends javax.jms.JMSException {

    /**
     * Constructs a ReadOnlyPropertyException.
     *
     * @param property The property being modified.
     */  
    public ReadOnlyPropertyException(String property) {
        super(property);
    }


}

