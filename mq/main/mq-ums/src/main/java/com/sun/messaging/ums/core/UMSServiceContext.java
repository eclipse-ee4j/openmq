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

package com.sun.messaging.ums.core;

import java.util.Hashtable;
import java.util.Properties;


/**
 * MQServiceContext is part of the MQ SOAP Service framework
 * implementation.
 *
 * Service context defines a set of methods that a SOAPService uses to
 * communicate with its MessageHandlers.
 *
 * <p>There is a context for each SOAPService instance. SOAPService
 * provider constructs this object and makes it available through
 * SOAPService interface.
 *
 * <p>This object is passed to the MessageHandler.init (ServiceContext ct)
 * method after MessageHandler is loaded by the Service provider.
 *
 * <p>The concept of this class is adopted from Java Servlet.
 *
 * @see SOAPService
 * @see MessageHandler
 */
public class UMSServiceContext implements ServiceContext {

    /**
     * hashtable to hold attributes for this context.
     */
    protected Hashtable attributes = null;

    /**
     * init properties.
     */
    protected Properties props = null;

    /**
     * default constructor.
     */
    public UMSServiceContext (Properties props) {
        this.props = props;

        attributes = new Hashtable();
    }

    /**
     * Binds an object to a given attribute name in this Service context.
     * If the name specified is already used for an attribute, this method
     * will replace the attribute with the new to the new attribute.
     *
     * <p>If a null value is passed, the effect is the same as calling
     * removeAttribute().
     *
     * <p>Attribute names should follow the same convention as package names.
     *
     * @param name   a String specifying the name of the attribute
     * @param value  an Object representing the attribute to be bound
     */
    public void setAttribute (Object key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * Returns the SOAP Service attribute with the given name, or null if
     * there is no attribute by that name.  SOAPService and its
     * MessageHandlers may use this API to share information.
     *
     * <p>The attribute is returned as a java.lang.Object or some subclass.
     *
     * @param name  a String specifying the name of the attribute.
     *
     * @return  an Object containing the value of the attribute, or null
     * if no attribute exists matching the given name
     *
     * @see getAttributeKeys
     */
    public Object getAttribute (Object name) {
        return this.attributes.get( name );
    }

    /**
     * Removes the attribute with the given name from the service context.
     * After removal, subsequent calls to getAttribute(java.lang.String)
     * to retrieve the attribute's value will return null.
     *
     * @param key an Object specifying the key of the attribute to be removed
     */
    public Object removeAttribute(Object key) {
        return this.attributes.remove(key);
    }

    /**
     * Returns an Iterator containing the attribute names available
     * within this service context. Use the getAttribute(java.lang.Object)
     * method with an attribute key to get the value of an attribute.
     *
     * @return an Iterator of attribute keys
     */
    public java.util.Iterator getAttributeKeys() {
        return this.attributes.keySet().iterator();
    }

    /**
     * Get the initialized properties from the Service context.  This is the
     * same object as the properties passed to SOAPService.init().
     *
     * @return the init properties of the SOAPService instance.
     */
    public java.util.Properties getInitProperties() {
        return this.props;
    }

}
