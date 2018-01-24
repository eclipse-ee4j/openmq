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
 * @(#)LoggerManager.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util;

import java.util.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.*;

/**
 * Handle dynamically updating the Logger with property changes. This
 * isn't done directly by logger since it is in jmq.util and doesn't
 * know about ConfigListeners.
 */
public class LoggerManager implements ConfigListener
{
    Logger logger = null;
    BrokerConfig config = null;

    public LoggerManager(Logger logger, BrokerConfig config) {
        this.logger = logger;
        this.config = config;

        // Register ourself as the config listener for all properties
        // that the logger can updated dynamically
        String[] props = logger.getUpdateableProperties();
        for (int n = 0; n < props.length; n++) {
            config.addListener(props[n], this);
        }
    }

    public void validate (String name, String value)
        throws PropertyUpdateException {

        // Validate does the update as well. Yuck.
        try {
            logger.updateProperty(name, value);
        } catch (IllegalArgumentException e) {
            throw new PropertyUpdateException(e.getMessage());
        }

    }

    public boolean update (String name, String value) {
        return true;
    }
}
