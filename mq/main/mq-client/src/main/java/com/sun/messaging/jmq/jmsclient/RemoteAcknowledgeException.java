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
 * @(#)RemoteAcknowledgeException.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;

import com.sun.messaging.jms.JMSException;

/**
 * 
 * This exception is thrown when a remote broker is killed and one of the following 
 * activities occurred:
 * 
 * 1. Auto-ack/dups-ok ack a message originated from the killed remote broker.
 * 
 * 2. Client-ack message(s) and the messages are originated from the killed remote broker.
 * 
 * 3. Client runtime sending a PREPARE or COMMIT protocol packet to broker and the
 * messages to be prepared/committed are originated from the killed remote broker.
 * 
 */
public class RemoteAcknowledgeException extends JMSException {
	
	/**
	 * property name in the props entry.  The property vale is a space
     * separated consumer UID String.
	 */
	public static final String JMQRemoteConsumerIDs = "JMQRemoteConsumerIDs";

	private Hashtable props = null;
	
	/** Constructs a <CODE>JMSException</CODE> with the specified reason and
	   *  error code.
	   *
	   *  @param  reason        a description of the exception
	   *  @param  errorCode     a string specifying the vendor-specific
	   *                        error code
	   **/
	  public
	  RemoteAcknowledgeException (String reason, String errorCode) {
	    super(reason, errorCode);
	  }

	  /** Constructs a <CODE>JMSException</CODE> with the specified reason and with
	   *  the error code defaulting to null.
	   *
	   *  @param  reason        a description of the exception
	   **/
	  public
	  RemoteAcknowledgeException (String reason) {
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
	  RemoteAcknowledgeException (String reason, String errorCode, Throwable cause) {
	    super(reason, errorCode, cause);
	  }
	  
	  /**
	   * Get the property object associate with this remote exception.
	   * 
	   * @return the property object associate with this remote exception.
	   */
	  public Hashtable getProperties() {
		  
		  if (this.props == null) {
			  synchronized (this) {
				  if (this.props == null) {
					  props = new Hashtable();
				  }
			  }
		  }
		  
		  return this.props;
	  }
	  
	  /**
	   * Set properties associate with this remote exception.
	   * @param p the property object associate with the remote exception.
	   */
	  public void setProperties(Hashtable p) {
		  synchronized (this) {
			  this.props = p;
		  }
	  }
	  
}
