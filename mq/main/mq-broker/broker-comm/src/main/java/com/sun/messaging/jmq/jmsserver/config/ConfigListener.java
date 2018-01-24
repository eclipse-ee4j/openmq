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
 * @(#)ConfigListener.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.config;

/**
 * This interface is used by any object who needs to listen
 * for notification when a specific property has changed.<P>
 *
 */
public interface ConfigListener 
{
    /**
     * method which is called to validate that the passed in
     * name/value is valid.
     *
     * If the data is not valid, a PropertyUpdateException should be
     * thrown.
     *
     * @param name the name of the property to be validated
     * @param value the new value requested for that property
     * @throws PropertyUpdateException the the value is invalid
     *
     */
    public void validate(String name, String value)
        throws PropertyUpdateException;

    /**
     * method which is called then a class which is interested in
     * the state of a specific property should updated its internal
     * state based on the new value of the property.
     *
     * @param name the name of the property to be validated
     * @param value the new value requested for that property
     * @return true if the property has taken affect, false if it
     *        will not take affect until the next broker restart
     *
     */
    public boolean update(String name, String value);
}
