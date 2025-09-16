/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms;

import java.io.PrintStream;
import java.io.PrintWriter;

import jakarta.jms.JMSException;
import lombok.experimental.UtilityClass;

@UtilityClass
class ExceptionUtils {
    void printStackTrace(JMSException exception, PrintStream stream) {
        Throwable cause;
        try {
            exception.getCause(); //NOPMD
        } catch (Throwable t) {
            if ((cause = exception.getLinkedException()) != null) {
                synchronized (stream) {
                    stream.print("Caused by: ");
                }
                cause.printStackTrace(stream);
            }
        }
    }

    void printStackTrace(JMSException exception, PrintWriter writer) {
        Throwable cause;
        try {
            exception.getCause(); //NOPMD
        } catch (Throwable t) {
            if ((cause = exception.getLinkedException()) != null) {
                synchronized (writer) {
                    writer.print("Caused by: ");
                }
                cause.printStackTrace(writer);
            }
        }
    }
}
