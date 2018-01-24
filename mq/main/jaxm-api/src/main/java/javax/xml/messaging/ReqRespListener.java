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

package javax.xml.messaging;

import javax.xml.soap.*;

/**
 * A marker interface for components that are 
 * intended to be consumers of request-response messages.
 * In the request-response style of messaging, sending a request and receiving
 * the response are both done in a single operation. This means that the 
 * client sending the request cannot do anything else until after it has
 * received the response.
 * <P>
 * From the standpoint of the
 * sender, a message is sent via the <code>SOAPConnection</code> method
 * <code>call</code> in a point-to-point fashion.  The method <code>call</code>
 * blocks, waiting until it gets a  response message that it can return.  
 * The sender may be a standalone client, or it may be deployed in a container.
 * <P>
 * The receiver, typically a service operating in a servlet, implements the 
 * <code>ReqRespListener</code> method <code>onMessage</code> to specify
 * how to respond to the requests it receives.
 *<P>
 * It is possible that a standalone client might use the method <code>call</code>
 * to send a message that does not require a response.  For such cases,
 * the receiver must implement the method <code>onMessage</code> such that 
 * it returns a message whose only purpose is to unblock the 
 * <code>call</code> method.
 *
 * @see JAXMServlet
 * @see OnewayListener
 * @see javax.xml.soap.SOAPConnection#call
 */
public interface ReqRespListener {

    /**
     * Passes the given <code>SOAPMessage</code> object to this
     * <code>ReqRespListener</code> object and returns the response.  
     * This method is invoked behind the scenes, typically by the
     * container (servlet or EJB container) after the messaging provider
     * delivers the message to the container. 
     *
     * It is expected that EJB Containers will deliver JAXM messages
     * to EJB components using message driven Beans that implement the
     * <code>javax.xml.messaging.ReqRespListener</code> interface.
     *
     * @param message the <code>SOAPMessage</code> object to be passed to this
     *                <code>ReqRespListener</code> object
     *
     * @return the response. If this is <code>null</code>, then the
     *         original message is treated as a "oneway" message.
     */
    public SOAPMessage onMessage(SOAPMessage message);
}
