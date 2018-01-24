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
 * @(#)mqbytes-message.h	1.10 06/26/07
 */ 

#ifndef MQ_BYTES_MESSAGE_H
#define MQ_BYTES_MESSAGE_H

/*
 * declarations of C interface for bytes message 
 */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"

/**
 * Creates a new bytes message.
 *
 * @param messageHandle the output parameter for the newly created message
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQCreateBytesMessage(MQMessageHandle * messageHandle);

/**
 * Gets the bytes from a bytes message.  This call is only valid if
 * messageHandle refers to a message whose type is
 * MQBytesMessageType.  The bytes that are returned are not a copy.  The
 * caller should not modify the bytes or attempt to free them.
 *
 * @param messageHandle the handle of the message to retrieve the bytes from
 * @param messageBytes the output parameter for the bytes of the message
 * @param messageBytesSize the number of bytes that are present in messageBytes
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQGetBytesMessageBytes(const MQMessageHandle messageHandle,
                         const MQInt8 ** messageBytes,
                         MQInt32 *       messageBytesSize);

/**
 * Sets the bytes for a bytes message.  This call is only valid if
 * messageHandle refers to a message whose type is
 * MQBytesMessageType.  A copy of the bytes is made, so the caller
 * can manipulate messageBytes after this function returns.
 *
 * @param messageHandle the handle of the message to set the bytes for
 * @param messageBytes the bytes to set for the message body
 * @param messageBytesSize the number of bytes that are present in messageBytes
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */   
EXPORTED_SYMBOL MQStatus 
MQSetBytesMessageBytes(const MQMessageHandle messageHandle,
                         const MQInt8 *        messageBytes,
                         MQInt32               messageBytesSize);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_BYTES_MESSAGE_H */
