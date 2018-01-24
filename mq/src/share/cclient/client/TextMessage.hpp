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
 * @(#)TextMessage.hpp	1.3 06/26/07
 */ 

#ifndef TEXTMESSAGE_HPP
#define TEXTMESSAGE_HPP

#include "Message.hpp"

/**
 * This class encapsulates a JMS Text Message.  
 */
class TextMessage : public Message {
private:
  // A copy of the body of the message.
  UTF8String * messageBodyText;

public:
  /**
   * Constructor.
   */
  TextMessage();

  /**
   * Constuctor.
   */
  TextMessage(Packet * const packetArg);

  /**
   * Destructor.
   */
  ~TextMessage();

  /**
   * @return TEXT_MESSAGE
   */
  virtual PRUint16 getType();

  /**
   * Sets the text of the message to messageText.  This method makes a
   * copy of messageText, so the caller is responsible for freeing
   * messageText.
   *
   * @param messageText is the string representation of the message text
   * @return IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError setMessageText(const UTF8String * const messageText);

  /**
   * Returns the text of the message in messageText.  messageText
   * actually stores a copy of the message text, so the caller is
   * reponsible for freeing messageText.
   *
   * @param messageText is the output parameter for the text of the message
   * @return IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError getMessageText(UTF8String ** const messageText);


  /** 
   * Similar to setMessageText except a null terminated UTF8-encoded
   * char string is used to initialize the message body.  A copy of
   * the messageText string is made.
   *
   * @param messageText is the string representation of the message text
   * @return IMQ_SUCCESS if successful and an error otherwise 
   * @see setMessageText */
  iMQError setMessageTextString(const char * const messageText);

  /**
   * Similar to getMessageText except a null terminated UTF8-encoded
   * char string is passed back.  The caller should not modify
   * messageText.
   *
   * @param messageText is the output parameter for the text of the message
   * @return IMQ_SUCCESS if successful and an error otherwise 
   * @see getMessageText */
  iMQError getMessageTextString(const char ** const messageText);

  /** @return the type of this object for HandledObject */
  virtual HandledObjectType getObjectType() const;

  /** @return the type of Message */
  virtual HandledObjectType getSuperObjectType() const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  TextMessage(const TextMessage& textMessage);
  TextMessage& operator=(const TextMessage& textMessage);

};

#endif // TEXTMESSAGE_HPP
