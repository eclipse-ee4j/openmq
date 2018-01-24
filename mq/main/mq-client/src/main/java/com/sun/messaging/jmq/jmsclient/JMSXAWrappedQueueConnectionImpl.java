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
 * @(#)JMSXAWrappedQueueConnectionImpl.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Vector;
import javax.jms.*;
import com.sun.jms.spi.xa.*;

/** An XAConnection is an active connection to a JMS provider.
  * A client uses an XAConnection to create one or more XASessions
  * for producing and consuming messages.
  *
  * @see javax.jms.XAConnectionFactory
  * @see javax.jms.XAConnection
  */

public class JMSXAWrappedQueueConnectionImpl implements JMSXAQueueConnection {

    private static final boolean debug = JMSXAWrappedConnectionFactoryImpl.debug;
    private Connection wrapped_connection;;

    private JMSXAWrappedConnectionFactoryImpl wcf_ = null;;
    private String username_ = null;
    private String password_ = null;

    private Vector sessions_ = new Vector();
    private boolean markClosed_ = false;
    private boolean closed_ = false;

    /** private constructor - disallow null constructor */
    private JMSXAWrappedQueueConnectionImpl() {}


    public JMSXAWrappedQueueConnectionImpl(QueueConnection qconn, 
                                      JMSXAWrappedConnectionFactoryImpl wcf, 
                                      String username, String password) throws JMSException {
        wrapped_connection = qconn;
        this.wcf_ = wcf;
        this.username_ = username;
        this.password_ = password;
    }

    /**
     * Create an XAQueueSession
     *  
     * @param transacted      ignored.
     * @param acknowledgeMode ignored.
     *  
     * @return a newly created XA topic session.
     *  
     * @exception JMSException if JMS Connection fails to create a
     *                         XA topic session due to some internal error.
     */ 
    public JMSXAQueueSession createXAQueueSession(boolean transacted,
                                                int acknowledgeMode) throws JMSException {
        synchronized(sessions_) {

        if (closed_)  {
            throw new javax.jms.IllegalStateException("JMSXWrapped Connection has been closed");
        }

        if (markClosed_)  {
            throw new javax.jms.IllegalStateException("JMSXAWrapped Connection is closed");
        }

        JMSXAQueueSession s = (JMSXAQueueSession) (new JMSXAWrappedQueueSessionImpl(
                                                         (QueueConnection)wrapped_connection,
                                                          transacted, acknowledgeMode, this));

        if (((JMSXAWrappedQueueSessionImpl)s).delaySessionClose()) sessions_.add(s);

        return s;
        }
    }


    /**
     * get a QueueConnection associated with this XAQueueConnection object.
     *  
     * @return a QueueConnection.
     */ 
    public QueueConnection getQueueConnection() {
        return (QueueConnection) wrapped_connection;
    }

    public void close() throws JMSException {
        dlog("closing "+wrapped_connection+" "+wrapped_connection.getClass().getName());
        synchronized(sessions_) {
           if (sessions_.isEmpty()) {
               closed_ = true;
           } else {
               markClosed_ = true; 
           }
        }
        if (closed_) {
           hardClose();
        }
    }

    private void hardClose() throws JMSException {
        dlog("hard closing "+wrapped_connection+" "+wrapped_connection.getClass().getName());
        wrapped_connection.close();
        closed_ = true;
        dlog("hard closed "+wrapped_connection+" "+wrapped_connection.getClass().getName());
    }

    protected void removeSession(JMSXAWrappedQueueSessionImpl s) {
        synchronized(sessions_) {
           sessions_.remove(s);
           if (sessions_.isEmpty() && markClosed_) {
               dlog("All sessions closed, hard close connection "
                   +wrapped_connection+" "+wrapped_connection.getClass().getName());
               closed_ = true;
            }
        }
        if (closed_) { 
           try {
           hardClose();
           } catch (JMSException e) {
           log("Warning:", e);
           }
        }
    }

    protected JMSXAConnectionFactory getJMSXAWrappedConnectionFactory() {
        return wcf_;
    }

    protected String getUsername() {
        return username_;
    }

    protected String getPassword() {
        return password_;
    }

    private final static void dlog(String msg) {
        if (debug) log("Info:", msg);
    }

    private final static void log(String level, Exception e) {
        log(level, e.getMessage());
        e.printStackTrace();
    }
    private final static void log(String level, String msg) {
        System.out.println(level+ " "+"JMSXAWrappedQueueConnectionImpl: " + msg);
    }

}
