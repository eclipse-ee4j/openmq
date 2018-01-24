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
 * @(#)MQMBeanReadOnly.java	1.18 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Enumeration;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.ObjectName;
import javax.management.DynamicMBean;
import javax.management.NotificationBroadcasterSupport;
import javax.management.MBeanInfo;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.InvalidAttributeValueException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.management.mbeans.resources.MBeanResources;
import com.sun.messaging.jmq.util.log.Logger;

public abstract class MQMBeanReadOnly extends NotificationBroadcasterSupport 
					implements DynamicMBean {
    int sequenceNumber = 0;
    protected Logger logger = Globals.getLogger();

    private String dClassName = this.getClass().getName();

    protected static final BrokerResources rb = Globals.getBrokerResources();
    protected static final MBeanResources mbr = Globals.getMBeanResources();

    private MBeanInfo dMBeanInfo = null;
    protected DestinationList DL = Globals.getDestinationList();

    public MQMBeanReadOnly()  {
        buildDynamicMBeanInfo();
    }

    public abstract String getMBeanName();

    public abstract String getMBeanDescription();

    public abstract MBeanAttributeInfo[] getMBeanAttributeInfo();

    public abstract MBeanOperationInfo[] getMBeanOperationInfo();

    public abstract MBeanNotificationInfo[] getMBeanNotificationInfo();

    /**
     * Sets the value of the specified attribute of the Dynamic MBean.
     */
    public void setAttribute(Attribute attribute) 
	throws AttributeNotFoundException,
	       InvalidAttributeValueException,
	       MBeanException, 
	       ReflectionException {

	String name = null;

	if (attribute != null)  {
	    name = attribute.getName();
	}

	/*
	 * Throw exception since there are no settable attributes
	 */
	throw(new AttributeNotFoundException(
			"Attribute " 
			+ name 
			+ " cannot be set in MBean: "
			+ getMBeanName()));
    }

    /**
     * Sets the values of several attributes of the Dynamic MBean, and returns the
     * list of attributes that have been set.
     */
    public AttributeList setAttributes(AttributeList attributes)  {
	if (attributes == null)  {
	    throw new RuntimeOperationsException(
		new IllegalArgumentException(
			"Null attribute list passed to setAttributes()"));
        }
	
	AttributeList resultList = new AttributeList();

	if (attributes.isEmpty())  {
	    return resultList;
	}

	for (Iterator i = attributes.iterator(); i.hasNext();) {
	    Attribute attr = (Attribute) i.next();

	    try  {
		setAttribute(attr);
		String name = attr.getName();
		Object value = getAttribute(name);
		resultList.add(new Attribute(name, value));
	    } catch (Exception e)  {
	        String tmp = "MBean "
		    + getMBeanName()
		    + ": Problem encountered while setting attribute "
		    + attr.getName()
		    + ": "
		    + e.toString();
	        logger.log(Logger.WARNING, tmp, e);
	    }
	}

	return (resultList);
    }

    /**
     * Allows an operation to be invoked on the Dynamic MBean.
     */
	public Object invoke(String operationName, Object[] params, String[] signature) 
				throws MBeanException, ReflectionException {
	if (operationName == null) {
	    throw new RuntimeOperationsException(new IllegalArgumentException(
			"Null Operation name passed to invoke()"));
	}

	MBeanOperationInfo op = getOperationInfo(operationName, signature);
	Method m = null;
	Object ret = null;

	if (op == null)  {
	    /*
	     * If this operation was not defined as an operation in this
	     * MBean, perhaps, it was defined as an 'isIs' attribute.
	     */
	    m = getIsIsMethod(operationName);

	    if (m == null)  {
	        throw new RuntimeOperationsException(new IllegalArgumentException(
			"MBean "
			+ getMBeanName()
			+ ": Invalid operation name and/or signature passed to invoke(): "
			    + operationName)
			);
	    }
	} else  {
	    m = getMethod(op);
	}

	try  {
	    if (m != null)  {
	    	final Method finalM = m;
	    	final Object[] finalParams = params;
	    	final MQMBeanReadOnly receiver = this;
	        try {
	        	ret = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
						public Object run() throws IllegalAccessException, IllegalArgumentException,
								InvocationTargetException {
							return finalM.invoke(receiver, finalParams);
						}
					}
	              );
	        } catch (PrivilegedActionException e) {
	        	throw e.getException();
	        }
	    }
	} catch(Exception e)  {
	    throw (new MBeanException(e, "Exception caught while invoking operation "
			+ operationName
			+ " in MBean "
			+ getMBeanName()));
	}

	return (ret);
    }

    private Method getMethod(MBeanOperationInfo op) throws ReflectionException {
	Method m = null;
	String methodName = null;

	if (op == null)  {
	    return (null);
	}

	try  {
	    Class methodParams[] = null;
	    MBeanParameterInfo params[] = op.getSignature();

	    methodName = op.getName();

	    if ((params != null) && (params.length > 0))  {
	        methodParams = new Class [ params.length ];

	        for (int i = 0; i < params.length; ++i)  {
		    String className = params[i].getType();

		    try  {
		        Class paramClass = Class.forName(className);
	                methodParams[i] = paramClass;
		    } catch (ClassNotFoundException cnfE)  {
	                throw (new ReflectionException(cnfE,
			      "Parameter type/class not found for operation"
			      + methodName
			      + " in MBean "
		              + getMBeanName()));
		    }
	        }
	    }

	    m = this.getClass().getMethod(methodName, methodParams);
	} catch(NoSuchMethodException noSuchE)  {
	    throw (new ReflectionException(noSuchE,
		  "Operation " 
		  + methodName 
		  + " does not exist in MBean"
		  + getMBeanName()));
	} catch(SecurityException se)  {
	    throw (new ReflectionException(se));
	}

	return (m);
    }

    private Method getIsIsMethod(String operationName) throws ReflectionException {
	Method m = null;
	String methodName = null;

	if (operationName == null)  {
	    return (null);
	}

	/*
	 * Check first if this operation name is:
	 *	"is" + <an attribute name>
	 */
	String attr = operationName.substring(2);
	try  {
	    checkIsIsAttribute(attr);
	} catch(Exception e)  {
	    return (null);
	}

	methodName = operationName;

	try  {
	    /*
	     * The "is" method does not have any params
	     */
	    m = this.getClass().getMethod(methodName, null);
	} catch(NoSuchMethodException noSuchE)  {
	    throw (new ReflectionException(noSuchE,
		  "Operation " 
		  + methodName 
		  + " does not exist in MBean"
		  + getMBeanName()));
	} catch(SecurityException se)  {
	    throw (new ReflectionException(se));
	}

	return (m);
    }


    /**
     * Enables the to get the values of several attributes of the Dynamic MBean.
     */
    public AttributeList getAttributes(String[] attributeNames)  {
	if (attributeNames == null) {
	    throw new RuntimeOperationsException(
		new IllegalArgumentException(
			"MBean "
			+ getMBeanName()
			+ ": Null attribute list passed to getAttributes()"));
        }

	AttributeList resultList = new AttributeList();

	if (attributeNames.length == 0)
	    return resultList;
	
	for (int i=0; i < attributeNames.length; i++)  {
	    try  {
	        Object value = getAttribute(attributeNames[i]);
		resultList.add(new Attribute(attributeNames[i], value));
	    } catch (Exception e)  {
	        String tmp = "MBean "
		    + getMBeanName()
		    + ": Problem encountered while getting attribute "
		    + attributeNames[i]
		    + ": "
		    + e.toString();
	        logger.log(Logger.WARNING, tmp, e);
	    }
	}
	
	return (resultList);
    }

    /**
     * Allows the value of the specified attribute of the Dynamic MBean to be obtained.
     */
    public Object getAttribute(String attributeName) throws
				AttributeNotFoundException,
				MBeanException,
				ReflectionException  {

	if (attributeName == null) {
	    throw new RuntimeOperationsException(
		new IllegalArgumentException(
			"MBean "
			+ getMBeanName()
			+ ": Null attribute passed to getAttribute()"));
        }

	checkReadableAttribute(attributeName);

	Method m = null;
	String methodName = "get" + attributeName;
	Object ret = null;

	try  {
	    Class methodParams[] = null;

	    m = this.getClass().getMethod(methodName, methodParams);
        } catch(NoSuchMethodException noSuchE)  {
	    String tmp = "MBean "
		+ getMBeanName()
	        + ": Cannot find method "
                + methodName;
            throw (new ReflectionException(noSuchE, tmp));
        } catch(SecurityException se)  {
            throw (new ReflectionException(se));
        }

	try  {
	    Object params[] = null;

	    ret = m.invoke(this, params);
	} catch(Exception e) { 
	    throw (new MBeanException(e)); 
	}

	return (ret);
    }

    private void checkReadableAttribute(String name) throws
		AttributeNotFoundException {
        MBeanAttributeInfo attrInfo = getAttributeInfo(name);

	if (attrInfo == null)  {
	    throw new AttributeNotFoundException("The attribute "
			+ name
			+ " is not a valid attribute for MBean"
			+ getMBeanName());
	}

	if (!attrInfo.isReadable())  {
	    throw new AttributeNotFoundException("The attribute "
			+ name
			+ " is not a gettable attribute for MBean"
			+ getMBeanName());
	}
    }

    private void checkIsIsAttribute(String name) throws
		AttributeNotFoundException {
        MBeanAttributeInfo attrInfo = getAttributeInfo(name);

	if (attrInfo == null)  {
	    throw new AttributeNotFoundException("The attribute "
			+ name
			+ " is not a valid attribute for MBean"
			+ getMBeanName());
	}

	if (!attrInfo.isIs())  {
	    throw new AttributeNotFoundException("The attribute "
			+ name
			+ " is not an isIs attribute for MBean"
			+ getMBeanName());
	}
    }

    public MBeanAttributeInfo getAttributeInfo(String name)  {
        MBeanAttributeInfo[] attrs = getMBeanAttributeInfo();

	if (attrs == null)  {
	    return (null);
	}

	for (int i = 0; i < attrs.length; ++i)  {
	    MBeanAttributeInfo oneAttr = attrs[i];

	    if (oneAttr.getName().equals(name))  {
		return (oneAttr);
	    }
	}

	return (null);
    }

    public MBeanOperationInfo getOperationInfo(String operationName,
			String[] signature)  {
        MBeanOperationInfo[] ops = getMBeanOperationInfo();

	if (ops == null)  {
	    return (null);
	}

	for (int i = 0; i < ops.length; ++i)  {
	    MBeanOperationInfo oneOp = ops[i];

	    if (oneOp.getName().equals(operationName))  {
		if (operationSignatureOK(oneOp, signature))  {
		    return (oneOp);
		}
	    }
	}

	return (null);
    }

    /*
     * Returns true if the number of parameters in 'signature'
     * and their types match that in 'op'.
     *
     * TBD: instead of making the types match, shouldn't they be
     * assignable ?
     */
    private boolean operationSignatureOK(MBeanOperationInfo op, 
						String[] signature)  {
	if (op == null)  {
	    return (false);
	}

	MBeanParameterInfo params[] = op.getSignature();

	/*
	 * Operation info has no parameters.
	 */
	if (params == null)  {
	    /*
	     * signature requested has params - no match
	     */
	    if (signature != null)  {
		return (false);
	    }

	    return (true);
	}

	/*
	 * Operation info has parameters
	 */

	/*
	 * Return false if signature requested has no params.
	 */
	if (signature == null)  {
	    return (false);
	}

	/*
	 * Return false if number of params don't match.
	 */
	if (params.length != signature.length)  {
	    return (false);
	}

	/*
	 * Compare each and every param
	 */
	for (int i = 0; i < params.length; ++i)  {
	    String paramType = params[i].getType();

	    if (signature[i] == null)  {
		return (false);
	    }

	    if (!signature[i].equals(paramType))  {
		return (false);
	    }
	}

	return (true);
    }

    public MBeanInfo getMBeanInfo() {

	// return the information we want to expose for management:
	// the dMBeanInfo private field has been built at instanciation time,
	return dMBeanInfo;
    }

    public MBeanConstructorInfo[] getMBeanConstructorInfo()  {
	Constructor[] constructors = this.getClass().getConstructors();
        MBeanConstructorInfo[] dConstructors = new MBeanConstructorInfo[1];
	dConstructors[0] = new MBeanConstructorInfo(
				"Constructor", constructors[0]);
	return (dConstructors);
    }

    protected void handleGetterException(String attrName, Throwable t)
					throws MBeanException  {
	String tmp;

	tmp = getMBeanName()
		+ ": Problem encountered while getting attribute "
		+ attrName
		+ ": "
		+ t.toString();
	logger.log(Logger.WARNING, 
		tmp, t);

	throw new MBeanException(new Exception(tmp));
    }

    protected void handleSetterException(String attrName, Throwable t) 
					throws MBeanException  {
	String tmp;

	tmp = getMBeanName()
		+ ": Problem encountered while setting attribute "
		+ attrName
		+ ": "
		+ t.toString();
	logger.log(Logger.WARNING, 
		tmp, t);
	
	throw new MBeanException(new Exception(tmp));
    }

    protected void handleOperationException(String opName, Exception e) 
					throws MBeanException  {
	String tmp;

	tmp = "MBean "
		+ getMBeanName()
		+ ": Problem encountered while invoking operation "
		+ opName
		+ ": "
		+ e.toString();
	logger.log(Logger.WARNING, tmp);
	
	throw new MBeanException(new Exception(tmp));
    }

    /**
     * Build the private dMBeanInfo field,
     * which represents the management interface exposed by the MBean;
     * that is, the set of attributes, constructors, operations and notifications
     * which are available for management. 
     *
     * A reference to the dMBeanInfo object is returned by the getMBeanInfo() method
     * of the DynamicMBean interface. Note that, once constructed, an MBeanInfo object is immutable.
     */
    private void buildDynamicMBeanInfo() {
	dMBeanInfo = new MBeanInfo(dClassName,
				   getMBeanDescription(),
				   getMBeanAttributeInfo(),
				   getMBeanConstructorInfo(),
				   getMBeanOperationInfo(), 
				   getMBeanNotificationInfo());
    }
}
