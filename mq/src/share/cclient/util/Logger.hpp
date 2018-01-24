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
 * @(#)Logger.hpp	1.7 06/26/07
 */ 

#ifndef LOGGER_HPP
#define LOGGER_HPP

#include <stdio.h>
#include <nspr.h>
#include "../basictypes/Monitor.hpp"
#include "../cshim/mqcallback-types-priv.h"

// Notes:
// 1.) Finer logging correlates to a higher number
// 2.) The lowest level of actual logging is 0
// 3.) Error checking for valid file names is not done
// 4.) Accessor functions are not reentrant, mutators are
// 5.) There is only one lock for the entire class

typedef PRInt32 LogLevel;


// the iMQLoggingLevel enum depends on these values
const LogLevel NONE_LOG_LEVEL    = -1;
const LogLevel SEVERE_LOG_LEVEL  = 0;
const LogLevel WARNING_LOG_LEVEL = 1;
const LogLevel INFO_LOG_LEVEL    = 2;
const LogLevel CONFIG_LOG_LEVEL  = 3;
const LogLevel FINE_LOG_LEVEL    = 4;
const LogLevel FINER_LOG_LEVEL   = 5;
const LogLevel FINEST_LOG_LEVEL  = 6;

// MIN_LOG_LEVEL refers to the lowest level where logging is ACTUALLY DONE!
const LogLevel MIN_LOG_LEVEL = SEVERE_LOG_LEVEL;
const LogLevel MAX_LOG_LEVEL = FINEST_LOG_LEVEL;

typedef PRInt32 LogCode;

/*
extern "C" {
typedef  void (*LoggingCallback)(const LogLevel  loggingLevel,
                                 const LogCode   logCode,
                                 const char *    logMessage,
                                 const PRInt64   timeOfMessage, // value returned by PR_Now
                                 const PRInt64   connectionID,
                                 const char *    filename,
                                 const PRInt32   fileLineNumber,
                                 // whatever was passed to setLoggingCallback
                                       void *    callbackData);     
}
*/

// These should be class members, but MSVC 6.0 doesn't support it.
static const PRInt32 LOGGER_MAX_LOG_NAME_SIZE         = 2000;
static const PRInt32 LOGGER_DEFAULT_MAXIMUM_LOG_SIZE  = 1 * 1000 * 1000; // 1 MB
static const PRInt32 LOGGER_DEFAULT_MAXIMUM_LOG_INDEX = 9;

class Logger {
private:
  static Logger * instance;
  
public:
  static Logger * getInstance();

  static void     deleteInstance();

  static void     test(PRInt32 numLogThreads, const PRInt32 itemsToLog);

private:
  LogLevel         m_logFileLogLevel;        // this is the normal file logLevel
  LogLevel         m_callbackLogLevel;
  LogLevel         m_stdErrLogLevel;

  LogLevel         g_logLevel; // the highest of above 3
  
  MQLoggingFunc    m_loggingCallback;
  FILE *           m_logFile;
  Monitor          m_monitor;

  char             m_logFileName[LOGGER_MAX_LOG_NAME_SIZE];
  char *           m_logFileName_gptr; // points passed last %g or NULL

  // one mask for each logging level
  PRUint32         m_mask[(MAX_LOG_LEVEL + 1)];
  
  // passed to loggingCallback
  void *           m_callbackData;

  // Maximum size of the log file in bytes
  PRInt32          m_maximumLogSize;

 
  //     Log a message that looks something like
  //        SEVERE: 2001/12/11 16:18:20.0001 1131 foo.cpp:1000 0x0F203380 "Server is down"
  void logva(const LogLevel       logLevel,
             const char *   const filename,
             const PRInt32        lineNumber,
             const PRUint32       mask,
             const PRInt64        connectionID,
             const LogCode        logCode,
             const char *   const format,
                   va_list        argptr);

  void parseLogFileName(char * logfilename, char ** gptr);

  // Open the log file with the given log filename and location of %g.
  // Slide all other logs down, deleting the oldest log.
  void openLogFile(const char * const filename, const char * const gptr);

  // Opens a new log file if this one is full
  void openNewLogIfNecessary();

  // The constructor is private because this is a Singleton
  Logger();
  
  void init();
  void reset();
  
public:
  ~Logger();
  LogLevel getLogLevel();

  // sets the filename of the log file, if filename is NULL no
  // information is logged to a file.
  void setLogFileName(const char * const logfilename);
  
  // sets/gets loggingCallback and callbackData
  void setLoggingCallback(MQLoggingFunc const loggingCallback, void * callbackData);
  MQLoggingFunc getLoggingCallback() const;
  
  // sets/gets logLevel
  void setLogFileLogLevel(const LogLevel logLevel);
  LogLevel getLogFileLogLevel() const;
  void setCallbackLogLevel(const LogLevel logLevel);
  LogLevel getCallbackLogLevel() const;
  void setStdErrLogLevel(const LogLevel logLevel);
  LogLevel getStdErrLogLevel() const;

  // sets/gets the logging mask for the specified level
  void setMask(const LogLevel logLevel, const PRUint32 mask);
  PRUint32 getMask(const LogLevel logLevel) const;


  void setMaxLogSize(const PRInt32 maxLogSize);
  PRInt32 getMaxLogSize() const;
  PRInt32 getMaxLogIndex() const;

  void log(const LogLevel       logLevel,
           const char *   const filename,
           const PRInt32        lineNumber,
           const PRUint32       mask,
           const PRInt64        connectionID,
           const LogCode        logCode,
           const char *   const format,
           ...);

  void log_Severe(const char *   const filename, 
                  const PRInt32        lineNumber,
                  const PRUint32       mask,  
                  const PRInt64        connectionID,
                  const LogCode        logCode, 
                  const char *   const format, 
                  ...);

  void log_Warning(const char *   const filename, 
                   const PRInt32        lineNumber,
                   const PRUint32       mask,  
                   const PRInt64        connectionID,
                   const LogCode        logCode, 
                   const char *   const format, 
                   ...);

  void log_Info(const char *   const filename, 
                const PRInt32        lineNumber,
                const PRUint32       mask,  
                const PRInt64        connectionID,
                const LogCode        logCode, 
                const char *   const format, 
                ...);

  void log_Config(const char *   const filename, 
                  const PRInt32        lineNumber,
                  const PRUint32       mask,  
                  const PRInt64        connectionID,
                  const LogCode        logCode, 
                  const char *   const format, 
                  ...);

  void log_Fine(const char *   const filename, 
                const PRInt32        lineNumber,
                const PRUint32       mask,  
                const PRInt64        connectionID,
                const LogCode        logCode, 
                const char *   const format, 
                ...);

  void log_Finer(const char *   const filename, 
                 const PRInt32        lineNumber,
                 const PRUint32       mask,  
                 const PRInt64        connectionID,
                 const LogCode        logCode, 
                 const char *   const format, 
                 ...);

  void log_Finest(const char *   const filename, 
                  const PRInt32        lineNumber,
                  const PRUint32       mask,  
                  const PRInt64        connectionID,
                  const LogCode        logCode, 
                  const char *   const format, ...);

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Logger(const Logger& logger);
  Logger& operator=(const Logger& logger);
};


#endif

