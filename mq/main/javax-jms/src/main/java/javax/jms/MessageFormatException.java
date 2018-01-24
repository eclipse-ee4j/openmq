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

/**
 * <P> This exception must be thrown when a JMS client 
 *     attempts to use a data type not supported by a message or attempts to 
 *     read data in a message as the wrong type. It must also be thrown when 
 *     equivalent type errors are made with message property values. For 
 *     example, this exception must be thrown if 
 *     {@code StreamMessage.writeObject} is given an unsupported class or 
 *     if {@code StreamMessage.readShort} is used to read a 
 *     {@code boolean} value. Note that the special case of a failure 
 *     caused by an attempt to read improperly formatted {@code String} 
 *     data as numeric values must throw the 
 *     {@code java.lang.NumberFormatException}.
 * 
 * @version JMS 2.0
 * @since JMS 1.0
 * 
 **/

public class MessageFormatException extends JMSException {
  
  /**
   * Explicitly set serialVersionUID to be the same as the implicit serialVersionUID of the JMS 1.1 version
   */
  private static final long serialVersionUID = -3642297253594750138L;

  /** Constructs a {@code MessageFormatException} with the specified 
   *  reason and error code.
   *
   *  @param  reason        a description of the exception
   *  @param  errorCode     a string specifying the vendor-specific
   *                        error code
   *                        
   **/
  public 
  MessageFormatException(String reason, String errorCode) {
    super(reason, errorCode);
  }

  /** Constructs a {@code MessageFormatException} with the specified 
   *  reason. The error code defaults to null.
   *
   *  @param  reason        a description of the exception
   **/
  public 
  MessageFormatException(String reason) {
    super(reason);
  }

}
