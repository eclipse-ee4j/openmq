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
 * This unchecked exception must be thrown when a JMS client attempts to give a
 * provider a message selector with invalid syntax, and the method signature
 * does not permit a {@code InvalidSelectorException} to be thrown.
 *
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 **/

public class InvalidSelectorRuntimeException extends JMSRuntimeException {
  
  /**
   * Explicitly set serialVersionUID to be the same as the implicit serialVersionUID of the JMS 2.0 version
   */
  private static final long serialVersionUID = 1974783946275023548L;

	/**
	 * Constructs a {@code InvalidSelectorRuntimeException} with the specified detail message
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 **/
	public InvalidSelectorRuntimeException(String detailMessage) {
		super(detailMessage);
	}
	
	/**
	 * Constructs a {@code InvalidSelectorRuntimeException} with the specified detail message
	 * and error code.
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 **/
	public InvalidSelectorRuntimeException(String detailMessage, String errorCode) {
		super(detailMessage,errorCode);
	}

	/**
	 * Constructs a {@code InvalidSelectorRuntimeException} with the specified detail message,
	 * error code and cause
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 * @param cause
	 *            the underlying cause of this exception
	 */
	public InvalidSelectorRuntimeException(String detailMessage, String errorCode, Throwable cause) {
		super(detailMessage,errorCode,cause);
	}
  
}
