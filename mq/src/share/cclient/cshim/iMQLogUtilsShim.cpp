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
 * @(#)iMQLogUtilsShim.cpp	1.12 06/26/07
 */ 

#include "mqlogutil-priv.h"
#include "shimUtils.hpp"
#include "../util/Logger.hpp"


/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetLogFileName(ConstMQString logFileName)
{
  static const char FUNCNAME[] = "MQSetLogFileName";
  MQError errorCode = MQ_SUCCESS;
 
  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setLogFileName(logFileName);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetLoggingFunc(MQLoggingFunc  loggingFunc,
                   void*    callbackData)
{
  static const char FUNCNAME[] = "MQSetLoggingFunc";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  ASSERT( sizeof(MQLoggingLevel) == sizeof(LogLevel) );
  logger->setLoggingCallback(loggingFunc, callbackData);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetMaxLogSize(MQInt32 maxLogSize)
{
  static const char FUNCNAME[] = "MQSetMaxLogSize";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setMaxLogSize(maxLogSize);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetLogFileLogLevel(MQLoggingLevel logLevel)
{
  static const char FUNCNAME[] = "MQSetLogFileLogLevel";
  MQError errorCode = MQ_SUCCESS;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setLogFileLogLevel(logLevel);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetStdErrLogLevel(MQLoggingLevel logLevel)
{
  static const char FUNCNAME[] = "MQSetStdErrLogLevel";

  MQError errorCode = MQ_SUCCESS;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setStdErrLogLevel(logLevel);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetCallbackLogLevel(MQLoggingLevel logLevel)
{
  static const char FUNCNAME[] = "MQSetCallbackLogLevel";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setCallbackLogLevel(logLevel);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetLogFileLogLevel(MQLoggingLevel * logLevel)
{
  static const char FUNCNAME[] = "MQGetLogFileLogLevel";
  MQError errorCode = MQ_SUCCESS;
  Logger * logger = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( logLevel == NULL, MQ_NULL_PTR_ARG );
  
  logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  *logLevel = (MQLoggingLevel)logger->getLogFileLogLevel();
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetStdErrLogLevel(MQLoggingLevel * logLevel)
{
  static const char FUNCNAME[] = "MQGetStdErrLogLevel";
  MQError errorCode = MQ_SUCCESS;
  Logger * logger = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);
  
  CNDCHK( logLevel == NULL, MQ_NULL_PTR_ARG );
  
  logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  *logLevel = (MQLoggingLevel)logger->getStdErrLogLevel();
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetCallbackLogLevel(MQLoggingLevel *  logLevel)
{
  static const char FUNCNAME[] = "MQGetCallbackLogLevel";
  MQError errorCode = MQ_SUCCESS;
  Logger * logger = NULL;
  
  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( logLevel == NULL, MQ_NULL_PTR_ARG );

  logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  *logLevel = (MQLoggingLevel)logger->getCallbackLogLevel();
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQSetLogMask(MQLoggingLevel logLevel, MQInt32 logMask)
{
  static const char FUNCNAME[] = "MQSetLogMask";
  MQError errorCode = MQ_SUCCESS;

  CLEAR_ERROR_TRACE(PR_FALSE);

  Logger * logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  logger->setMask(logLevel, logMask);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

/*
 *
 */
EXPORTED_SYMBOL MQStatus
MQGetLogMask(MQLoggingLevel logLevel, MQInt32 * logMask)
{
  static const char FUNCNAME[] = "MQGetLogMask";
  MQError errorCode = MQ_SUCCESS;
  Logger * logger = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  CNDCHK( logMask == NULL, MQ_NULL_PTR_ARG );
  
  logger = Logger::getInstance();
  CNDCHK( logger == NULL, MQ_STATUS_NULL_LOGGER );

  *logMask = logger->getMask(logLevel);
  
  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}
