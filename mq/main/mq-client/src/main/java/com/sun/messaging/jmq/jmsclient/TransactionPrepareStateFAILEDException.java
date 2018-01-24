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
 */ 

package com.sun.messaging.jmq.jmsclient;

import com.sun.messaging.jms.JMSException;

/**
 */
public class TransactionPrepareStateFAILEDException extends JMSException {
	
	  public TransactionPrepareStateFAILEDException
	   (String reason, String errorCode) {
	    super(reason, errorCode);
	  }

	  /** Constructs a <CODE>JMSException</CODE> with the specified reason and with
	   *  the error code defaulting to null.
	   *
	   *  @param  reason        a description of the exception
	   **/
	  public
	  TransactionPrepareStateFAILEDException (String reason) {
	    super(reason);
	  }

	  /** Constructs a <CODE>JMSException</CODE> with the specified reason,
	   *  error code, and a specified cause.
	   *
	   *  @param  reason        a description of the exception
	   *  @param  errorCode     a string specifying the vendor-specific
	   *                        error code
	   *  @param  cause         the cause. A <tt>null</tt> value is permitted,
	   *                        and indicates that the cause is non-existent
	   *                        or unknown.
	   **/
	  public
	  TransactionPrepareStateFAILEDException (String reason, String errorCode, Throwable cause) {
	    super(reason, errorCode, cause);
	  }
	  
}
