/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging;

import java.io.Serial;

/**
 * An <code>InvalidPropertyValueException</code> is thrown when setProperty is called with an invalid property value
 * parameter.
 *
 * @see jakarta.jms.ConnectionFactory jakarta.jms.ConnectionFactory
 * @see com.sun.messaging.AdministeredObject#setProperty(String, String)
 * com.sun.messaging.AdministeredObject.setProperty(propname, propval)
 */

public class InvalidPropertyValueException extends jakarta.jms.JMSException {

    @Serial
    private static final long serialVersionUID = -5598985795609666298L;

    /**
     * Constructs an InvalidPropertyValueException.
     * <p>
     * The exception message is formatted as
     * <p>
     * <b><code>name=value</code></b>
     *
     * @param name The property name.
     * @param value The invalid property value.
     */
    public InvalidPropertyValueException(String name, String value) {
        super(name + "=" + value);
    }

}
