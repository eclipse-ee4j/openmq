/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;

import javax.transaction.xa.XAResource;

/** The {@code XASession} interface extends the capability of 
  * {@code Session} by adding access to a JMS provider's support for the
  * Java Transaction API (JTA) (optional). This support takes the form of a 
  * {@code javax.transaction.xa.XAResource} object. The functionality of 
  * this object closely resembles that defined by the standard X/Open XA 
  * Resource interface.
  *
  * <P>An application server controls the transactional assignment of an 
  * {@code XASession} by obtaining its {@code XAResource}. It uses 
  * the {@code XAResource} to assign the session to a transaction, prepare 
  * and commit work on the transaction, and so on.
  *
  * <P>An {@code XAResource} provides some fairly sophisticated facilities 
  * for interleaving work on multiple transactions, recovering a list of 
  * transactions in progress, and so on. A JTA aware JMS provider must fully 
  * implement this functionality. This could be done by using the services 
  * of a database that supports XA, or a JMS provider may choose to implement 
  * this functionality from scratch.
  *
  * <P>A client of the application server is given what it thinks is a 
  * regular JMS {@code Session}. Behind the scenes, the application server 
  * controls the transaction management of the underlying 
  * {@code XASession}.
  *
  * <P>The {@code XASession} interface is optional.  JMS providers 
  * are not required to support this interface. This interface is for 
  * use by JMS providers to support transactional environments. 
  * Client programs are strongly encouraged to use the transactional support
  * available in their environment, rather than use these XA
  * interfaces directly. 
  *
  * @see javax.jms.Session
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */
 
public interface XASession extends Session {

   /** Gets the session associated with this {@code XASession}.
      *  
      * @return the  session object
      *  
      * @exception JMSException if an internal error occurs.
      *
      * @since JMS 1.1
      */ 
     Session
     getSession() throws JMSException;
  
    /** Returns an XA resource to the caller.
      *
      * @return an XA resource to the caller
      */

     XAResource
     getXAResource();

    /** Indicates whether the session is in transacted mode.
      *  
      * @return true
      *  
      * @exception JMSException if the JMS provider fails to return the 
      *                         transaction mode due to some internal error.
      */ 

    boolean
    getTransacted() throws JMSException;


    /** Throws a {@code TransactionInProgressException}, since it should 
      * not be called for an {@code XASession} object.
      *
      * @exception TransactionInProgressException if the method is called on 
      *                         an {@code XASession}.
      *                                     
      */

    void
    commit() throws JMSException;


    /** Throws a {@code TransactionInProgressException}, since it should 
      * not be called for an {@code XASession} object.
      *
      * @exception TransactionInProgressException if the method is called on 
      *                         an {@code XASession}.
      *                                     
      */

    void
    rollback() throws JMSException;
}
