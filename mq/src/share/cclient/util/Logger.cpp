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
 * @(#)Logger.cpp	1.19 10/17/07
 */ 

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <stdio.h>
#include <prenv.h>
#if defined(WIN32)
#include <process.h>
#else
#include <unistd.h>
#endif

#ifdef __cplusplus
}
#endif /* __cplusplus */

#include "Logger.hpp"
#include "LogUtils.hpp"
#include "../util/UtilityMacros.h"
#include "../cshim/iMQCallbackUtils.hpp"

static const int MAX_PRINT_LOG_LEVEL_STR_LEN = 7; // WARNING
static const int MAX_PRINT_LOG_CODE_LEN = 7; 
static const int MAX_PRINT_CONN_ID_LEN = 21;
static const int MAX_PRINT_FILE_NAME_LEN = 23;
static const int MAX_PRINT_LINE_NUMBER_LEN = 4;

Logger::Logger()
{
  this->init();
}



Logger::~Logger() 
{
  this->reset();
}

void
Logger::init()
{
  m_logFileLogLevel  = CONFIG_LOG_LEVEL;
  m_callbackLogLevel = CONFIG_LOG_LEVEL;
  m_stdErrLogLevel   = CONFIG_LOG_LEVEL;
  
  char * env = NULL;
  if ((env = PR_GetEnv("MQ_LOG_LEVEL")) != NULL && env[0]) {
    int cnt = 0, enval = NONE_LOG_LEVEL-1;
    cnt = sscanf(env, "%d", &enval);
    if (cnt == 0 || cnt == EOF ||
        enval < NONE_LOG_LEVEL ||
        enval > FINEST_LOG_LEVEL) { 
      fprintf(stderr, "WARNING: Invalid MQ_LOG_LEVEL %s\n", env);
    }
    else {
      m_logFileLogLevel  = enval;
      m_callbackLogLevel = enval;
      m_stdErrLogLevel   = enval;
    }
  }
  if (m_logFileLogLevel > m_callbackLogLevel) {
    g_logLevel = m_logFileLogLevel;
  } else if (m_callbackLogLevel > m_stdErrLogLevel) { 
    g_logLevel = m_callbackLogLevel;
  } else {
    g_logLevel = m_stdErrLogLevel;
  }

  STRCPY( m_logFileName, "" );
  m_logFileName_gptr = NULL;
  m_logFile          = NULL;
  int pid = 0;
  if ((env = PR_GetEnv("MQ_LOG_FILE_APPEND_PID")) != NULL && env[0]) {
    pid = (int)GETPID();
  }
  if ((env = PR_GetEnv("MQ_LOG_FILE")) != NULL && env[0]) {
    if (pid == 0) {
      STRNCPY(m_logFileName, env,  sizeof(m_logFileName));
    } else {
      SNPRINTF(m_logFileName, sizeof(m_logFileName), "%s_%d", env, pid);
    }
    m_logFileName[sizeof(m_logFileName)-1] = '\0';
    this->parseLogFileName(m_logFileName, &m_logFileName_gptr);
    this->openLogFile(m_logFileName, m_logFileName_gptr);
  }
  
  m_loggingCallback  = NULL;
  m_callbackData     = NULL;
  m_maximumLogSize   = LOGGER_DEFAULT_MAXIMUM_LOG_SIZE;

  // init the masks to FFFFFFFF for all levels
  for (LogLevel l = MIN_LOG_LEVEL; l <= MAX_LOG_LEVEL; l++) {
    m_mask[l] = (PRUint32)~0;
  }

  if ((env = PR_GetEnv("MQ_LOG_MASK")) != NULL && env[0]) {
    int cnt = 0, enval = 0;
    cnt = sscanf(env, "%d", &enval);
    if (cnt == 0 || cnt == EOF) {
        fprintf(stderr, "WARNING: Invalid MQ_LOG_MASK %s\n", env);
    } else {
      for (LogLevel l = MIN_LOG_LEVEL; l <= MAX_LOG_LEVEL; l++) {
        m_mask[l] = (PRUint32)enval;
      }
    }
  }

}

void
Logger::reset()
{
  // just close the file... nothing was dynamically allocated
  if (m_logFile != NULL) {
    fclose(m_logFile);
    m_logFile = NULL;
  }
  this->init();
}

void
Logger::setLogFileName(const char * const logfilename)
{
  if (logfilename == NULL) {
    STRCPY(m_logFileName, "");
    this->openLogFile(NULL, NULL);
  } else {
    STRNCPY(m_logFileName, logfilename, sizeof(m_logFileName));
    m_logFileName[sizeof(m_logFileName)-1] = '\0';
    this->parseLogFileName(m_logFileName, &m_logFileName_gptr);
    this->openLogFile(m_logFileName, m_logFileName_gptr);
  }
}

