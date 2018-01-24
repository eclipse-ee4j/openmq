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
 * @(#)JMSObjFactory.java	1.25 06/28/07
 */ 

package com.sun.messaging.jmq.admin.util;

import java.util.Enumeration;
import java.util.Properties;
import javax.jms.JMSException;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.InvalidPropertyException;
import com.sun.messaging.ReadOnlyPropertyException;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;

/**
 * This class creates a JMS object from a Properties object
 */

public class JMSObjFactory {

    /**
     * Create a JMS Topic.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS Topic
     * is created.
     * @return the com.sun.messaging.Topic
     */ 
    public static Object createTopic(Properties objProps) 
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.Topic();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS Queue.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS Queue
     * is created.
     * @return the com.sun.messaging.Queue
     */ 
    public static Object createQueue(Properties objProps)  
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.Queue();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS Topic Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * Topic Connection Factory is created.
     * @return the com.sun.messaging.TopicConnectionFactory
     */ 
    public static Object createTopicConnectionFactory(Properties objProps)
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.TopicConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * Topic Connection Factory is created.
     * @return the com.sun.messaging.ConnectionFactory
     */ 
    public static Object createConnectionFactory(Properties objProps)
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.ConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS XA Topic Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * XA Topic Connection Factory is created.
     * @return the com.sun.messaging.XATopicConnectionFactory
     */ 
    public static Object createXATopicConnectionFactory(Properties objProps)
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.XATopicConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS Queue Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * Queue Connection Factory is created.
     * @return the com.sun.messaging.QueueConnectionFactory
     */ 
    public static Object createQueueConnectionFactory(Properties objProps) 
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.QueueConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS XA Queue Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * XA Queue Connection Factory is created.
     * @return the com.sun.messaging.XAQueueConnectionFactory
     */ 
    public static Object createXAQueueConnectionFactory(Properties objProps) 
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.XAQueueConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    /**
     * Create a JMS XA Connection Factory.
     *
     * <P>No verification of valid param values are needed at this point
     * because the assumption is that valid values were checked before
     * this was called.
     *
     * @param props  the set of Properties to be set when the JMS 
     * XA Queue Connection Factory is created.
     * @return the com.sun.messaging.XAConnectionFactory
     */ 
    public static Object createXAConnectionFactory(Properties objProps) 
				throws JMSException {

	AdministeredObject obj = null;

	obj = (AdministeredObject)new com.sun.messaging.XAConnectionFactory();

	setProperties(obj, objProps);

	return (obj);
    }

    public static Object updateTopic(Object oldObj, Properties objProps,
				     String readOnlyValue)  
						throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.Topic();
	/*
	 * Copy the properties from old object to new object.
	 * Then set the new specified props into the new object.
	 * XXX REVISIT - What if oldObj is not instance of AdministeredObject??
	 */
	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
					readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateQueue(Object oldObj, Properties objProps,
				     String readOnlyValue)  
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.Queue();
	/*
	 * Copy the properties from old object to new object.
	 * Then set the new specified props into the new object.
	 * XXX REVISIT - What if oldObj is not instance of AdministeredObject??
	 */
	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateTopicConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.TopicConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateXATopicConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.XATopicConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateQueueConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.QueueConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.ConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateXAQueueConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.XAQueueConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    public static Object updateXAConnectionFactory(Object oldObj, 
			Properties objProps, String readOnlyValue) 
				throws JMSException {

	AdministeredObject newObj = null;
	String value;

	newObj = (AdministeredObject)new com.sun.messaging.XAConnectionFactory();

	if (oldObj instanceof AdministeredObject) {
	    updateAdministeredObject((AdministeredObject)oldObj, newObj, objProps,
				     readOnlyValue);
	}

	return (newObj);
    }

    /*
     * Set the properties on this object.
     */
    private static void setProperties(AdministeredObject obj, 
				Properties objProps) 
				throws JMSException {
	/*
	 * Set the specified properties on the new object.
	 */
	for (Enumeration e = objProps.propertyNames(); e.hasMoreElements(); ) {

	    String propName = (String)e.nextElement();
	    String value  = objProps.getProperty(propName);
	    if (value != null) {
	        try {
		    obj.setProperty(propName, value);

	        } catch (JMSException je) {
		    throw je;
 	        }
	    }
	}
    }

    /*
     *  Update a Read-Only Object:  -r true  => readOnly
     *				    -r false => RW				
     *				    no -r    => readOnly
     *
     *  Update a Read-Write Object:  -r true  => readOnly
     *				     -r false => RW				
     *				     no -r    => readWrite
     */
    private static void updateAdministeredObject(AdministeredObject oldObj,
		AdministeredObject newObj, Properties objProps,
		String readOnlyValue) throws JMSException {
 	/*
	 * Get the properties from the old object and 
	 * set them into the new object.
	 */
	 Properties oldProps = ((AdministeredObject)oldObj).getConfiguration();
	 setProperties((AdministeredObject)newObj, oldProps);

        /*
	 * Now set the new, specified props into the new object.
	 */
        setProperties(newObj, objProps);

	/*
	 * Set the read-only flag on new Object, if necessary.
	 */ 
	if (oldObj.isReadOnly() && readOnlyValue == null) {
	   newObj.setReadOnly();
	} else if (readOnlyValue != null &&
		   readOnlyValue.equalsIgnoreCase(Boolean.TRUE.toString())) {
	   newObj.setReadOnly();
	} else {
	}

    }

    /*
     * Create object with -r true  => ReadOnly
     * 		          -r false => ReadWrite
     * 		          no -r    => ReadWrite
     */
    public static void doReadOnlyForAdd(Object obj, String value)  {
	
	if (value != null && 
	    value.equalsIgnoreCase(Boolean.TRUE.toString())) {
	    ((AdministeredObject)obj).setReadOnly();
        } else {
	}
    }
}
