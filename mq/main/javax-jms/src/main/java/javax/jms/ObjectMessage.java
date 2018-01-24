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

import java.io.Serializable;

/** An {@code ObjectMessage} object is used to send a message that contains
  * a serializable object in the Java programming language ("Java object").
  * It inherits from the {@code Message} interface and adds a body
  * containing a single reference to an object. Only {@code Serializable} 
  * Java objects can be used.
  *
  * <P>If a collection of Java objects must be sent, one of the 
  * {@code Collection} classes provided since JDK 1.2 can be used.
  *
  * <P>When a client receives an {@code ObjectMessage}, it is in read-only 
  * mode. If a client attempts to write to the message at this point, a 
  * {@code MessageNotWriteableException} is thrown. If 
  * {@code clearBody} is called, the message can now be both read from and 
  * written to.
  * 
  * @see         javax.jms.Session#createObjectMessage()
  * @see         javax.jms.Session#createObjectMessage(Serializable)
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface ObjectMessage extends Message {

    /** Sets the serializable object containing this message's data.
      * It is important to note that an {@code ObjectMessage}
      * contains a snapshot of the object at the time {@code setObject()}
      * is called; subsequent modifications of the object will have no 
      * effect on the {@code ObjectMessage} body.
      *
      * @param object the message's data
      *  
      * @exception JMSException if the JMS provider fails to set the object
      *                         due to some internal error.
      * @exception MessageFormatException if object serialization fails.
      * @exception MessageNotWriteableException if the message is in read-only
      *                                         mode.
      */

    void 
    setObject(Serializable object) throws JMSException;


    /** Gets the serializable object containing this message's data. The 
      * default value is null.
      *
      * @return the serializable object containing this message's data
      *  
      * @exception JMSException if the JMS provider fails to get the object
      *                         due to some internal error.
      * @exception MessageFormatException if object deserialization fails.
      */

    Serializable 
    getObject() throws JMSException;
}
