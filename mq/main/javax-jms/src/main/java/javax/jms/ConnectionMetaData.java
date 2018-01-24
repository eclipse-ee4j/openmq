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

import java.util.Enumeration;

/** A {@code ConnectionMetaData} object provides information describing the 
  * {@code Connection} object.
  *
  * @version JMS 2.0
  * @since JMS 1.0
  */

public interface ConnectionMetaData {

    /** Gets the JMS API version.
      *
      * @return the JMS API version
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    String 
    getJMSVersion() throws JMSException;


    /** Gets the JMS major version number.
      *  
      * @return the JMS API major version number
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    int 
    getJMSMajorVersion() throws JMSException; 
 

    /** Gets the JMS minor version number.
      *  
      * @return the JMS API minor version number
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    int  
    getJMSMinorVersion() throws JMSException;


    /** Gets the JMS provider name.
      *
      * @return the JMS provider name
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */ 

    String 
    getJMSProviderName() throws JMSException;


    /** Gets the JMS provider version.
      *
      * @return the JMS provider version
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */ 

    String 
    getProviderVersion() throws JMSException;


    /** Gets the JMS provider major version number.
      *  
      * @return the JMS provider major version number
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    int
    getProviderMajorVersion() throws JMSException; 

 
    /** Gets the JMS provider minor version number.
      *  
      * @return the JMS provider minor version number
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    int  
    getProviderMinorVersion() throws JMSException;

 
    /** Gets an enumeration of the JMSX property names.
      *  
      * @return an Enumeration of JMSX property names
      *  
      * @exception JMSException if the JMS provider fails to retrieve the
      *                         metadata due to some internal error.
      */

    Enumeration
    getJMSXPropertyNames() throws JMSException;
}
