/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsclient;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

import jakarta.jms.*;
import javax.transaction.xa.Xid;
import javax.transaction.xa.XAResource;

import com.sun.jms.spi.xa.*;

/**
 * An XAQueueSession provides a regular QueueSession which can be used to create QueueReceivers, QueueSenders and
 * QueueBrowsers (optional).
 *
 * <P>
 * XASession extends the capability of Session by adding access to a JMS provider's support for JTA (optional). This
 * support takes the form of a <CODE>javax.transaction.xa.XAResource</CODE> object. The functionality of this object
 * closely resembles that defined by the standard X/Open XA Resource interface.
 *
 * <P>
 * An application server controls the transactional assignment of an XASession by obtaining its XAResource. It uses the
 * XAResource to assign the session to a transaction; prepare and commit work on the transaction; etc.
 *
 * <P>
 * An XAResource provides some fairly sophisticated facilities for interleaving work on multiple transactions;
 * recovering a list of transactions in progress; etc. A JTA aware JMS provider must fully implement this functionality.
 * This could be done by using the services of a database that supports XA or a JMS provider may choose to implement
 * this functionality from scratch.
 *
 * <P>
 * A client of the application server is given what it thinks is a regular JMS Session. Behind the scenes, the
 * application server controls the transaction management of the underlying XASession.
 *
 * @see jakarta.jms.XASession jakarta.jms.XASession
 * @see jakarta.jms.XAQueueSession jakarta.jms.XAQueueSession
 */

public class JMSXAWrappedQueueSessionImpl extends JMSXAWrappedXSessionImpl implements JMSXAQueueSession, JMSXAWrappedTransactionListener {
    private JMSXAWrappedQueueConnectionImpl wconn_ = null;

    public JMSXAWrappedQueueSessionImpl(QueueConnection qconn, boolean transacted, int ackMode, JMSXAWrappedQueueConnectionImpl wconn) throws JMSException {
        wconn_ = wconn;
        // An XAQueueSession is created in this case
        // Per std jakarta.jms method signatures only the createXAQueueSession() does this
        if (qconn instanceof XAQueueConnection) {
            session_ = ((XAQueueConnection) qconn).createXAQueueSession();
            xaresource_ = new JMSXAWrappedXAResourceImpl(((XASession) session_).getXAResource(), true, wconn.getJMSXAWrappedConnectionFactory(),
                    wconn.getUsername(), wconn.getPassword());

            delaySessionCloseForRAR_ = JMSXAWrappedXAResourceImpl.isSystemPropertySetFor("delaySessionCloseForExternalJMSXAResource",
                    xaresource_.getDelegatedXAResource().getClass().getName());

            ignoreSessionCloseForRAR_ = JMSXAWrappedXAResourceImpl.isSystemPropertySetFor("ignoreSessionCloseForExternalJMSXAResource",
                    xaresource_.getDelegatedXAResource().getClass().getName());

            if (delaySessionCloseForRAR_) {
                log(INFO, "Enable delay Session.close() for " + xaresource_.getDelegatedXAResource().getClass().getName());
                lock_ = new JMSXAWrappedLock();
                xaresource_.setTransactionListener(this);
            }

        } else {
            // A QueueSession is created in this case
            // Per std jakarta.jms method signatures only the createQueueSession(transacted, ackMode) can be used
            session_ = qconn.createQueueSession(transacted, ackMode);
            nonxaresource_ = new XAResourceUnsupportedImpl();
        }

    }

    @Override
    public void afterTransactionComplete(Xid foreignXid, boolean completed) {
        try {

            if (completed) {
                if (debug) {
                    if (transactions_.get(foreignXid) == null) {
                        log(WARNING, "afterTransactionComplete: transaction Xid=" + foreignXid + " not found");
                    }
                }
                transactions_.remove(foreignXid);
            }
            if (transactions_.isEmpty()) {
                if (markClosed_) {
                    dlog("All transaction completed, hard close session " + session_ + " " + session_.getClass().getName());
                    closed_ = true;
                }
            }

        } finally {
            lock_.releaseLock();
        }

        if (closed_) {
            try {
                hardClose();
            } catch (JMSException e) {
                logWarning(e);
            }
        }
    }

    @Override
    public void close() throws JMSException {

        if (delaySessionCloseForRAR_) {

            if (session_.getMessageListener() != null) {
                log(INFO, "Session MessageListener set. No delay session close for session " + session_ + " " + session_.getClass().getName());
            } else {

                lock_.acquireLock();
                try {

                    if (transactions_.isEmpty() || xaresource_ == null) {
                        closed_ = true;
                    } else {
                        markClosed_ = true;
                    }

                } finally {
                    lock_.releaseLock();
                }

                if (closed_) {
                    hardClose();
                }
                return;

            }
        }

        if (ignoreSessionCloseForRAR_) {
            hardClose(false);
            return;
        }

        hardClose();
    }

    @Override
    public XAResource getXAResource() {
        if (session_ instanceof XASession) {
            return xaresource_;
        } else {
            // Either a QueueSession or TopicSession
            // So return the dummy XAResource created above
            return nonxaresource_;
        }
    }

    @Override
    public QueueSession getQueueSession() throws JMSException {
        if (closed_) {
            throw new jakarta.jms.IllegalStateException("JMSXWrapped Session has been closed");
        }
        if (markClosed_) {
            throw new jakarta.jms.IllegalStateException("JMSXAWrapped Session is closed");
        }
        if (session_ instanceof XASession) {
            return ((XAQueueSession) session_).getQueueSession();
        } else {
            // If it's not an XASession, session_ itself is a regular QueueSession
            return (QueueSession) session_;
        }
    }

    @Override
    void removeSelfFromConnection() {
        wconn_.removeSession(this);
    }
}
