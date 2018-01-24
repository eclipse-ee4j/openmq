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
 * @(#)MQGroup.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.auth.jaas;

import java.security.Principal;

public class MQGroup implements Principal, java.io.Serializable {

    private String name;

    public MQGroup(String name) {
	    this.name = name;
    }

    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof MQGroup)) return false;
        if (this == obj) return true;

        MQGroup that = (MQGroup)obj;
        return this.getName().equals(that.getName());
    }
    
    public String toString() {
	    return name;
    }

    public int hashCode() {
	    return name.hashCode();
    }

    public String getName() {
	    return name;
    }

}
