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


/**
 * The MessageHandler is part of the MQ SOAP Messaging Service framework.
 *
 * <p>A MessageHandler is a component to process SOAP messages.  For example,
 * a handler could be designed to process a specific set of SOAP headers.
 *
 * <p>More than one MessageHandler can be formed in a MessageHandler chain.
 *
 * <p>The difference between this interface to JAXM OnewayListener is that
 * the method processMessage() throws SOAPException.  This is required
 * because the implementation is part of the MQ SOAP message processing
 * model, and SOAPException is likely to be generated during the header
 * processing phase.
 *
 * <p>After MQ framework finished processing one MessageHandler, the message is
 * forwarded to the next MessageHandler.
 *
 * @author  chiaming yang
 * @see     MessageHandlerChain
 * @see     SOAPService
 * @see     ServiceContext
 * @see     MessageHandlerException
 */
public interface MessageHandler {

    /**
     * initialize the message handler with the current ServiceContext.
     * This method is called by SOAPService provider after the
     * MessageHandler is loaded to JVM.
     *
     * @throw MessageHandlerException if unable to initialize this
     *        handler.
     */
    public void
    init (ServiceContext context) throws MessageHandlerException;

    /**
     * Process the message context passed in the parameter.
     * @param context the message context to be processed.
     *
     * @throw SOAPException if any internal error when processing the message.
     */
    public void
    processMessage (MessageContext context) throws MessageHandlerException;

    /**
     * Close the message handler.  This method is called when SOAPService
     * is closed. MessageHandler SHOULD free all the resources it allocates.
     */
    public void close();

}

