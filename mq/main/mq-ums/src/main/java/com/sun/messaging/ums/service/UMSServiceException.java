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

package com.sun.messaging.ums.service;

import java.io.Serial;

/**
 *
 * @author chiaming
 */

/**
 *
 * MQService throws this exception when it encounters errors.
 *
 */
public class UMSServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8769067268978890428L;

    public UMSServiceException() {
    }

    public UMSServiceException(String message) {
        super(message);
    }

    public UMSServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UMSServiceException(Throwable cause) {
        super(cause);
    }
}
