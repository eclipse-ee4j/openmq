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

package com.sun.messaging.jmq.jmsclient;

import java.io.PrintStream;
import java.util.*;

@SuppressWarnings("JdkObsolete")
abstract class FlowControlEntry {
    protected boolean debug = Debug.debug;

    protected FlowControl fc;
    protected ProtocolHandler protocolHandler;

    FlowControlEntry(FlowControl fc, ProtocolHandler protocolHandler) {
        this.fc = fc;
        this.protocolHandler = protocolHandler;
    }

    public abstract void messageReceived();

    public abstract void messageDelivered();

    public abstract void resetFlowControl(int count);

    public abstract void setResumeRequested(boolean resumeRequested);

    protected abstract void sendResumeFlow() throws Exception;

    protected Hashtable getDebugState() {
        return new Hashtable();
    }

    protected Object TEST_GetAttribute(String name) {
        return null;
    }

    protected abstract void status_report(PrintStream dbg);
}

