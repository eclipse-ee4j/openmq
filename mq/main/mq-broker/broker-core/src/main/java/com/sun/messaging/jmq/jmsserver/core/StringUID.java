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
 * @(#)StringUID.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;


/**
 * a UID class which is based off of a unique String
 */


public class StringUID implements java.io.Serializable
{
    static final long serialVersionUID = -8531498672331928433L;

    private String str = null;
    private int hashCode = 0;


    public StringUID(String str) {
        assert str != null;
        this.str = str;
    }

    public String toString() {
        return str;
    }

    public int hashCode() {
        if (str == null) {
            return 0;
        }
        if (hashCode == 0)
            hashCode = str.hashCode();
        return hashCode;
    }

    public boolean equals(Object o) {
        if (str == null) {
            return o == null;
        }
        return str.equals(o.toString());
    }

}
