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

import java.util.Vector;


/**
 * The MessageHandlerChain is part of the MQ SOAP Messaging Service framework.
 *
 * <p>A MessageHandlerChain is a list of MessageHandlers that process each
 * SOAPMessage in sequence.  A SOAP message *flow* through each MessageHandler
 * registered in the MessageHandlerChain.
 *
 * <p>MessageHandlerChian is used in the SOAPService.  A SOAPService defines
 * two message handler chains - ReqHandlerChain and RespHandlerChain.
 *
 * @author  chiaming yang
 * @see MessageHandler
 * @see SOAPService
 */
public class MessageHandlerChain {

    private Vector handlerChain = new Vector();

    /**
     * Add a MessageHandler to the message handler chain.
     */
    public void addMessageHandler (MessageHandler handler) {
            handlerChain.add(handler);
    }

    /**
     * Add a MessageHandler to the message handler chain at the specified
     * index.
     */
    public void addMessageHandlerAt (int index, MessageHandler handler) {
            handlerChain.add(index, handler);
    }

    /**
     * Get MessageHandler from the handler chain at the specified index.
     */
    public MessageHandler getMessageHandlerAt (int index) {
        return (MessageHandler) handlerChain.get( index );
    }

    /**
     * Get all message handlers from this handler chain.
     *
     * @return an array of MessageHandlers in the handler chain.
     */
    public Object[] getMessageHandlers () {
        return handlerChain.toArray();
    }

    /**
     * Remove the message handler from the hendler chain.
     */
    public boolean removeMessageHandler (MessageHandler handler) {
        return handlerChain.remove( handler );
    }

    /**
     * Deletes the message handler at the specified index.
     *
     * @throws ArrayIndexOutOfBoundsException - if the index was invalid
     */
    public void removeMessageHandlerAt (int index) {
        handlerChain.remove( index );
    }

    /**
     * Remove all message handlers in this chain.
     */
    public void removeAllMessageHandlers() {
        handlerChain.clear();
    }

    /**
     * Get the number of Message Handlers in this chain.
     *
     * @return count of message handlers in this chain.
     */
    public int size() {
        return handlerChain.size();
    }
}
