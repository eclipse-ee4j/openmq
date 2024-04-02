/*
 * Copyright (c) 2024 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq;

import java.util.Set;

public final class StandardServiceName {
    private StandardServiceName() {
        throw new UnsupportedOperationException();
    }

    public static final String JMS_SERVICE_NAME = "jms";

    public static final String SSLJMS_SERVICE_NAME = "ssljms";

    public static final String HTTPJMS_SERVICE_NAME = "httpjms";

    public static final String HTTPSJMS_SERVICE_NAME = "httpsjms";

    public static final String ADMIN_SERVICE_NAME = "admin";

    public static final String SSLADMIN_SERVICE_NAME = "ssladmin";

    private static final Set<String> STANDARD_SERVICE_NAMES = Set.of(
            JMS_SERVICE_NAME,
            SSLJMS_SERVICE_NAME,
            ADMIN_SERVICE_NAME,
            SSLADMIN_SERVICE_NAME,
            HTTPJMS_SERVICE_NAME,
            HTTPSJMS_SERVICE_NAME
    );

    public static boolean isDefaultStandardServiceName(String name) {
        return STANDARD_SERVICE_NAMES.contains(name);
    }
}
