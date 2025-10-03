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

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import com.sun.messaging.jms.management.server.*;

public class LogMonitor extends MQMBeanReadOnly {
    private static String[] logNotificationTypes = { LogNotification.LOG_LEVEL_PREFIX + LogLevel.WARNING, LogNotification.LOG_LEVEL_PREFIX + LogLevel.ERROR,
            LogNotification.LOG_LEVEL_PREFIX + LogLevel.INFO };

    private static MBeanNotificationInfo[] notifs = {
            new MBeanNotificationInfo(logNotificationTypes, LogNotification.class.getName(), mbr.getString(mbr.I_LOG_NOTIFICATIONS)) };

    @Override
    public String getMBeanName() {
        return ("LogMonitor");
    }

    @Override
    public String getMBeanDescription() {
        return (mbr.getString(mbr.I_LOG_MON_DESC));
    }

    @Override
    public MBeanAttributeInfo[] getMBeanAttributeInfo() {
        return (null);
    }

    @Override
    public MBeanOperationInfo[] getMBeanOperationInfo() {
        return (null);
    }

    @Override
    public MBeanNotificationInfo[] getMBeanNotificationInfo() {
        return (notifs);
    }

    public void notifyLogMessage(String level, String msg) {
        LogNotification n;
        n = new LogNotification(LogNotification.LOG_LEVEL_PREFIX + level, this, sequenceNumber++);

        n.setMessage(msg);
        n.setLevel(level);

        sendNotification(n);
    }
}
