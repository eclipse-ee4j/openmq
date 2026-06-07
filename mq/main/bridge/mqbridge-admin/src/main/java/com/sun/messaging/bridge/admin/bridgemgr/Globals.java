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

package com.sun.messaging.bridge.admin.bridgemgr;

import java.util.Locale;
import com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources;

/**
 * singleton class which contains globals for bridge admin
 */

final class Globals {
    private static BridgeAdminResources bar = null;

    private Globals() {
    }

    public static synchronized BridgeAdminResources getBridgeAdminResources() {
        if (bar == null) {
            bar = BridgeAdminResources.getResources(Locale.getDefault());
        }
        return bar;
    }

}
