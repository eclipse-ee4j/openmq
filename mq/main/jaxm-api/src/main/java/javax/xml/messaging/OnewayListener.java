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
 * A marker interface for components (for example, servlets) that are 
 * intended to be consumers of one-way (asynchronous) JAXM messages.  
 * The receiver of a one-way message is sent the message in one operation,
 * and it sends the response in another separate operation. The time
 * interval between the receipt of a one-way message and the sending
 * of the response may be measured in fractions of seconds or days.
 * <P>
 * The implementation of the <code>onMessage</code> method defines
 * how the receiver responds to the <code>SOAPMessage</code> object
 * that was passed to the <code>onMessage</code> method.
 *
 * @see JAXMServlet
 * @see ReqRespListener
 */
public interface OnewayListener {

    /**
     * Passes the given <code>SOAPMessage</code> object to this
     * <code>OnewayListener</code> object.  
     * This method is invoked behind the scenes, typically by the
     * container (servlet or EJB container) after the messaging provider
     * delivers the message to the container. 
     *
     * It is expected that EJB Containers will deliver JAXM messages
     * to EJB components using message driven Beans that implement the
     * <code>javax.xml.messaging.OnewayListener</code> interface.
     *
     * @param message the <code>SOAPMessage</code> object to be passed to this
     *                <code>OnewayListener</code> object
     */
    public void onMessage(SOAPMessage message);
}
