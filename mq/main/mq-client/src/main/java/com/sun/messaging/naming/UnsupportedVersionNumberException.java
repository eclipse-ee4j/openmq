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

package com.sun.messaging.naming;

import java.io.Serial;

/**
 * An <code>UnsupportedVersionNumberException</code> is thrown when the <code>getInstance()</code> method of
 * <code>AdministeredObjectFactory</code> finds a Version number for an unsupported format of a iMQ Administered Object
 * in the Reference object.
 *
 * @see com.sun.messaging.naming.AdministeredObjectFactory com.sun.messaging.naming.AdministeredObjectFactory
 */

public class UnsupportedVersionNumberException extends javax.naming.NamingException {

    @Serial
    private static final long serialVersionUID = -8634114274627205413L;

    /**
     * Constructs an UnsupportedVersionNumberException.
     *
     * @param version The unsupported version number that was found.
     */
    public UnsupportedVersionNumberException(String version) {
        super(version);
    }
}
