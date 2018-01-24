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

package com.sun.messaging.bridge.service.jms;

/**
 * 
 * @author amyk
 *
 */
public class AutoDestination {
    
    private boolean _isqueue = true;
    private String _name = null ;

    public AutoDestination(String name, boolean isqueue) {
        _name = name;
        _isqueue = isqueue;
    };

    public String getName() { 
        return _name;
    }

    public boolean isQueue() { 
        return _isqueue;
    }

    public boolean isTopic() { 
        return !_isqueue;
    }

    public String toString() {
        return ""+(_isqueue ? "queue:":"topic:")+getName();
    }

}
