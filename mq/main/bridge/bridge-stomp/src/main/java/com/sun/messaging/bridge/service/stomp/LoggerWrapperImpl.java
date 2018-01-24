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

package com.sun.messaging.bridge.service.stomp;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.messaging.jmq.util.LoggerWrapper;

/**
 * @author amyk 
 */
public class LoggerWrapperImpl implements LoggerWrapper {
    private final Logger logger; 

    public LoggerWrapperImpl(Logger logger) {
        this.logger = logger;
    }

    public void logFinest(String msg, Throwable t) {
        logger.log(Level.FINEST, msg, t);
    }

    public void logFine(String msg, Throwable t) {
        logger.log(Level.FINE, msg, t);
    }

    public void logInfo(String msg, Throwable t) {
        logger.log(Level.INFO, msg, t);
    }

    public void logWarn(String msg, Throwable t) {
        logger.log(Level.WARNING, msg, t);
    }

    public void logSevere(String msg, Throwable t) {
        logger.log(Level.SEVERE, msg, t);
    }

    public boolean isFinestLoggable() {
        return logger.isLoggable(Level.FINEST);
    }

    public boolean isFineLoggable() {
        return logger.isLoggable(Level.FINE);
    }
}

