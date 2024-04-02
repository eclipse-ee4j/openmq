/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.admin.apps.console.util;

import javax.swing.JTextField;

public class IntegerField extends JTextField {

    private static final long serialVersionUID = -3341304154834961301L;

    public IntegerField(long min, long max, String text) {
        this(min, max, text, 0);
    }

    public IntegerField(long min, long max, int columns) {
        this(min, max, null, columns);
    }

    public IntegerField(long min, long max, String text, int columns) {
        super(new IntegerDocument(min, max), text, columns);
    }
}

