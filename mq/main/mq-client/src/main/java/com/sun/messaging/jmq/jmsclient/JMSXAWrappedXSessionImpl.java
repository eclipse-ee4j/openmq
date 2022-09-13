/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Hashtable;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.jms.JMSException;
import jakarta.jms.Session;

abstract class JMSXAWrappedXSessionImpl {
    static final boolean debug = JMSXAWrappedConnectionFactoryImpl.debug;

    boolean delaySessionCloseForRAR_ = false;

    boolean ignoreSessionCloseForRAR_ = false;

    JMSXAWrappedLock lock_ = null;

    boolean closed_ = false;

    boolean markClosed_ = false;

    Hashtable transactions_ = new Hashtable();

    Session session_;

    XAResource nonxaresource_ = null;

    JMSXAWrappedXAResourceImpl xaresource_ = null;

    final boolean delaySessionClose() {
        return delaySessionCloseForRAR_;
    }

    public final void beforeTransactionStart() throws JMSException {
        lock_.acquireLock();
        if (closed_) {
            throw new jakarta.jms.IllegalStateException("JMSXWrapped Session has been closed");
        }
        if (markClosed_) {
            throw new jakarta.jms.IllegalStateException("JMSXAWrapped Session is closed");
        }
    }

    public final void afterTransactionStart(Xid foreignXid, boolean started) {
        if (started) {
            transactions_.put(foreignXid, "");
        }
        lock_.releaseLock();
    }

    public final void beforeTransactionComplete() {
        lock_.acquireLock();
    }

    final void hardClose() throws JMSException {
        hardClose(true);
    }

    final void hardClose(boolean hard) throws JMSException {
        session_.close();
        dlog("hard closed session:" + session_ + " " + session_.getClass().getName());
        if (xaresource_ != null && hard) {
            xaresource_.close();
        }
        closed_ = true;
        if (delaySessionCloseForRAR_) {
            removeSelfFromConnection();
        }
    }

    abstract void removeSelfFromConnection();

    static void dlog(String msg) {
        if (debug) {
            log("Info:", msg);
        }
    }

    static void log(String level, Exception e) {
        log(level, e.getMessage());
        e.printStackTrace();
    }

    static void log(String level, String msg) {
        System.out.println(level + " " + "JMSXAWrappedQueueSessionImpl: " + msg);
    }

    public final Session getSession() throws JMSException {
        if (closed_) {
            throw new jakarta.jms.IllegalStateException("JMSXWrapped Session has been closed");
        }
        if (markClosed_) {
            throw new jakarta.jms.IllegalStateException("JMSXAWrapped Session is closed");
        }
        return session_;
    }
}
