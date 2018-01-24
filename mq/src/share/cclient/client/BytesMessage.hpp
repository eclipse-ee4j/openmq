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
 * @(#)BytesMessage.hpp	1.3 06/26/07
 */ 

#ifndef BYTESMESSAGE_HPP
#define BYTESMESSAGE_HPP

#include "Message.hpp"

/**
 * This class encapsulates a JMS Bytes Message.  
 */
class BytesMessage : public Message {
private:

public:
  /**
   * Constructor.
   */
  BytesMessage();

  /**
   * Constructor.
   */
  BytesMessage(Packet * const packetArg);

  /**
   * Destructor.
   */
  ~BytesMessage();

  /**
   * @return BYTES_MESSAGE
   */
  virtual PRUint16 getType();

  /**
   * Sets the bytes of the message to messageBytes.  This method makes a
   * copy of messageBytes, so the caller is responsible for freeing
   * messageBytes.
   *
   * @param messageBytes the paylod of the message
   * @param messageBytesSize the size of the paylod of the message
   * @return IMQ_SUCCESS if successful and an error otherwise 
   */
  iMQError setMessageBytes(const PRUint8 * const messageBytes,
                           const PRInt32 messageBytesSize);

  /**
   * Returns the bytes of the message in messageBytes.  A pointer to
   * the actual message bytes are returned.  The caller should not
   * modify or attempt to free messageBytes.
   *
   * @param messageBytes is the output parameter for the bytes of the message
   * @param messageBytesSize is the size of the 
   * @return IMQ_SUCCESS if successful and an error otherwise */
  iMQError getMessageBytes(const PRUint8 ** const messageBytes,
                           PRInt32 * const messageBytesSize) const;

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
  BytesMessage(const BytesMessage& bytesMessage);
  BytesMessage& operator=(const BytesMessage& bytesMessage);
};

#endif // BYTESMESSAGE_HPP
