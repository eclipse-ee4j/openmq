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
 * @(#)VRFileWarning.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.io.disk;

import java.util.ArrayList;

/**
 * This class provides information about problems and
 * data corruptions encountered when loading the backing file.
 */

public class VRFileWarning extends Throwable {

    private ArrayList warnings = new ArrayList(1);

    /**
     * Constructs a VRFileWarning
     */ 
    public VRFileWarning() {
        super();
    }

    /** 
     * Constructs a VRFileWarning with a reason
     *
     * @param  reason        a description of the exception
     **/
    public VRFileWarning(String reason) {
        super(reason);
    }

    /**
     * Gets all warnings.
     *
     * @return all warnings
     **/
    public synchronized String[] getWarnings() {
        return (String[])warnings.toArray(new String[warnings.size()]);
    }

    /**
     * Add a warning.
     * @param w       a warning
     **/
    public synchronized void addWarning(String w) {
        warnings.add(w);
    }

    public String toString() {
	return super.toString() + ":" + warnings;
    }
}
