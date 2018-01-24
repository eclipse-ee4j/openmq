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
 * This is the root class of all unchecked exceptions in the JMS API.
 * <p>
 * In additional to the detailMessage and cause fields inherited from
 * {@code Throwable}, this class also allows a provider-specific errorCode
 * to be set.
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 **/

public class JMSRuntimeException extends RuntimeException {
  
  /**
   * Explicitly set serialVersionUID to be the same as the implicit serialVersionUID of the JMS 1.1 version
   */
  private static final long serialVersionUID = -5204332229969809982L;

	/**
	 * Provider-specific error code.
	 **/
	private String errorCode=null;

	/**
	 * Constructs a {@code JMSRuntimeException} with the specified detail message
	 * and error code.
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 **/
	public JMSRuntimeException(String detailMessage, String errorCode) {
		super(detailMessage);
		this.errorCode = errorCode;
	}

	/**
	 * Constructs a {@code JMSRuntimeException} with the specified detail message
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 **/
	public JMSRuntimeException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * Constructs a {@code JMSRuntimeException} with the specified detail message,
	 * error code and cause
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 * @param cause
	 *            the underlying cause of this exception
	 */
	public JMSRuntimeException(String detailMessage, String errorCode, Throwable cause) {
		super(detailMessage, cause);
		this.errorCode = errorCode;
	}

	/**
	 * Returns the vendor-specific error code.
	 * 
	 * @return the provider-specific error code
	 **/
	public String getErrorCode() {
		return this.errorCode;
	}
}
