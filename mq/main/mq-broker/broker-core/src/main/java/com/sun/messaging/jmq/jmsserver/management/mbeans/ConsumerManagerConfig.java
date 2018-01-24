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
 * @(#)ConsumerManagerConfig.java	1.15 06/28/07
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
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.management.util.ConsumerUtil;
import com.sun.messaging.jmq.util.log.Logger;

public class ConsumerManagerConfig extends MQMBeanReadWrite  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ConsumerAttributes.NUM_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_CON_MGR_ATTR_NUM_CONSUMERS),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] purgeSignature = {
		    new MBeanParameterInfo("consumerID", String.class.getName(),
			mbr.getString(mbr.I_CON_MGR_OP_PARAM_CON_ID_DESC))
			    };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ConsumerOperations.GET_CONSUMER_IDS,
		mbr.getString(mbr.I_CON_MGR_OP_GET_CONSUMER_IDS_DESC),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ConsumerOperations.PURGE,
		mbr.getString(mbr.I_CON_MGR_OP_PURGE_DESC),
		    purgeSignature, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.INFO),


		};


    public ConsumerManagerConfig()  {
	super();
    }

    public Integer getNumConsumers()  {
        return (Integer.valueOf(ConsumerUtil.getNumConsumersNoChildren()));
    }

    public String[] getConsumerIDs() throws MBeanException  {
	int numConsumers = getNumConsumers().intValue();
	String ids[];
	Iterator consumers;

	if (numConsumers <= 0)  {
	    return (null);
	}

	ids = new String [ numConsumers ];

	consumers = (new HashSet(ConsumerUtil.getAllConsumersNoChildren().values())).iterator();

	int i = 0;
	while (consumers.hasNext()) {
	    Consumer oneCon = (Consumer)consumers.next();
	    long conID = oneCon.getConsumerUID().longValue();
	    String id;

	    try  {
	        id = Long.toString(conID);

	        ids[i] = id;
	    } catch (Exception ex)  {
		handleOperationException(ConsumerOperations.GET_CONSUMER_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public void purge(String consumerID) throws MBeanException {
	ConsumerUID cid = null;

	try  {
	    cid = new ConsumerUID(Long.parseLong(consumerID));
	} catch (Exception e)  {
	    /*
	     * XXX - should  send specific 'cannot parse consumerID' exception
	     */
            handleOperationException(ConsumerOperations.PURGE, e);
	}

	Consumer con = Consumer.getConsumer(cid);

        if (!con.isDurableSubscriber())  {
	    logger.log(Logger.INFO, 
		"Purge not supported for non durable subscribers.");
	    return;
	}

	if (con instanceof Subscription)  {
            Subscription sub = (Subscription)con;

	    try  {
		sub.purge();
	    } catch(Exception e)  {
		handleOperationException(ConsumerOperations.PURGE, e);
	    }
        }
    }


    public String getMBeanName()  {
	return ("ConsumerManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CON_MGR_CFG_DESC));
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
