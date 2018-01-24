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
 * @(#)mqtext-message.h	1.10 06/26/07
 */ 

#ifndef MQ_TEXT_MESSAGE_H
#define MQ_TEXT_MESSAGE_H

/*
 * declarations of C interface for text message
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"

/**
 * Creates a new text message.
 *
 * @param messageHandle the output parameter for the newly created message
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQCreateTextMessage(MQMessageHandle * messageHandle);
  
/**
 * Gets the bytes from a bytes message.  This call is only valid if
 * messageHandle refers to a message whose type is MQTextMessageType.
 * The string that is returned is not a copy.  The caller should not
 * modify the bytes or attempt to free it.  The string is a NULL-
 * terminated UTF8-encoded string.
 *
 * @param messageHandle the handle of the message to retrieve the text from
 * @param messageText the output parameter for the message text
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQGetTextMessageText(const MQMessageHandle messageHandle,
                     ConstMQString *       messageText);

/**
 * Sets the text for a text message.  This call is only valid if
 * messageHandle refers to a message whose type is
 * MQTextMessageType.  A copy of the string is made, so the caller
 * can manipulate messageText after this function returns.  messageText
 * should be a NULL-terminated UTF8-encoded string.
 *
 * @param messageHandle the handle of the message to set the text 
 * @param messageText the string to set the message text to
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQSetTextMessageText(const MQMessageHandle messageHandle,
                     ConstMQString         messageText);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_TEXT_MESSAGE_H */
