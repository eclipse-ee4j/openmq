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
 * @(#)ProducerManagerConfig.java	1.15 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.HashSet;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.Producer;

public class ProducerManagerConfig extends MQMBeanReadWrite  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ProducerAttributes.NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_PRD_MGR_ATTR_NUM_PRODUCERS),
					true,
					false,
					false)
			};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ProducerOperations.GET_PRODUCER_IDS,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_PRODUCER_IDS),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO)
		};

    public ProducerManagerConfig()  {
	super();
    }

    public Integer getNumProducers()  {
        return (Integer.valueOf(Producer.getNumProducers()));
    }

    public String[] getProducerIDs() throws MBeanException  {
	int numProducers = getNumProducers().intValue();
	String ids[];
	Iterator producers;

	if (numProducers <= 0)  {
	    return (null);
	}

	ids = new String [ numProducers ];

	producers = Producer.getAllProducers();
	int i = 0;
	while (producers.hasNext()) {
	    Producer oneProd = (Producer)producers.next();
	    long prodID = oneProd.getProducerUID().longValue();
	    String id;

	    try  {
	        id = Long.toString(prodID);

	        ids[i] = id;
	    } catch (Exception ex)  {
		handleOperationException(ProducerOperations.GET_PRODUCER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public String getMBeanName()  {
	return ("ProducerManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_PRD_MGR_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (null);
    }
}
