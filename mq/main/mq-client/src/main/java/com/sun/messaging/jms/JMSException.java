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
 * @(#)JMSException.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms;

import java.io.*;
import javax.jms.*;
import com.sun.messaging.jmq.jmsclient.logging.Loggable;

/**
 * <P>This class encapsulates MQ JMSExceptions.
 *
 * <P>It provides the following information:
 * <UL>
 *   <LI> A provider-specific string describing the error. This string is
 *        the standard exception message and is available via the
 *        <CODE>getMessage</CODE> method.
 *   <LI> A provider-specific string error code
 *   <LI> A reference to another exception. Often a JMS API exception will
 *        be the result of a lower-level problem. If appropriate, this
 *        lower-level exception can be linked to the JMS API exception.
 * </UL>
 **/

public class JMSException extends javax.jms.JMSException implements Loggable {

  private Throwable cause = null;

  private boolean isLogged = false;

  /** Constructs a <CODE>JMSException</CODE> with the specified reason and
   *  error code.
   *
   *  @param  reason        a description of the exception
   *  @param  errorCode     a string specifying the vendor-specific
   *                        error code
   **/
  public
  JMSException(String reason, String errorCode) {
    super(reason, errorCode);
  }

  /** Constructs a <CODE>JMSException</CODE> with the specified reason and with
   *  the error code defaulting to null.
   *
   *  @param  reason        a description of the exception
   **/
  public
  JMSException(String reason) {
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
  JMSException(String reason, String errorCode, Throwable cause) {
    super(reason, errorCode);
    this.cause = cause;
    if (cause != null && cause instanceof java.lang.Exception) {
        setLinkedException((Exception)cause);
    }
  }

  /**
   *
   * <P>
   * If running under J2SE1.4 or above, this method will also
   * set the cause of the <CODE>JMSException</CODE>.
   * When a backtrace of the <CODE>JMSException</CODE> is printed
   * using {@link java.lang.Exception#printStackTrace printStackTrace}
   * using {@link java.lang.Throwable#printStackTrace printStackTrace}
   * a backtrace of the cause will also get printed.
   *
  **/
  public
  synchronized void setLinkedException(Exception ex) {
      super.setLinkedException(ex);
      try {
          initCause(ex);
      } catch (Throwable t) {

      }
  }

  /**
   *
   * <P>
   * If running under versions of the Java platform prior to J2SE1.4,
   * this method will also print the backtrace of the exception linked
   * to this <CODE>JMSException</CODE> and obtained via
   * {@link javax.jms.JMSException#getLinkedException javax.jms.JMSException.getLinkedException()}
  **/
  public
  void printStackTrace() {
      this.printStackTrace(System.err);
  }

  /**
   * {@inheritDoc}
   * <P>
   * If running under versions of the Java platform prior to J2SE1.4,
   * this method will also print the backtrace of the exception linked
   * to this <CODE>JMSException</CODE> and obtained via
   * {@link javax.jms.JMSException#getLinkedException javax.jms.JMSException.getLinkedException()}
  **/
  public
  void printStackTrace(PrintStream s) {
      Throwable cause;
      super.printStackTrace(s);
      try {
          getCause();
      } catch (Throwable t) {
	  if ( (cause = getLinkedException()) != null) {
              synchronized (s) {
                  s.print("Caused by: ");
              }
              cause.printStackTrace(s);
          }

      }
  }

  /**
   *
   * <P>
   * If running under versions of the Java platform prior to J2SE1.4,
   * this method will also print the backtrace of the exception linked
   * to this <CODE>JMSException</CODE> and obtained via
   * {@link javax.jms.JMSException#getLinkedException}
  **/
  public
  void printStackTrace(PrintWriter s) {
      Throwable cause;
      super.printStackTrace(s);
      try {
          getCause();
      } catch (Throwable t) {
	  if ( (cause = getLinkedException()) != null) {
              synchronized (s) {
                  s.print("Caused by: "); // + cause.toString());
              }
              cause.printStackTrace(s);
          }

      }
  }

  /**
    * set state to true if this object is logged.
    * @param state boolean
    */
   public void setLogState (boolean state) {
       this.isLogged = state;
   }

   /**
    * get logging state of this object.
    * @return boolean true if this object is logged.
    */
   public boolean getLogState() {
       return this.isLogged;
   }

}
