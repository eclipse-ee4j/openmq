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
 * @(#)JVMMonitor.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;

import com.sun.messaging.jms.management.server.*;

public class JVMMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(JVMAttributes.FREE_MEMORY,
					Long.class.getName(),
					mbr.getString(mbr.I_JVM_ATTR_FREE_MEMORY),
					true,
					false,
					false),

	    new MBeanAttributeInfo(JVMAttributes.INIT_MEMORY,
					Long.class.getName(),
					mbr.getString(mbr.I_JVM_ATTR_INIT_MEMORY),
					true,
					false,
					false),

	    new MBeanAttributeInfo(JVMAttributes.MAX_MEMORY,
					Long.class.getName(),
					mbr.getString(mbr.I_JVM_ATTR_MAX_MEMORY),
					true,
					false,
					false),

	    new MBeanAttributeInfo(JVMAttributes.TOTAL_MEMORY,
					Long.class.getName(),
					mbr.getString(mbr.I_JVM_ATTR_TOTAL_MEMORY),
					true,
					false,
					false)
			};

    public JVMMonitor()  {
        super();
    }

    public Long getFreeMemory()  {
	return (Long.valueOf(Runtime.getRuntime().freeMemory()));
    }

    public Long getInitMemory()  {
	return (Long.valueOf(0));
    }

    public Long getMaxMemory()  {
	return (Long.valueOf(Runtime.getRuntime().maxMemory()));
    }

    public Long getTotalMemory()  {
	return (Long.valueOf(Runtime.getRuntime().totalMemory()));
    }

    public String getMBeanName()  {
	return ("JVMMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_JVM_MON_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (null);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (null);
    }
}
