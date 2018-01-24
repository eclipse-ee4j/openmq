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
 * @(#)LogUtils.hpp	1.14 10/17/07
 */ 

#ifndef LOGUTILS_HPP
#define LOGUTILS_HPP


#include "Logger.hpp"

// LOG_FINE( CODELOC, CLIENT_LOG_MASK, connectionID, errorCode,
//      "Something bad of the %s variety happened.", whatHappenedStr );


// CODELOC is an abbreviation so we don't have to use  __FILE__, __LINE__ everywhere
#define CODELOC  __FILE__, __LINE__


#if defined(LOG) || defined(LOG_SEVERE) || defined(LOG_WARNING) || defined(LOG_INFO) || defined(LOG_CONFIG) || defined(LOG_FINE) || defined(LOG_FINER) || defined(LOG_FINEST) 
# error "One of LOG* is already defined"
#else
# define LOG (Logger::getInstance()->log) // you should always LOG_XXX macros not this one

// We use macros for LOG_SEVERE, ... so that we can compile them away by doing something like
// # define LOG_FINER(args)   ((void)(0))


// Automatically break when we log a severe or warning message
#ifndef MQ_EXPORT_DLL_SYMBOLS
//# define LOG_SEVERE(args)  {BREAKPOINT(); (Logger::getInstance()->log_Severe)  args;}
//# define LOG_WARNING(args) {BREAKPOINT(); (Logger::getInstance()->log_Warning) args;}
# define LOG_SEVERE(args) \
  {                                                             \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();           \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= SEVERE_LOG_LEVEL) {\
    ((i_m_q_L_o_g_e_r__)->log_Severe)  args;                    \
  }                                                             \
  }
# define LOG_WARNING(args) \
  {                                                              \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();            \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel() >= WARNING_LOG_LEVEL) { \
    ((i_m_q_L_o_g_e_r__)->log_Warning)  args;                    \
  }                                                              \
  }

#else
# define LOG_SEVERE(args) \
  {                                                             \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();           \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= SEVERE_LOG_LEVEL) {\
    ((i_m_q_L_o_g_e_r__)->log_Severe)  args;                    \
  }                                                             \
  }

# define LOG_WARNING(args) \
  {                                                              \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();            \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= WARNING_LOG_LEVEL) {\
    ((i_m_q_L_o_g_e_r__)->log_Warning) args;                     \
  }                                                              \
  }

#endif //ifndef MQ_EXPORT_DLL_SYMBOLS

#define MQ_MIMIMAL_LOGGING
//#define MQ_MAXIMAL_LOGGING

# define LOG_INFO(args) \
  {                                                           \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();         \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= INFO_LOG_LEVEL) {\
    ((i_m_q_L_o_g_e_r__)->log_Info) args;                     \
  }                                                           \
  }

# define LOG_CONFIG(args) \
  {                                                             \
  Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();           \
  if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= CONFIG_LOG_LEVEL) {\
    ((i_m_q_L_o_g_e_r__)->log_Config)  args;                    \
  }                                                             \
  }

# if (defined(NDEBUG) || defined(MQ_MIMIMAL_LOGGING)) && !defined(MQ_MAXIMAL_LOGGING)
//#  define LOG_INFO(args)    ((void)(0))
//#  define LOG_CONFIG(args)  ((void)(0))
#  define LOG_FINE(args)    ((void)(0))
#  define LOG_FINER(args)   ((void)(0))
#  define LOG_FINEST(args)  ((void)(0))
# else
#  define LOG_FINE(args) \
   {                                                           \
   Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();         \
   if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= FINE_LOG_LEVEL) {\
     ((i_m_q_L_o_g_e_r__)->log_Fine) args;                     \
   }                                                           \
   }

//only used by win32 debug 
#  define LOG_FINE_NEW(args)    (Logger::getInstance()->log_Fine) args

#  define LOG_FINER(args) \
   {                                                            \
   Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();          \
   if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= FINER_LOG_LEVEL) {\
     ((i_m_q_L_o_g_e_r__)->log_Finer) args;                     \
   }                                                            \
   }

#  define LOG_FINEST(args) \
   {                                                             \
   Logger * i_m_q_L_o_g_e_r__ = Logger::getInstance();           \
   if ((i_m_q_L_o_g_e_r__)->getLogLevel()  >= FINEST_LOG_LEVEL) {\
     ((i_m_q_L_o_g_e_r__)->log_Finest)  args;                    \
   }                                                             \
   }

# endif


#endif // defined(LOG) || defined(LOG_SEVERE) ...



#define CONNECTION_LOG_MASK          (1 << 0)
#define SOCKET_LOG_MASK              (1 << 1)
#define TCP_HANDLER_LOG_MASK         (1 << 2)
#define PROTOCOL_HANDLER_LOG_MASK    (1 << 3)
#define READ_CHANNEL_LOG_MASK        (1 << 4)
#define SESSION_READER_LOG_MASK      (1 << 5)
#define READQTABLE_LOG_MASK          (1 << 6)
#define SESSION_LOG_MASK             (1 << 7)
#define HANDLED_OBJECT_LOG_MASK      (1 << 8)
#define CONSUMER_LOG_MASK            (1 << 9)
#define CODE_ERROR_LOG_MASK          (1 << 10)
#define FLOW_CONTROL_LOG_MASK        (1 << 11)
#define RECEIVE_QUEUE_LOG_MASK       (1 << 12)
#define AUTH_HANDLER_LOG_MASK        (1 << 13)
#define SSL_HANDLER_LOG_MASK         (1 << 14)
#define SSL_LOG_MASK                 (1 << 15)
#define MEMORY_LOG_MASK              (1 << 16)
#define ERROR_TRACE_LOG_MASK         (1 << 17)
#define PRODUCER_FLOWCONTROL_LOG_MASK (1 << 18)
#define MESSAGECONSUMERTABLE_LOG_MASK (1 << 19)
#define XA_SWITCH_LOG_MASK            (1 << 20)
#define HASHTABLE_LOG_MASK            (1 << 21)


// Used when the connection ID cannot be determined
#define NULL_CONN_ID        0 

#endif // LOGUTILS_HPP
