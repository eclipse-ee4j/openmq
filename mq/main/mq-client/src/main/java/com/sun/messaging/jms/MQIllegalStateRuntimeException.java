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

package com.sun.messaging.jms;

import javax.jms.IllegalStateException;

import com.sun.messaging.jmq.jmsclient.logging.Loggable;

/**
 * <P>
 * This class is the MQ-specific implementation of
 * javax.jms.IllegalStateRuntimeException and adds a new constructor and the
 * methods setLogState and getlogState
 **/
public class MQIllegalStateRuntimeException extends javax.jms.IllegalStateRuntimeException implements Loggable {

	private boolean isLogged = false;

	/**
	 * Constructs a <code>MQIllegalStateRuntimeException</code> with the specified
	 * detail message
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 **/
	public MQIllegalStateRuntimeException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * Constructs a <code>MQIllegalStateRuntimeException</code> with the specified
	 * detail message and error code.
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 **/
	public MQIllegalStateRuntimeException(String detailMessage, String errorCode) {
		super(detailMessage, errorCode);
	}

	/**
	 * Constructs a <code>MQIllegalStateRuntimeException</code> with the specified
	 * detail message, error code and cause
	 * 
	 * @param detailMessage
	 *            a description of the exception
	 * @param errorCode
	 *            a provider-specific error code
	 * @param cause
	 *            the underlying cause of this exception
	 */
	public MQIllegalStateRuntimeException(String detailMessage, String errorCode, Throwable cause) {
		super(detailMessage, errorCode, cause);
	}

	/**
	 * Construct a <code>MQIllegalStateRuntimeException</code> to wrap the
	 * specified IllegalStateException
	 * 
	 * @param cause the underlying cause of this exception
	 */
	public MQIllegalStateRuntimeException(IllegalStateException cause) {
		super(cause.getMessage(), cause.getErrorCode(), cause);
	}

	/**
	 * Specify whether this object is logged.
	 * 
	 * @param whether
	 *            this object is logged
	 */
	public void setLogState(boolean state) {
		this.isLogged = state;
	}

	/**
	 * return whether this object is logged
	 * 
	 * @return whether this object is logged
	 */
	public boolean getLogState() {
		return this.isLogged;
	}
}
