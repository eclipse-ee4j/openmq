/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util.log;

import java.io.Serial;
import java.util.logging.Level;

final class ForceLogLevel extends Level {
    @Serial
    private static final long serialVersionUID = 4240388699161654518L;
    private static String defaultBundle = "sun.util.logging.resources.logging";
    public static final Level FORCE = new ForceLogLevel("FORCE", 1100, defaultBundle);

    ForceLogLevel(String name, int value, String resourceBundleName) {
        super(name, value, resourceBundleName);
    }

    ForceLogLevel(String name, int value) {
        super(name, value);
    }
}
