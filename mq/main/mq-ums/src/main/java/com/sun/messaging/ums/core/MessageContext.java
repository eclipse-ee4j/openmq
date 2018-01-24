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

import javax.xml.soap.SOAPMessage;

/**
 * Message context is an object that associates with a specific message in
 * the life time of a SOAP Service message processing cycle.
 * <p>
 * A SOAP service message processing cycle is defined as follows:
 * A message that *flows* through A SOAP Service's request handler chain,
 * get processed (by a service provider), and *flows* through the service's
 * response handler chain.
 * <p>
 * A message context instance is obtained from the ServiceContext object in a
 * SOAPService.
 * <p>
 * A message context instance is removed after a SOAPService finished
 * processing the last Response MessageHandler.
 * <p>
 * Message context may be used by Message Handlers to communicate with each
 * other for the life time of a message processing cycle in a SOAP service.
 * <p>
 * Information that needs to be kept longer than a message processing cycle
 * should be set as attributes in the ServiceContext.
 *
 * @see ServiceContext
 * @see MessageHandler
 */
public interface MessageContext {

    /**
     * Binds an object to a given attribute name in this message context.
     * If the name specified is already used for an attribute, this method
     * will replace the attribute with the new to the new attribute.
     *
     * <p>If a null value is passed, the effect is the same as calling
     * removeAttribute().
     *
     * <p>Attribute names should follow the same convention as package names.
     *
     * @param key   an Objetc specifying the key of the attribute
     * @param value  an Object representing the attribute to be bound
     */
    public void setAttribute (Object key, Object value);

    /**
     * Returns the attribute with the given name, or null if
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
    public Object getAttribute (Object key);

    /**
     * Removes the attribute with the given name from the message context.
     * After removal, subsequent calls to getAttribute(java.lang.String)
     * to retrieve the attribute's value will return null.
     *
     * @param name a String specifying the name of the attribute to be removed
     */
    public Object removeAttribute(Object key);

    /**
     * Returns an Iterator containing the attribute names available
     * within this message context. Use the getAttribute(java.lang.String)
     * method with an attribute name to get the value of an attribute.
     *
     * @return an Iterator of attribute keys
     */
    public java.util.Iterator getAttributeKeys();

    /**
     * Get request message for this message context.
     *
     * @return request message associated with this message context.
     */
    public SOAPMessage getRequestMessage();

    /**
     * Set request message for this message context.
     *
     * @param message the request message associated with this context.
     */
    public void setRequestMessage (SOAPMessage message);

    /**
     * Get response message for this message context.
     *
     * @return response message associated with this message context.
     */
    public SOAPMessage getResponseMessage();

    /**
     * Set response message for this message context.
     *
     * @param message the response message associated with this context.
     */
    public void setResponseMessage(SOAPMessage message);

}
