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

import java.lang.reflect.Method;
import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.AttributeChangeNotification;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.InvalidAttributeValueException;

import com.sun.messaging.jmq.util.log.Logger;

public abstract class MQMBeanReadWrite extends MQMBeanReadOnly {
    /**
     * Sets the value of the specified attribute of the Dynamic MBean.
     */
    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {

        if (attribute == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException("MBean " + getMBeanName() + ": Null attribute passed to setAttribute()"));
        }

        String name = attribute.getName();
        String methodName = "set" + name;
        Object value = attribute.getValue();
        Method m = null;

        checkSettableAttribute(name, value);

        try {
            /*
             * What if value is null ?
             */
            Class methodParams[] = { value.getClass() };

            m = this.getClass().getMethod(methodName, methodParams);
        } catch (NoSuchMethodException noSuchE) {
            String tmp = "MBean " + getMBeanName() + ": Cannot find method " + methodName;
            throw (new ReflectionException(noSuchE, tmp));
        } catch (SecurityException se) {
            throw (new ReflectionException(se));
        }

        try {
            Object params[] = { value };
            m.invoke(this, params);
        } catch (Exception e) {
            throw (new MBeanException(e, e.toString()));
        }
    }

    public void logProblemGettingOldVal(String attr, Exception e) {
        logger.log(Logger.ERROR, getMBeanName() + " notification " + AttributeChangeNotification.ATTRIBUTE_CHANGE
                + ": encountered problem while getting old value of attribute " + attr + ": " + e);
    }

    private void checkSettableAttribute(String name, Object value) throws AttributeNotFoundException, InvalidAttributeValueException {
        MBeanAttributeInfo attrInfo = getAttributeInfo(name);

        if (attrInfo == null) {
            throw new AttributeNotFoundException("The attribute " + name + " is not a valid attribute for MBean" + getMBeanName());
        }

        if (!attrInfo.isWritable()) {
            throw new AttributeNotFoundException("The attribute " + name + " is not a settable attribute for MBean" + getMBeanName());
        }

        if (!attrInfo.getType().equals(value.getClass().getName())) {
            throw new InvalidAttributeValueException("The type of the value used to set the attribute " + name + " is incorrect (" + value.getClass().getName()
                    + ").\n" + "The expected value type is " + attrInfo.getType() + ".");
        }
    }
}
