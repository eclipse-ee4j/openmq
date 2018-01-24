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
 * @(#)ProducerManagerMonitor.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.ArrayList;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;

import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Producer;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.management.util.ProducerUtil;

public class ProducerManagerMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ProducerAttributes.NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_PRD_MGR_ATTR_NUM_PRODUCERS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ProducerAttributes.NUM_WILDCARD_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_PRD_MGR_ATTR_NUM_WILDCARD_PRODUCERS),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] getProducerInfoByIDSignature = {
		    new MBeanParameterInfo("producerID", String.class.getName(), 
			mbr.getString(mbr.I_PRD_MGR_OP_PARAM_PRD_ID))
			    };

    private static MBeanParameterInfo[] numWildcardProducersSignature = {
	    new MBeanParameterInfo("wildcard", String.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_WILDCARD_PRODUCERS_DESC))
    		};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ProducerOperations.GET_PRODUCER_IDS,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_PRODUCER_IDS),
		    null , 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ProducerOperations.GET_PRODUCER_INFO,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_PRODUCER_INFO),
		    null , 
		    CompositeData[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ProducerOperations.GET_PRODUCER_INFO_BY_ID,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_PRODUCER_INFO_BY_ID),
		    getProducerInfoByIDSignature, 
		    CompositeData.class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ProducerOperations.GET_PRODUCER_WILDCARDS,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_PRODUCER_WILDCARDS),
		null , 
		String[].class.getName(),
		MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ProducerOperations.GET_NUM_WILDCARD_PRODUCERS,
		mbr.getString(mbr.I_PRD_MGR_OP_GET_NUM_WILDCARD_PRODUCERS),
		numWildcardProducersSignature , 
		Integer.class.getName(),
		MBeanOperationInfo.INFO)
		};
	
    public ProducerManagerMonitor()  {
	super();
    }

    public Integer getNumProducers()  {
        return (Integer.valueOf(Producer.getNumProducers()));
    }

    public Integer getNumWildcardProducers() throws MBeanException  {
        return(Integer.valueOf(Producer.getNumWildcardProducers()));
    }

    public Integer getNumWildcardProducers(String wildcard) throws MBeanException  {
	int numWildcardProducers = Producer.getNumWildcardProducers();

	if (numWildcardProducers <= 0)  {
	    return (Integer.valueOf(0));
	}

	Iterator producers = Producer.getWildcardProducers();

	if (producers == null)  {
	    return (Integer.valueOf(0));
	}

	int count = 0;
	while (producers.hasNext()) {
	    ProducerUID pid = (ProducerUID)producers.next();
	    Producer oneProd = (Producer)Producer.getProducer(pid);

	    /*
	     * If wildcard param is not null, check for matches
	     * If it is null, return total count of wildcards
	     */
	    if (wildcard != null)  {
	        DestinationUID id = oneProd.getDestinationUID();
	        if (id.getName().equals(wildcard))  {
	            count++;
	        }
	    } else  {
	        count++;
	    }
	}

        return (Integer.valueOf(count));
    }

    public String[] getProducerWildcards() throws MBeanException  {
	ArrayList<String> al = new ArrayList<String>();
	String[] list = null;
	int numWildcardProducers = Producer.getNumWildcardProducers();
	Iterator producers;

	if (numWildcardProducers <= 0)  {
	    return (null);
	}

	producers = Producer.getWildcardProducers();

	if (producers == null)  {
	    return (null);
	}

	while (producers.hasNext()) {
	    ProducerUID pid = (ProducerUID)producers.next();
	    Producer oneProd = (Producer)Producer.getProducer(pid);

	    DestinationUID id = oneProd.getDestinationUID();
	    al.add(id.getName());
	}

	if (al.size() > 0)  {
	    list = new String [ al.size() ];
	    list = (String[])al.toArray(list);
	}

        return (list);
    }


    public String[] getProducerIDs() throws MBeanException  {
	return (ProducerUtil.getProducerIDs());
    }

    public CompositeData[] getProducerInfo() throws MBeanException {
	CompositeData cds[] = null;

	try  {
	    cds = ProducerUtil.getProducerInfo();
	} catch(Exception e)  {
	    handleOperationException(ProducerOperations.GET_PRODUCER_INFO, e);
	}

	return (cds);
    }

    public CompositeData getProducerInfoByID(String producerID) throws MBeanException  {
	CompositeData cd = null;

	try  {
	    cd = ProducerUtil.getProducerInfo(producerID);
	} catch(Exception e)  {
	    handleOperationException(ProducerOperations.GET_PRODUCER_INFO_BY_ID, e);
	}

	return (cd);
    }

    public String getMBeanName()  {
	return ("ProducerManagerMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_PRD_MGR_MON_DESC));
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
