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

/**
 * The {@code XAJMSContext} interface extends the capability of
 * {@code JMSContext} by adding access to a JMS provider's support for the Java
 * Transaction API (JTA) (optional). This support takes the form of a
 * {@code javax.transaction.xa.XAResource} object. The functionality of this
 * object closely resembles that defined by the standard X/Open XA Resource
 * interface.
 * 
 * <P>
 * An application server controls the transactional assignment of an
 * {@code XASession} by obtaining its {@code XAResource}. It uses the
 * {@code XAResource} to assign the session to a transaction, prepare and commit
 * work on the transaction, and so on.
 * 
 * <P>
 * An {@code XAResource} provides some fairly sophisticated facilities for
 * interleaving work on multiple transactions, recovering a list of transactions
 * in progress, and so on. A JTA aware JMS provider must fully implement this
 * functionality. This could be done by using the services of a database that
 * supports XA, or a JMS provider may choose to implement this functionality
 * from scratch.
 * 
 * <P>
 * A client of the application server is given what it thinks is an ordinary
 * {@code JMSContext}. Behind the scenes, the application server controls the
 * transaction management of the underlying {@code XAJMSContext}.
 * 
 * <P>
 * The {@code XAJMSContext} interface is optional. JMS providers are not
 * required to support this interface. This interface is for use by JMS
 * providers to support transactional environments. Client programs are strongly
 * encouraged to use the transactional support available in their environment,
 * rather than use these XA interfaces directly.
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 */

public interface XAJMSContext extends JMSContext {

	/**
	 * Returns the {@code JMSContext} object associated with this
	 * {@code XAJMSContext}.
	 * 
	 * @return the {@code JMSContext} object associated with this
	 *         {@code XAJMSContext}
	 * 
	 */
	JMSContext getContext();

	/**
	 * Returns an {@code XAResource} to the caller.
	 * 
	 * @return an {@code XAResource}
	 */
	XAResource getXAResource();

	/**
	 * Returns whether the session is in transacted mode; this method always
	 * returns true.
	 * 
	 * @return true
	 * 
	 */
	@Override
	boolean getTransacted();

	/**
	 * Throws a {@code TransactionInProgressRuntimeException}, since it should
	 * not be called for an {@code XAJMSContext} object.
	 * 
	 * @exception TransactionInProgressRuntimeException
	 *                if the method is called on an {@code XAJMSContext}.
	 * 
	 */

	@Override
	void commit();

	/**
	 * Throws a {@code TransactionInProgressRuntimeException}, since it should
	 * not be called for an {@code XAJMSContext} object.
	 * 
	 * @exception TransactionInProgressRuntimeException
	 *                if the method is called on an {@code XAJMSContext}.
	 * 
	 */

	@Override
	void rollback();

}