//XXX no i18n
void
Logger::parseLogFileName(char * logfilename, char **gptr)
{
  char abbrFilename[LOGGER_MAX_LOG_NAME_SIZE];
  const char * slashptr = NULL, *pctptr = NULL;
  char *ptr = NULL;
  PRUptrdiff termindex = 0;
  PRBool escape = PR_FALSE;

  if (gptr == NULL) return; //should not happen
  *gptr = NULL; 
#if defined(XP_UNIX)
  if ((slashptr = STRRCHR(logfilename, '/')) != NULL) {
#elif defined(WIN32) //XXX
  if ((slashptr = STRRCHR(logfilename, '\\')) != NULL) {
#else //XXX really not currently supported 
  return;
#endif
    STRNCPY(abbrFilename, &(slashptr[1]), sizeof(abbrFilename));
    abbrFilename[sizeof(abbrFilename)-1] = '\0';
  } else {
    STRNCPY(abbrFilename, logfilename, sizeof(abbrFilename));
    abbrFilename[sizeof(abbrFilename)-1] = '\0';
  }
  while (STRLEN(abbrFilename) > 0) {
  if ((pctptr = STRRCHR(abbrFilename, '%')) != NULL) {
    if (*(pctptr+1)  == 'g') {
      if (pctptr > abbrFilename && *(pctptr -1) == '%') { //%%g
        pctptr--;
        escape = PR_TRUE;
      } 
      if (slashptr != NULL) {
        termindex = ((slashptr+1)-logfilename)+(pctptr-abbrFilename);
      } else {
        termindex = pctptr-abbrFilename;
      }
      logfilename[termindex] = '\0';
      if (escape == PR_TRUE) {
        STRCAT(logfilename, logfilename+termindex+1);
      } else {
        *gptr = logfilename + termindex+2;
      }
      return;
    }
    termindex = pctptr - abbrFilename;
    abbrFilename[termindex]= '\0';
    continue;
  } else { 
    break;
  }
  }//while
}

void
Logger::openLogFile(const char * const filename, const char * const gptr)
{
  char logFileName[LOGGER_MAX_LOG_NAME_SIZE];

  // Close the current log file
  if (m_logFile != NULL) {
    fclose(m_logFile);
  }  
  
  if (filename == NULL) {
    return;
  }
  
  // Remove the oldest log
  if (gptr == NULL) {
  SNPRINTF(logFileName, sizeof(logFileName), "%s.%d",
           filename, this->getMaxLogIndex() );
  } else {
  SNPRINTF(logFileName, sizeof(logFileName), "%s%d%s",
           filename, this->getMaxLogIndex(), gptr);
  }
  logFileName[sizeof(logFileName)-1] = '\0';
  remove(logFileName);
  
  // Slide the other logs down.
  // log8.txt -> log9.txt, log7.txt -> log8.txt ...
  for (int i = this->getMaxLogIndex(); i > 0; i--) {
    char logFileNameOld[LOGGER_MAX_LOG_NAME_SIZE];
    char logFileNameNew[LOGGER_MAX_LOG_NAME_SIZE];

    if (gptr == NULL) {
    SNPRINTF(logFileNameOld, sizeof(logFileNameOld), "%s.%d", filename, i - 1);
    SNPRINTF(logFileNameNew, sizeof(logFileNameNew), "%s.%d", filename, i);
    } else {
    SNPRINTF(logFileNameOld, sizeof(logFileNameOld), "%s%d%s", filename, i - 1, gptr);
    SNPRINTF(logFileNameNew, sizeof(logFileNameNew), "%s%d%s", filename, i, gptr);
    }
    logFileNameOld[sizeof(logFileNameOld)-1] = '\0';
    logFileNameNew[sizeof(logFileNameNew)-1] = '\0';
    rename(logFileNameOld, logFileNameNew);
  }

  // Open the current log
  if (gptr == NULL) {
  SNPRINTF(logFileName, sizeof(logFileName), "%s.%d", filename, 0 );
  } else {
  SNPRINTF(logFileName, sizeof(logFileName), "%s%d%s", filename, 0, gptr);
  }
  logFileName[sizeof(logFileName)-1] = '\0';
  m_logFile = fopen(logFileName, "w");
  if (m_logFile != NULL) {
  fprintf(m_logFile, "Log file opened.\n");
  fflush(m_logFile);
  } else {
  fprintf(stderr, "WARNING: Unable to open log file %s\n", logFileName);
  fflush(stderr);
  }
}

void
Logger::setMaxLogSize(const PRInt32 maxLogSize)
{
  m_monitor.enter();
    m_maximumLogSize = maxLogSize;
  m_monitor.exit();
}

PRInt32
Logger::getMaxLogSize() const
{
  return m_maximumLogSize;
}


void 
Logger::setLoggingCallback(MQLoggingFunc const loggingCallback, void * callbackData) 
{
  m_monitor.enter();
  m_loggingCallback = loggingCallback;
  m_callbackData = callbackData;
  m_monitor.exit();
}

MQLoggingFunc
Logger::getLoggingCallback() const 
{
  return m_loggingCallback;
}

LogLevel
Logger::getLogLevel()
{
  return g_logLevel;
}

void 
Logger::setLogFileLogLevel(const LogLevel logFileLogLevel) 
{
  m_monitor.enter();
    if (logFileLogLevel < MIN_LOG_LEVEL) {
      m_logFileLogLevel = MIN_LOG_LEVEL;
    } else if (logFileLogLevel > MAX_LOG_LEVEL) {
      m_logFileLogLevel = MAX_LOG_LEVEL;
    } else {
      m_logFileLogLevel = logFileLogLevel;
    }
    if (m_logFileLogLevel > g_logLevel) {
      g_logLevel = m_logFileLogLevel;
    }
  m_monitor.exit();
}

LogLevel 
Logger::getLogFileLogLevel() const 
{
  return m_logFileLogLevel;
}

void 
Logger::setCallbackLogLevel(const LogLevel callbackLogLevel) 
{
  m_monitor.enter();
    if (callbackLogLevel < MIN_LOG_LEVEL) {
      m_callbackLogLevel = MIN_LOG_LEVEL;
    } else if (callbackLogLevel > MAX_LOG_LEVEL) {
      m_callbackLogLevel = MAX_LOG_LEVEL;
    } else {
      m_callbackLogLevel = callbackLogLevel;
    }
    if (m_callbackLogLevel > g_logLevel) {
      g_logLevel = m_callbackLogLevel;
    }
  m_monitor.exit();
}

LogLevel 
Logger::getCallbackLogLevel() const 
{
  return m_callbackLogLevel;
}

void 
Logger::setStdErrLogLevel(const LogLevel stdErrLogLevel) 
{
  m_monitor.enter();
    if (stdErrLogLevel < MIN_LOG_LEVEL) {
      m_stdErrLogLevel = MIN_LOG_LEVEL;
    } else if (stdErrLogLevel > MAX_LOG_LEVEL) {
      m_stdErrLogLevel = MAX_LOG_LEVEL;
    } else {
      m_stdErrLogLevel = stdErrLogLevel;
    }
    if (m_stdErrLogLevel > g_logLevel) {
      g_logLevel = m_stdErrLogLevel;
    }
  m_monitor.exit();
}

LogLevel 
Logger::getStdErrLogLevel() const 
{
  return m_stdErrLogLevel;
}

void 
Logger::setMask(const LogLevel logLevel, const PRUint32 mask) 
{
  m_monitor.enter();
    if ((logLevel >= MIN_LOG_LEVEL) && (logLevel <= MAX_LOG_LEVEL)) {
      m_mask[logLevel] = mask;
    }
  m_monitor.exit();
}

PRUint32 
Logger::getMask(const LogLevel logLevel) const 
{
  if ((logLevel < MIN_LOG_LEVEL) || (logLevel > MAX_LOG_LEVEL)) {
    return 0;
  }

  return (m_mask[logLevel]);
}

PRInt32
Logger::getMaxLogIndex() const
{
  return LOGGER_DEFAULT_MAXIMUM_LOG_INDEX;
}

void
Logger::logva(const LogLevel       logLevel,
              const char *   const filename,
              const PRInt32        lineNumber,
              const PRUint32       mask,
              const PRInt64        connectionID,
              const LogCode        logCode,
              const char *   const format,
                    va_list        argptr) 
{
  if (logLevel < 0) {
    return;
  }
  //perf: we do not support changing these on-the-fly
  if ((m_loggingCallback == NULL 
       || logLevel > m_callbackLogLevel || m_callbackLogLevel < MIN_LOG_LEVEL)
      && (m_logFile == NULL 
          || logLevel > m_logFileLogLevel || m_logFileLogLevel < MIN_LOG_LEVEL)
      && (logLevel > m_stdErrLogLevel || m_stdErrLogLevel < MIN_LOG_LEVEL)) {
    return; 
  }

  m_monitor.enter();

  PRInt64 time = (PRInt64)PR_Now();

  // execute the callback immediately if necessary
  if ((m_loggingCallback != NULL)          && 
      (logLevel <= m_callbackLogLevel)     && 
      (m_callbackLogLevel >= MIN_LOG_LEVEL)) 
  {
    invokeLoggingCallback(logLevel, logCode, format, time, connectionID,
                       filename, lineNumber, m_loggingCallback, m_callbackData);
  }

  //// check the logLevel for the other outputs
  //if ((logLevel > m_logLevel) && (logLevel > m_stdErrLogLevel)) {
  //  m_monitor.exit();
  //  return;
  //}

  // check the mask
  if ((m_mask[logLevel] & mask) == 0) {
    m_monitor.exit();
    return;
  }

  // get the time
  PRExplodedTime et;
  PR_ExplodeTime(time, PR_LocalTimeParameters, &et);
  // increment the month - it's behind
  et.tm_month++;
  void * threadID = (void *)(PR_GetCurrentThread());

  // write the output string with its expanded args
  char outString[10000];
  PR_vsnprintf(outString, sizeof(outString), format, argptr);
  outString[sizeof(outString)-1] = '\0';

  const char* SEVERITY[] = {"SEVERE", "WARNING", "INFO", "CONFIG",
                            "FINE", "FINER", "FINEST"};

  // Eliminate the directory path of the file
  const char * abbrFilename = NULL;
  if (((abbrFilename = STRRCHR(filename, '/')) != NULL) ||
      ((abbrFilename = STRRCHR(filename, '\\')) != NULL))
  {
    abbrFilename = &(abbrFilename[1]); // skip past the last "/"
  } else {
    abbrFilename = filename;
  }

  // write the outstring to file or stderr
  if ((m_logFileLogLevel >= MIN_LOG_LEVEL) && 
      (logLevel  <= m_logFileLogLevel)    && 
      (m_logFile != NULL)) 
  {
    Long connectionIDLong(connectionID);
    this->openNewLogIfNecessary();


    fprintf(m_logFile, 
            "%-*s "
            "logCode=%-*d "
            "conn=%*s "
#if defined(AIX)
            "td=0x%p "
#else
            "td=0x%08p "
#endif
            "(%d/%02d/%02d %02d:%02d:%02d.%03d) "
            "%*s "
            "%*d "
            "        \"%s\"\n",
            MAX_PRINT_LOG_LEVEL_STR_LEN, SEVERITY[logLevel],
            MAX_PRINT_LOG_CODE_LEN, logCode,
            MAX_PRINT_CONN_ID_LEN, connectionIDLong.toString(),
            threadID,
            et.tm_year, et.tm_month, et.tm_mday, et.tm_hour, et.tm_min, et.tm_sec, et.tm_usec/1000,
            
            MAX_PRINT_FILE_NAME_LEN, abbrFilename,
            MAX_PRINT_LINE_NUMBER_LEN, lineNumber, 
            outString );

    /*    fprintf(m_logFile, 
            "%s: msgid=%d conn=%d td=0x%p "
            "(%d/%02d/%02d %02d:%02d:%02d.%03d) "
            "%s:%d \n"
            "    \"%s\"\n", 
            SEVERITY[logLevel], logCode, connectionID, threadID,
            et.tm_year, et.tm_month, et.tm_mday, 
            et.tm_hour, et.tm_min, et.tm_sec, et.tm_usec/1000,
            abbrFilename, lineNumber, 
            outString );*/
#define LOGGER_FFLUSH
#ifdef LOGGER_FFLUSH
    fflush(m_logFile);
#endif
  }
  if ((m_stdErrLogLevel >= MIN_LOG_LEVEL) && 
      (logLevel <= m_stdErrLogLevel) && 
      (m_logFile == NULL)) 
  {
    fprintf(stderr, 
#ifdef LOG_THREAD_INFO
            "%s: msgid=%d conn=%d td=0x%p "
            "(%d/%02d/%02d %02d:%02d:%02d.%06d) "
            "%s:%d \n"
#endif // LOG_THREAD_INFO
            "    \"%s\"\n", 
#ifdef LOG_THREAD_INFO
            SEVERITY[logLevel], logCode, connectionID, threadID,
            et.tm_year, et.tm_month, et.tm_mday, 
            et.tm_hour, et.tm_min, et.tm_sec, et.tm_usec/1000,
            abbrFilename, lineNumber, 
#endif // LOG_THREAD_INFO

            outString );
#ifdef LOGGER_FFLUSH
    fflush(stderr);
#endif
  }
  m_monitor.exit();
}

void
Logger::openNewLogIfNecessary()
{
  if (m_logFile != NULL) {
    long filePosition;
    if ((filePosition=ftell(m_logFile)) >=0 ) {
      if (filePosition > this->getMaxLogSize()) {
        openLogFile(m_logFileName, m_logFileName_gptr);
      }
    }
  }
}

void 
Logger::log(const LogLevel       logLevel,
            const char *   const filename,
            const PRInt32        lineNumber,
            const PRUint32       mask,
            const PRInt64        connectionID,
            const LogCode        logCode,
            const char *   const format,
            ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(logLevel,
        filename,
	lineNumber,
	mask,
	connectionID,
	logCode,
	format,
	argptr);
}

void 
Logger::log_Severe(const char *   const filename,
                   const PRInt32        lineNumber,
                   const PRUint32       mask,
                   const PRInt64        connectionID,
                   const LogCode        logCode,
                   const char *   const format,
                   ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(SEVERE_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

void 
Logger::log_Warning(const char *   const filename,
                    const PRInt32        lineNumber,
                    const PRUint32       mask,
                    const PRInt64        connectionID,
                    const LogCode        logCode,
                    const char *   const format,
                    ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(WARNING_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

void 
Logger::log_Info(const char *   const filename,
                 const PRInt32        lineNumber,
                 const PRUint32       mask,
                 const PRInt64        connectionID,
                 const LogCode        logCode,
                 const char *   const format,
                 ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(INFO_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);

}

void 
Logger::log_Config(const char *   const filename,
                   const PRInt32        lineNumber,
                   const PRUint32       mask,
                   const PRInt64        connectionID,
                   const LogCode        logCode,
                   const char *   const format,
                   ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(CONFIG_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

void 
Logger::log_Fine(const char *   const filename,
                 const PRInt32        lineNumber,
                 const PRUint32       mask,
                 const PRInt64        connectionID,
                 const LogCode        logCode,
                 const char *   const format,
                 ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(FINE_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

void 
Logger::log_Finer(const char *   const filename,
                  const PRInt32        lineNumber,
                  const PRUint32       mask,
                  const PRInt64        connectionID,
                  const LogCode        logCode,
                  const char *   const format,
                  ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(FINER_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

void 
Logger::log_Finest(const char *   const filename,
                   const PRInt32        lineNumber,
                   const PRUint32       mask,
                   const PRInt64        connectionID,
                   const LogCode        logCode,
                   const char *   const format,
                   ...) 
{
  va_list argptr;
  va_start(argptr, format);
  logva(FINEST_LOG_LEVEL,
        filename,
        lineNumber,
        mask,
        connectionID,
        logCode,
        format,
        argptr);
}

//
// Static methods
//

Logger * Logger::instance = NULL;

/*
 *
 */
Logger*
Logger::getInstance()
{
  if (instance == NULL) {
    instance = new Logger();
  }
  return instance;
}

void
Logger::deleteInstance()
{
  DELETE(instance);
}


// TESTING METHODS

extern "C" {
  void logSomething(void * arg);
}


const PRInt32 MAX_NUM_LOG_THREADS = 10000;

void 
Logger::test(PRInt32 numLogThreads, const PRInt32 itemsToLog)
{
  int i;
  int numItemsToLog = itemsToLog;
  PRThread* logThreads[MAX_NUM_LOG_THREADS];

  // Create the logging threads
  numLogThreads = MIN(numLogThreads, MAX_NUM_LOG_THREADS);
  for (i = 0; i < numLogThreads; i++) {
    logThreads[i] = PR_CreateThread(PR_SYSTEM_THREAD, logSomething, (void*)&numItemsToLog,  
                                    PR_PRIORITY_NORMAL, PR_LOCAL_THREAD, PR_JOINABLE_THREAD, 0);
  }

  // Wait for the logging threads to finish
  for (i = 0; i < numLogThreads; i++) {
    if (logThreads[i] != NULL) {
      PR_JoinThread(logThreads[i]);
    }
  }
}



#define SOME_LOG_MASK 0x00000001

void 
logSomething(void * arg) 
{
  PRInt32 itemsToLog = *(PRInt32*)arg;
  int j;
  
  for (j = 0; j < itemsToLog; j++) {
    LOG_FINE(( CODELOC, SOME_LOG_MASK, 1, j, "Some error. %d", j ));
    LOG_FINER(( CODELOC, SOME_LOG_MASK, 1, j, "This line should not appear." ));

    PR_Sleep(0);  // Yield
  }
}


