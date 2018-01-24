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

/*
 * @(#)JMXLogHandler.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.agent;

import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.util.log.Logger;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

/**
 * A LogHandler that uses the JMX infrastructure to send
 * JMX notifications to interested parties.
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler")
@PerLookup
public class JMXLogHandler extends Handler {

    public JMXLogHandler() {
    }

    /**
     * Close handler
     */
    public void close() {
    }

    /**
     * Return a string description of this handler.
     */
    public String toString() {
	return this.getClass().getName();
    }

    /**
     * Pass log record to JMX system
     */
	@Override
	public void publish(LogRecord record) {
		// ignore FORCE messages if we have explicitly been asked to ignore them
		if (!this.isLoggable(record)) {
			return;
		}

		Agent agent = Globals.getAgent();

		if (agent != null) {
			agent.notifyLogMessage(record.getLevel().intValue(),
						record.getMessage());
		}
		
	}

	@Override
	public void flush() {
		// TODO Auto-generated method stub
		// Nothing to do
	}
}
