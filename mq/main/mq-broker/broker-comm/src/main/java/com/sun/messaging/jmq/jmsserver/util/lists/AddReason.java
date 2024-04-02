/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.util.lists;

import com.sun.messaging.jmq.util.lists.*;

/**
 * Reasons a packet may be added to list.
 */

public class AddReason extends Reason {
    public static final AddReason LOADED = new AddReason(3, "Loaded");
    public static final AddReason QUEUED = new AddReason(4, "Queued");

    private AddReason(int id, String str) {
        super(id, str);
    }
}
