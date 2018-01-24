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
 * @(#)mqconnection-priv.h	1.13 10/17/07
 */ 

#ifndef MQ_CONNECTION_PRIV_H 
#define MQ_CONNECTION_PRIV_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqtypes.h"
#include "mqcallback-types-priv.h"
#include "mqproperties.h"
#include "mqconnection-props.h"
#include "mqconnection.h"

/**
 * Opens a new connection the broker based on the supplied parameters.
 *
 * @param propertiesHandle a handle to a properties object containing
 *        properties to be used for this connection.  This handle will
 *        be invalid after this function returns.
 * @param username the username to use when connecting to the broker
 * @param password the password to use when connection to the broker
 * @param clientID the connectionID for the connection
 * @param exceptionListener the connection exception callback function
 * @param exceptionCallbackData the connection exception callback data 
 * @param createThreadFunc the callback function to use when this 
 *        connection creates a thread.  If this parameter is NULL, then
 *        the connection creates its own threads.
 * @param createThreadFuncData the void* function that is passed to
 *        createThreadFunc whenever it is called.
 * @param connectionHandle the output parameter that contains the
 *        newly opened connection.
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.
 */
EXPORTED_SYMBOL MQStatus 
MQCreateConnectionExt(MQPropertiesHandle                propertiesHandle,
                      ConstMQString                     username,
                      ConstMQString                     password,
                      ConstMQString                     clientID,
                      MQConnectionExceptionListenerFunc exceptionListener,
                      void *                            listenerCallbackData,
                      MQCreateThreadFunc                createThreadFunc,
                      void *                            createThreadFuncData,
                      MQBool                            isXA,
                      MQConnectionHandle *              connectionHandle);

EXPORTED_SYMBOL MQStatus 
MQCreateXAConnection(MQPropertiesHandle                propertiesHandle,
                     ConstMQString                     username,
                     ConstMQString                     password,
                     ConstMQString                     clientID,
                     MQConnectionExceptionListenerFunc exceptionListener,
                     void *                            listenerCallbackData,
                     MQConnectionHandle *              connectionHandle);
/**
 * 
 * @param connectionHandle the handle to the XA connection to close
 * @return the status of the function call.  Pass this value to
 *         MQStatusIsError to determine if the call was
 *         successful.  */
EXPORTED_SYMBOL MQStatus
MQCloseXAConnection(const MQConnectionHandle connectionHandle);


#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* MQ_CONNECTION_PRIV_H */
