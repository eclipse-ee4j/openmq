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
 * @(#)InvalidPropertyException.java	1.4 06/28/07
 */ 

package com.sun.messaging;

import javax.jms.JMSException;

/** 
 * An <code>InvalidPropertyException</code> is thrown when a setProperty
 * is called with an invalid property name parameter.
 * 
 * @see         javax.jms.ConnectionFactory javax.jms.ConnectionFactory 
 * @see         com.sun.messaging.AdministeredObject#setProperty(String, String) com.sun.messaging.AdministeredObject.setProperty(propname, propval) 
 */

public class InvalidPropertyException extends javax.jms.JMSException {

    /**
     * Constructs an InvalidPropertyException.
     *   
     * @param property The invalid property name.
     */  
    public InvalidPropertyException(String property) {
        super(property);
    }

}

