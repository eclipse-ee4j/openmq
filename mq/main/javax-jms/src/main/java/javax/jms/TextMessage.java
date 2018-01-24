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

/** A {@code TextMessage} object is used to send a message containing a 
  * {@code java.lang.String}.
  * It inherits from the {@code Message} interface and adds a text message 
  * body.
  *
  * <P>This message type can be used to transport text-based messages, including
  *  those with XML content.
  *
  * <P>When a client receives a {@code TextMessage}, it is in read-only 
  * mode. If a client attempts to write to the message at this point, a 
  * {@code MessageNotWriteableException} is thrown. If 
  * {@code clearBody} is 
  * called, the message can now be both read from and written to.
  *
  * @see         javax.jms.Session#createTextMessage()
  * @see         javax.jms.Session#createTextMessage(String)
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         java.lang.String
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */
 
public interface TextMessage extends Message { 

    /** Sets the string containing this message's data.
      *  
      * @param string the {@code String} containing the message's data
      *  
      * @exception JMSException if the JMS provider fails to set the text due to
      *                         some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void
    setText(String string) throws JMSException;


    /** Gets the string containing this message's data.  The default
      * value is null.
      *  
      * @return the {@code String} containing the message's data
      *  
      * @exception JMSException if the JMS provider fails to get the text due to
      *                         some internal error.
      */ 

    String
    getText() throws JMSException;
}
