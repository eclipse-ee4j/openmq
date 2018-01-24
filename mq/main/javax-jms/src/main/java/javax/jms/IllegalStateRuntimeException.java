/*
 * Copyright (c) 2011, 2017 Oracle and/or its affiliates. All rights reserved.
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

/**
 * This unchecked exception is thrown when a method is invoked at an illegal or
 * inappropriate time or if the provider is not in an appropriate state for the
 * requested operation, and the method signature does not permit a
 * {@code IllegalStateRuntimeException} to be thrown. For example, this
 * exception must be thrown if {@code JMSContext.commit} is called on a
 * non-transacted session.
 *
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 **/
public class IllegalStateRuntimeException extends JMSRuntimeException {
  
  /**
   * Explicitly set serialVersionUID to be the same as the implicit serialVersionUID of the JMS 2.0 version
   */
  private static final long serialVersionUID = 6838714637432837899L;

	/**
	 * Constructs a {@code IllegalStateRuntimeException} with the specified detail message
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 **/
	public IllegalStateRuntimeException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * Constructs a {@code IllegalStateRuntimeException} with the specified detail message
	 * and error code.
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 **/
	public IllegalStateRuntimeException(String detailMessage, String errorCode) {
		super(detailMessage,errorCode);
	}

	/**
	 * Constructs a {@code IllegalStateRuntimeException} with the specified detail message,
	 * error code and cause
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 * @param cause
	 *            the underlying cause of this exception
	 */
	public IllegalStateRuntimeException(String detailMessage, String errorCode, Throwable cause) {
		super(detailMessage, errorCode, cause);
	}
	
}
