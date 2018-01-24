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
 * @(#)MessageID.hpp	1.6 06/26/07
 */ 

#ifndef MESSAGEID_HPP
#define MESSAGEID_HPP

#include "Message.hpp"
#include "../basictypes/Object.hpp"

/**
 * This class is stored information about Messages that have not been
 * acknowledged.  We do not store the Messages themselves because the
 * user could delete those.  
 */
class MessageID : public Object {
private:
  SysMessageID sysMessageID;
  PRUint64 consumerID;

public:
  MessageID(const Message * const message);
  virtual ~MessageID();

  PRBool equals(const Message * const message);

  iMQError write(IMQDataOutputStream * const outStream) const;

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  MessageID(const MessageID& messageID);
  MessageID& operator=(const MessageID& messageID);
};

#endif  // MESSAGEID_HPP
