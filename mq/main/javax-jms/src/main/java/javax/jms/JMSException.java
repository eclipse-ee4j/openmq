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
 * <P>This is the root class of all checked exceptions in the JMS API.
 *
 * <P>It provides the following information:
 * <UL>
 *   <LI> A provider-specific string describing the error. This string is 
 *        the standard exception message and is available via the
 *        {@code getMessage} method.
 *   <LI> A provider-specific string error code 
 *   <LI> A reference to another exception. Often a JMS API exception will 
 *        be the result of a lower-level problem. If appropriate, this 
 *        lower-level exception can be linked to the JMS API exception.
 * </UL>
 * 
 * @version JMS 2.0
 * @since JMS 1.0
 * 
 **/

public class JMSException extends Exception {

  /**
   * Explicitly set serialVersionUID to be the same as the implicit serialVersionUID of the JMS 1.1 version
   */
  private static final long serialVersionUID = 8951994251593378324L;

  /** Vendor-specific error code.
  **/
  private String errorCode;

  /** {@code Exception} reference.
  **/
  private Exception linkedException;


  /** Constructs a {@code JMSException} with the specified reason and 
   *  error code.
   *
   *  @param  reason        a description of the exception
   *  @param  errorCode     a string specifying the vendor-specific
   *                        error code
   **/
  public 
  JMSException(String reason, String errorCode) {
    super(reason);
    this.errorCode = errorCode;
    linkedException = null;
  }

  /** Constructs a {@code JMSException} with the specified reason and with
   *  the error code defaulting to null.
   *
   *  @param  reason        a description of the exception
   **/
  public 
  JMSException(String reason) {
    super(reason);
    this.errorCode = null;
    linkedException = null;
  }

  /** Gets the vendor-specific error code.
   *  @return   a string specifying the vendor-specific
   *                        error code
  **/
  public 
  String getErrorCode() {
    return this.errorCode;
  }

  /**
   * Gets the exception linked to this one.
   *
   * @return the linked {@code Exception}, null if none
  **/
  public 
  Exception getLinkedException() {
    return (linkedException);
  }

  /**
   * Adds a linked {@code Exception}.
   *
   * @param ex       the linked {@code Exception}
  **/
  public void setLinkedException(Exception ex) {
      linkedException = ex;
  } 
}
