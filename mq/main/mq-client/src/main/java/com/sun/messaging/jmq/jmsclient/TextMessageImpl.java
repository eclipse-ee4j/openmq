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
 * @(#)TextMessageImpl.java	1.14 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.PacketType;

/** A TextMessage is used to send a message containing a
 * <CODE>java.lang.String</CODE>.
 * It inherits from <CODE>Message</CODE> and adds a text message body.
 *
 * <P>The inclusion of this message type is based on our presumption
 * that XML will likely become a popular mechanism for representing
 * content of all kinds including the content of JMS messages.
 *
 * <P>When a client receives a TextMessage, it is in read-only mode. If a
 * client attempts to write to the message at this point, a
 * MessageNotWriteableException is thrown. If <CODE>clearBody</CODE> is
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
 */

public class TextMessageImpl
    extends MessageImpl
    implements TextMessage {

  private String text = null;

  //serialize message body
  //This is called when producing messages.
  protected void
      setMessageBodyToPacket() throws JMSException {

    //ObjectOutputStream oos;
    if (text == null) {
      return;
    }
    try {
      setMessageBody(text.getBytes(UTF8));
    }
    catch (Exception e) {
      ExceptionHandler.handleException(e,
                                       AdministeredObject.cr.X_MESSAGE_SERIALIZE);
    }

  }

  //deserialize message body
  //This is called after message is received
  protected void
      getMessageBodyFromPacket() throws JMSException {

    //InputStream is = getMessageBodyStream();
    //ObjectInputStream ois = new ObjectInputStream (is);
    //text = (String) ois.readObject();

    try {
      byte[] body = getMessageBody();
      if (body != null) {
        text = new String(body, UTF8);
      }
    }
    catch (Exception e) {
      ExceptionHandler.handleException(e,
                                       AdministeredObject.cr.X_MESSAGE_DESERIALIZE);
    }
  }

  /**
   * Constructor.
   */
  protected TextMessageImpl() throws JMSException {
    super();
    setPacketType(PacketType.TEXT_MESSAGE);
  }

  /**
   * clear body
   */
  public void clearBody() throws JMSException {
    text = null;
    setMessageReadMode(false);
  }

  /** Set the string containing this message's data.
   *
   * @param string the String containing the message's data
   *
   * @exception JMSException if JMS fails to set text due to
   *                         some internal JMS error.
   * @exception MessageNotWriteableException if message in read-only mode.
   */

  public void
      setText(String string) throws JMSException {
    checkMessageAccess();
    text = string;
  }

  /** Get the string containing this message's data.  The default
   * value is null.
   *
   * @return the String containing the message's data
   *
   * @exception JMSException if JMS fails to get text due to
   *                         some internal JMS error.
   */

  public String
      getText() throws JMSException {
    return text;
  }

  public String toString() {
    return new StringBuffer().append("\nText:\t").append(text).append(super.
        toString()).toString();
  }

  public void dump(PrintStream ps) {
    ps.println("------ TextMessageImpl dump ------");
    super.dump(ps);
  }
}
