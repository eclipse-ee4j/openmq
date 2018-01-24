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
 * @(#)AutoRollbackType.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.Serializable;
import java.io.ObjectStreamException;

public class AutoRollbackType implements Serializable
{

    static final long serialVersionUID = -6704477057825567951L;

    /**
     * descriptive string associated with the type
     */
    private final String name;


    /**
     * int value for the state used when reading/writing
     * the client->broker protocol.
     */
    private final int value;


    /**
     * value for ROLLBACK_ALL used with the protocol.
     */
    private static final int I_ROLLBACK_ALL=1;

    /**
     * value for ROLLBACK_NOT_PREPARED used with the protocol.
     */
    private static final int I_ROLLBACK_NOT_PREPARED=2;

    /**
     * value for ROLLBACK_NEVER used with the protocol.
     */
    private static final int I_ROLLBACK_NEVER=3;


    /**
     * mapping of type (int) values to AutoRollbackType
     */
    private static AutoRollbackType[] bs =new AutoRollbackType[3];


    /**
     * private constructor for AutoRollbackType
     */
    private AutoRollbackType(String name, int value) {
        this.name = name;
        this.value = value;
        bs[value-1]=this;
    }

    /**
     * method which takes an int (retrieved from the
     * persistent store) and converts it to a state
     */
    public static final AutoRollbackType getType(int value) 
    {
        return bs[value-1];
    }

    /**
     * method which returns the int value associated
     * with the state. This method should only be used when the
     * state written to or read from the protocol.
     */
    public int intValue()
    {
        return value;
    }

    /**
     * a string representation of the object
     */
    public String toString() {
        return "AutoRollbackType["+name+"]";
    }

    /**
     * Rollback a transaction of this type when the
     * broker is restarted.
     */
    public static final AutoRollbackType ALL = 
             new AutoRollbackType("ALL",
                      I_ROLLBACK_ALL);


    /**
     * Rollback a transaction of this type if
     * it is not in PREPARED when the broker is restarted.
     */
    public static final AutoRollbackType NOT_PREPARED = 
             new AutoRollbackType("NOT_PREPARED",
                      I_ROLLBACK_NOT_PREPARED);

    /**
     * Never rollback a transaction of this type when 
     * the broker is restarted. The transaction must be
     * COMMITTED, ROLLEDBACK or it must timeout.
     */
    public static final AutoRollbackType NEVER = 
             new AutoRollbackType("NEVER",
                      I_ROLLBACK_NEVER);


    Object readResolve() throws ObjectStreamException
    {
        return getType(value);
    }

}

