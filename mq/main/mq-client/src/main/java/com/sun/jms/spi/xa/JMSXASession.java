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
 * @(#)JMSXASession.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

import javax.jms.*;
import javax.transaction.xa.XAResource;

/** XASession provides access to a JMS provider's support for JTA (optional) 
  * and the associated Session. This support takes the form of a 
  * <CODE>javax.transaction.xa.XAResource</CODE> object. The functionality of 
  * this object closely resembles that defined by the standard X/Open XA 
  * Resource interface.
  *
  * <P>An application server controls the transactional assignment of an 
  * JMSXASession by obtaining its XAResource. It uses the XAResource to assign 
  * the session to a transaction; prepare and commit work on the
  * transaction; etc.
  *
  * <P>An XAResource provides some fairly sophisticated facilities for 
  * interleaving work on multiple transactions; recovering a list of 
  * transactions in progress; etc. A JTA aware JMS provider must fully 
  * implement this functionality. This could be done by using the services 
  * of a database that supports XA or a JMS provider may choose to implement 
  * this functionality from scratch.
  *
  * <P>A client of the application server is given what it thinks is a 
  * regular JMS Session. Behind the scenes, the application server controls 
  * the transaction management of the underlying JMSXASession.
  * 
  * @see         com.sun.jms.xa.spi.JMSXAQueueSession
  * @see         com.sun.jms.xa.spi.JMSXATopicSession
  */ 
 
public interface JMSXASession {

    /** Return an XA resource to the caller.
      *
      * @return an XA resource to the caller
      */

     XAResource
     getXAResource();


    /*
     * return a Session associated with this XASession object.
     *
     * @return a Session to the caller
     *  
     * @exception JMSException if a JMS error occurs.
     */

    Session getSession() throws JMSException;
    
    /*
     * close the JMSXASession.
     *
     * @exception JMSException if a JMS error occurs.
     */
    void close() throws JMSException;
}
