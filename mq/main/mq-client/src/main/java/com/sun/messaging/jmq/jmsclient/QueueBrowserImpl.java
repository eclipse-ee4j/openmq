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
 * @(#)QueueBrowserImpl.java	1.12 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Enumeration;
import java.util.Vector;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;

/** A client uses a QueueBrowser to look at messages on a queue without
  * removing them.
  *
  * <P>The browse methods return a java.util.Enumeration that is used to scan
  * the queue's messages. It may be an enumeration of the entire content of a
  * queue or it may only contain the messages matching a message selector.
  *
  * <P>Messages may be arriving and expiring while the scan is done. JMS does
  * not require the content of an enumeration to be a static snapshot of queue
  * content. Whether these changes are visible or not depends on the JMS
  * provider.
  *
  * @see         javax.jms.QueueSession#createBrowser(Queue)
  * @see         javax.jms.QueueSession#createBrowser(Queue, String)
  * @see         javax.jms.QueueReceiver
  */

public class QueueBrowserImpl implements QueueBrowser {

    private SessionImpl session = null;
    private Queue queue = null;
    private String messageSelector = null;
    private Vector consumers = new Vector();

    private boolean isClosed = false;

    public QueueBrowserImpl (SessionImpl session,
                             Queue queue) throws JMSException {
        this(session, queue, null);
    }

    public QueueBrowserImpl (SessionImpl session,
                             Queue queue,
                             String selector) throws JMSException {
        if (queue == null) {
            String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DESTINATION_NAME, "null");
            throw new InvalidDestinationException(errorString,
                AdministeredObject.cr.X_INVALID_DESTINATION_NAME);
        }
        this.session = session;
        this.queue = queue;
        this.messageSelector = selector;
        init();
    }

    private void init() throws JMSException {
		//ConnectionConsumer workaround 4715054
		session.checkBrowserCreation();
        session.verifyDestination(queue, messageSelector, true);
    }

    /** Get the queue associated with this queue browser.
      *
      * @return the queue
      *
      * @exception JMSException if JMS fails to get the
      *                         queue associated with this Browser
      *                         due to some JMS error.
      */

    public Queue
    getQueue() throws JMSException {
        checkState();
        return queue;
    }


    /** Get this queue browser's message selector expression.
      *
      * @return this queue browser's message selector
      *
      * @exception JMSException if JMS fails to get the
      *                         message selector for this browser
      *                         due to some JMS error.
      */

    public String
    getMessageSelector() throws JMSException {
        checkState();
        return messageSelector;
    }

    /** Get an enumeration for browsing the current queue messages in the
      * order they would be received.
      *
      * @return an enumeration for browsing the messages
      *
      * @exception JMSException if JMS fails to get the
      *                         enumeration for this browser
      *                         due to some JMS error.
      */

    public Enumeration
    getEnumeration() throws JMSException {

        checkState();

        return (Enumeration)(new BrowserConsumer(this, queue,
                                                 messageSelector));
    }

    protected void addBrowserConsumer(BrowserConsumer consumer) {
        consumers.addElement(consumer);
    }

    protected void removeBrowserConsumer(BrowserConsumer consumer) {
        consumers.removeElement(consumer);
    }

    /** Since a provider may allocate some resources on behalf of a
      * QueueBrowser outside the JVM, clients should close them when they
      * are not needed. Relying on garbage collection to eventually reclaim
      * these resources may not be timely enough.
      *
      * @exception JMSException if a JMS fails to close this
      *                         Browser due to some JMS error.
      */

    public void
    close() throws JMSException {
        BrowserConsumer consumer = null;
        for(int i = consumers.size()-1; i >= 0; i--) {
            consumer = (BrowserConsumer)consumers.elementAt(i);
            consumer.close();
        }

        isClosed = true;
    }

    protected SessionImpl getSession() {
        return session;
    }

    protected void checkState() throws JMSException {

        if ( isClosed ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_BROWSER_CLOSED);
            throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_BROWSER_CLOSED);
        }

        session.checkSessionState();

    }
}
