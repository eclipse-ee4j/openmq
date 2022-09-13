/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021, 2022 Contributors to the Eclipse Foundation
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

import com.sun.messaging.jmq.admin.event.CommonCmdStatusEvent;

/**
 * Event class indicating some actions related to Bridge Management.
 * <P>
 * The fields of this event include the various pieces of information needed for broker management tasks.
 */
public class BridgeMgrStatusEvent extends CommonCmdStatusEvent {
    private static final long serialVersionUID = -5013347940636367723L;

    /*******************************************************************************
     * BridgeMgrStatusEvent event types use integers 5000 - 5999
     *******************************************************************************/
    public enum Type {
        ;
        public static final int HELLO = 5000;
        public static final int LIST = 5001;
        public static final int START = 5002;
        public static final int STOP = 5003;
        public static final int RESUME = 5004;
        public static final int PAUSE = 5005;
        public static final int DEBUG = 5006;
    }

    /**
     * @param source the object where the event originated
     * @param type the event type
     */
    public BridgeMgrStatusEvent(Object source, int type) {
        super(source, type);
    }
}
