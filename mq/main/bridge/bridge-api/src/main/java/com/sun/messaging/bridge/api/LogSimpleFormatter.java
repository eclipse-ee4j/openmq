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

package com.sun.messaging.bridge.api;

import java.util.logging.SimpleFormatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A modified SimpleFormatter for java.util.logging 
 * 
 * @author amyk
 *
 */
public class LogSimpleFormatter extends SimpleFormatter {

    Logger _logger = null;

    public LogSimpleFormatter(Logger logger ) {
        super();
        _logger = logger;
    }

    @Override
    public String format(LogRecord record) { 

        if (!_logger.isLoggable(Level.FINE)) {
            record.setSourceClassName(null);
            record.setSourceMethodName(null);
            record.setLoggerName("");
        }
        String data = super.format(record);

        if (_logger.isLoggable(Level.FINE)) {
            return "[ThreadID="+ record.getThreadID()+"]: "+data;
        }

        return data;
    }
}    
