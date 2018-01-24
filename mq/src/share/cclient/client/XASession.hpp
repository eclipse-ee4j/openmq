/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)XASession.hpp	1.2 10/23/07
 */ 

#ifndef XASESSION_HPP
#define XASESSION_HPP

#include "Session.hpp"
#include "../cshim/xaswitch.hpp"

/**
 **/
class XASession : public Session {
private:

  PRUintn xidIndex;
  MQMessageListenerBAFunc beforeMessageListener;
  MQMessageListenerBAFunc afterMessageListener;
  void * baMLCallbackData;
  

public:

  /**
   * @param connection the connection that created this Session
   * @param beforeDeliveryArg for async receiving mode
   * @param afterDeliveryArg  for async receiving mode
   * @paramt callbackDataArg  data pointer to be passed to before/afterDelivery
   * @param receiveMode */
  XASession(Connection *             const connection,
            const ReceiveMode        receiveMode,
            MQMessageListenerBAFunc beforeMessageListenerArg,
            MQMessageListenerBAFunc afterMessageListenerArg,
            void * callbackDataArg);


  MQMessageListenerBAFunc getBeforeMessageListenerFunc();
  MQMessageListenerBAFunc getAfterMessageListenerFunc();
  void * getMessageListenerBACallbackData();
  virtual MQError writeJMSMessage(Message * const message, PRInt64 producerID);
  virtual MQError acknowledge(Message * message, PRBool fromMessageListener);

  /**
   * Destructor.  Calls close, which closes all producers and
   * consumers that were created by this session.  */
  virtual ~XASession();
  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  XASession(const XASession& session);
  XASession& operator=(const XASession& session);
};


#endif

