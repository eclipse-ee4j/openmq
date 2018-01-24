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
 * @(#)xaswitch.cpp	1.4 11/11/07
 */
#include "shimUtils.hpp"
#include "../io/Status.hpp"
#include "../client/Connection.hpp"
#include "../client/iMQConstants.hpp"
#include "../client/XIDObject.hpp"
#include "../containers/StringKeyHashtable.hpp"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include "mqconnection-priv.h"
#include "mqssl.h"
#include "mqstatus.h"
#include "mqxaswitch.h"
#include "xaswitch.hpp"

#define MQ_ERR_CHK(mqCall)                      \
  if (MQStatusIsError(status = (mqCall))) {  \
    goto Cleanup;                                 \
  } else {                                        \
  }

#define MQ_ERR_LOG(func, status)  \
  if (MQStatusIsError(status) == MQ_TRUE) { \
  MQString estr = MQGetStatusString(status); \
  LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status), \
               "%s:%s", func, ((estr == NULL) ? "NULL":estr) )); \
  MQFreeString(estr); \
  }

#if defined(WIN32)
#include <process.h>
#else
#include <unistd.h>
#endif

/* 
 */
struct xa_switch_t sun_mq_xa_switch =
{
	"SUN_MQ",
	TMNOFLAGS,
	0,
#ifdef __STDC__
	&mq_xa_open,
	&mq_xa_close,
	&mq_xa_start,
	&mq_xa_end,
	&mq_xa_rollback,
	&mq_xa_prepare,
	&mq_xa_commit,
	&mq_xa_recover,
	&mq_xa_forget,
	&mq_xa_complete
#else
	(int (*)())&mq_xa_open,
	(int (*)())&mq_xa_close,
	(int (*)())&mq_xa_start,
	(int (*)())&mq_xa_end,
	(int (*)())&mq_xa_rollback,
	(int (*)())&mq_xa_prepare,
	(int (*)())&mq_xa_commit,
	(int (*)())&mq_xa_recover,
	(int (*)())&mq_xa_forget,
	(int (*)())&mq_xa_complete
#endif
};


/**
 * the connection we are using for this RM
 */
static MQConnectionHandle connectionHandle = MQ_INVALID_HANDLE;
static MQConnectionHandle invalidHandle = MQ_INVALID_HANDLE;

/**
 * Thread id to recovery XID vector table
 */
static StringKeyHashtable recoveryTable;
static int _mq_recoveryScan(char * tid, XID *xids, long count, int rmid, long flags);

/**
 * index for the thread local data used to store a thread's MQXID. 
 */
static PRUintn _xidIndex;
static PRBool _xidIndexObtained = PR_FALSE;

static PRBool _nssInitialized = PR_FALSE;

static MQError _mq_parseXAInfo(char *xa_info, MQPropertiesHandle * props, 
                               char **hostname, PRInt32 * port,
                               char ** username, char ** password, 
                               char ** clientid, char ** certdbpath, PRInt32 * maxRetryCnt);
/*XXX*/
static void _mqxid_dtor(void *mqxid);

static XID   *  _mq_copyXID(XID *xid);
static MQXID *  _mq_makeMQXID(XID *xid, PRInt64 transactionID);

static MQStatus _mqOpenConnection(char *xa_info);
static MQStatus _mqOpenOpenConnection(char *xa_info);
static MQStatus _mqCloseConnection();
static MQStatus _mqStartXATransaction(MQConnectionHandle connectionHandle, 
                           XID *xid, long xaflags, PRInt64 * transactionID);
static MQStatus _mqPrepareXATransaction(MQConnectionHandle connectionHandle,
                                                     XID *xid, long xaflags);
static MQStatus _mqEndXATransaction(MQConnectionHandle connectionHandle,
                                                 XID *xid, long xaflags);
static MQStatus _mqCommitXATransaction(MQConnectionHandle connectionHandle,
                                                    XID *xid, long xaflags);
static MQStatus _mqRollbackXATransaction(MQConnectionHandle connectionHandle,
                                                      XID *xid, long xaflags);
static MQStatus _mqRecoverXATransaction(MQConnectionHandle connectionHandle,
                                    long xaflags, ObjectVector ** const xidv);

PRIntn  mq_rm_compare_mqxid_data(const void *d1, const void *d2);

void _mqexceptionListenerFunc(const MQConnectionHandle  connectionHandle,
                              MQStatus exception,
                              void * callbackData); 


static PRInt32 _maxReconnects = 0;

#define SUN_MQ_CLOSED  0
#define SUN_MQ_OPENED  1
#define SUN_MQ_BROKEN  2  

static int openState = SUN_MQ_CLOSED;
static int _myrmid = -1; 

static Monitor monitor;


EXPORTED_SYMBOL int
mq_xa_open(char *xa_info, int rmid, long flags)
{
  LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_open(xa_info=%s, rmid=%d, flags=%ld)",
              (xa_info ==  NULL ? "NULL":xa_info), rmid, flags ));

  if (xa_info == NULL) return XAER_INVAL;
  if (flags & TMASYNC) return XAER_ASYNC;

  monitor.enter();

  if (openState == SUN_MQ_OPENED) {
    LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
               "mq_xa_open(rmid=%d): rm[%d] already opened", 
                rmid, _myrmid ));
    monitor.exit();
    return XA_OK;
  }
 
  if (_xidIndexObtained == PR_FALSE) {
    if (PR_NewThreadPrivateIndex(&_xidIndex, _mqxid_dtor) != PR_SUCCESS) {
      monitor.exit();
      LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
        "mq_xa_open(xa_info=%s, rmid=%d, flags=%ld) failed to obtain thread private index",
        (xa_info ==  NULL ? "NULL":xa_info), rmid, flags ));
      return XAER_RMERR; 
    }
    _xidIndexObtained = PR_TRUE;
  }

  MQStatus status; 

  if (openState == SUN_MQ_BROKEN) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_BROKER_CONNECTION_CLOSED,
       "mq_xa_open(xa_info=%s, rmid=%d, flags=%ld) Closing broken connection ...",
       (xa_info ==  NULL ? "NULL":xa_info), rmid, flags ));
    status = _mqCloseConnection();
    if (MQGetStatusCode(status) != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
        "mq_xa_open(xa_info=%s, rmid=%d, flags=%ld) failed close broken connection: %s",
        (xa_info ==  NULL ? "NULL":xa_info), rmid, flags, MQGetStatusString(status) ));
    }
  }

  status = _mqOpenOpenConnection(xa_info);
  if (MQGetStatusCode(status) == MQ_SUCCESS) {
    openState = SUN_MQ_OPENED;
    _myrmid = rmid;
    monitor.exit();
    return XA_OK;
  }
  
  monitor.exit();
  LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
               "mq_xa_open(xa_info=%s, rmid=%d, flags=%ld) failed to connect to broker: %s", 
                (xa_info ==  NULL ? "NULL":xa_info), rmid, flags, MQGetStatusString(status) ));
  return XAER_RMERR;
}


EXPORTED_SYMBOL int 
mq_xa_close(char *xa_info, int rmid, long flags)
{
  LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_close(xa_info=%s, rmid=%d[%d], flags=%ld)",
              (xa_info ==  NULL ? "NULL":xa_info), rmid, _myrmid, flags ));

  _maxReconnects = 0;

  if (xa_info == NULL) return XAER_INVAL;
  if (flags & TMASYNC) return XAER_ASYNC;

  if (PR_GetThreadPrivate(_xidIndex) != NULL) return XAER_PROTO; 

  monitor.enter();

  if (openState == SUN_MQ_CLOSED) {
    monitor.exit();
    return XA_OK;
  }

  MQStatus status = _mqCloseConnection();
  if (MQGetStatusCode(status) != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
                  "mq_xa_close(rmid=%d[%d]) failed to close connection to broker: %s",
                   rmid, _myrmid, MQGetStatusString(status) ));
    openState = SUN_MQ_CLOSED;
    monitor.exit();
    return XAER_RMERR;
  }
  openState  = SUN_MQ_CLOSED;

  LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_close(rmid=%d[%d]) successfully closed", rmid, _myrmid ));

  monitor.exit();
  return XA_OK;
}


EXPORTED_SYMBOL int 
mq_xa_start(XID *xid, int rmid, long flags)
{
  MQStatus status;

  char *key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMFAIL );

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_start(%s, %d[%d], %ld), openState=%d\n", 
              key, rmid, _myrmid, flags, openState ));

  if (openState != SUN_MQ_OPENED) {
    DELETE( key );
    if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
    return XAER_PROTO;
  }
  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  MQXID * mqxid = (MQXID *)PR_GetThreadPrivate(_xidIndex);
  if (mqxid != NULL) {
    char *ekey = XIDObject::toString(mqxid->xid);
    if (ekey == NULL) {
      PRINT_ERROR_IF_OOM( ekey );
      DELETE( key );
      return XAER_RMERR;
    }
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XAER_PROTO,
        "mq_xa_start(%s, %d[%d], %ld): The thread has already associated with a transaction %s", 
                 key, rmid, _myrmid, flags, ekey ));
    DELETE( key );
    DELETE( ekey );
    return XAER_PROTO;
  }

  PRThread * thr = PR_GetCurrentThread();
  PRInt64 transactionID = LL_Zero();
  status = _mqStartXATransaction(connectionHandle, xid, flags, &transactionID);
  if (MQGetStatusCode(status) != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
                 "mq_xa_start(%s, %d[%d], %ld) Start transaction on thread 0x%p failed: %s",
                  key, rmid, _myrmid, flags, thr, MQGetStatusString(status) ));

    if (MQGetStatusCode(status) == MQ_BROKER_CONNECTION_CLOSED) {
      DELETE( key );
      return XAER_RMFAIL;
    }

    LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
      "mq_xa_start(%s, %d[%d], %ld): Notify broker the start failure of the transaction ...",
                   key, rmid, _myrmid, flags  ));
    status = _mqEndXATransaction(connectionHandle, xid, TMFAIL);
    if (MQGetStatusCode(status) != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
        "mq_xa_start(%s, %d[%d], %ld): Failed to notify broker the start failure of the transaction: %s",
                     key, rmid, _myrmid, flags, MQGetStatusString(status)  ));

      DELETE( key );
      return XAER_RMERR;
    } else {

      DELETE( key );
      return XA_RBROLLBACK;
    }
  }
  
  Long transactionIDLong(transactionID);
  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
      "mq_xa_start(%s, %d[%d], %ld): Started transaction %s on thread 0x%p",
               key, rmid, _myrmid, flags, transactionIDLong.toString(), thr ));
  if (PR_SetThreadPrivate(_xidIndex, 
                          _mq_makeMQXID(xid, transactionID)) != PR_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
      "mq_xa_start(%s, %d[%d], %ld): Failed to associate the thread 0x%p to the transaction %s: %s",
       key, rmid, _myrmid, flags, thr, transactionIDLong.toString(), MQGetStatusString(status) ));


    LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
      "mq_xa_start(%s, %d[%d], %ld): Notify broker the start failure of the transaction ...",
                   key, rmid, _myrmid, flags  ));
    status = _mqEndXATransaction(connectionHandle, xid, TMFAIL);
    if (MQGetStatusCode(status) != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
        "mq_xa_start(%s, %d[%d], %ld): Failed to notify broker the start failure of the transaction %s: %s",
         key, rmid, _myrmid, flags, transactionIDLong.toString(), MQGetStatusString(status)  ));

      DELETE( key );
      return XAER_RMERR;
    } else {

      DELETE( key );
      return XA_RBROLLBACK;
    }
  }

  DELETE( key );
  return XA_OK;
}


EXPORTED_SYMBOL int 
mq_xa_end(XID *xid, int rmid, long flags) 
{
  MQStatus status;

  char * key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMERR);

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_end(%s, %d[%d], %ld), openState=%d\n", 
              key, rmid, _myrmid, flags, openState ));

  MQXID * mqxid = (MQXID *)PR_GetThreadPrivate(_xidIndex);

  if (openState != SUN_MQ_OPENED) {

    if (openState == SUN_MQ_BROKEN) {
      if (mqxid != NULL) {
        if (PR_SetThreadPrivate(_xidIndex, NULL) != PR_SUCCESS) {
          char *ekey = XIDObject::toString(mqxid->xid);
          if (ekey == NULL) {
            PRINT_ERROR_IF_OOM( ekey );
            DELETE( key );
            return XAER_RMERR;
          }
          LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
            "mq_xa_end(%s, %d[%d], %ld): failed to disassociate the thread 0x%p from the transaction %s on broken connection",
            key, rmid, _myrmid, flags, PR_GetCurrentThread(), ekey ));
          DELETE( ekey );
        }
      }
      DELETE( key );
      return XAER_RMFAIL;
    }

    DELETE( key );
    return XAER_PROTO;
  }

  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  if (mqxid == NULL) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XAER_PROTO,
      "mq_xa_end(%s, %d[%d], %ld) The thread 0x%p is not associated with any transaction",
       key, rmid, _myrmid, flags, PR_GetCurrentThread() ));
    DELETE( key );
    return XAER_PROTO;
  }

  char *ekey = XIDObject::toString(mqxid->xid);
  if (ekey == NULL) {
    PRINT_ERROR_IF_OOM( ekey ); 
    DELETE( key );
    return XAER_RMERR; 
  }
  if (STRCMP(key, ekey) != 0) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XAER_PROTO,
      "mq_xa_end(%s, %d[%d], %ld) The thread 0x%p is associated with a different transaction %s",
       key, rmid, _myrmid, flags, PR_GetCurrentThread(), ekey ));
    DELETE( ekey );
    DELETE( key );
    return XAER_PROTO;
  }

  int ret = XA_OK;

  status = _mqEndXATransaction(connectionHandle, xid, flags);
  if (MQGetStatusCode(status) != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
                 "mq_xa_end(%s, %d[%d], %ld) failed to end the transaction: %s",
                  key, rmid, _myrmid, flags, MQGetStatusString(status) ));

    if (MQGetStatusCode(status) == MQ_BROKER_CONNECTION_CLOSED) {
      ret = XAER_RMFAIL;
    } else {
      ret = XAER_RMERR;
    }
  } 

  PRBool  disasso = PR_TRUE;
  if (PR_SetThreadPrivate(_xidIndex, NULL) != PR_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
      "mq_xa_end(%s, %d[%d], %ld) failed to disassociate the thread 0x%p from the transaction: %s on return %d",
       key, rmid, _myrmid, flags, PR_GetCurrentThread(), MQGetStatusString(status), ret ));
    if (ret == XA_OK) ret = XAER_RMERR;
    disasso = PR_FALSE;
  }

  if (ret != XA_OK && ret != XAER_RMFAIL) {
    LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, ret,
      "mq_xa_end(%s, %d[%d], %ld): Notify broker the end failure of the transaction ..",
       key, rmid, _myrmid, flags ));
    status = _mqEndXATransaction(connectionHandle, xid, TMFAIL);
    if (MQGetStatusCode(status) != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
        "mq_xa_end(%s, %d[%d], %ld) failed to notify broker the end failure of the transaction: %s",
         key, rmid, _myrmid, flags, MQGetStatusString(status) ));
    } else {
      if (disasso == PR_TRUE) ret = XA_RBROLLBACK;
    }
  }

  DELETE( key );
  DELETE( ekey );

  return ret;
}


EXPORTED_SYMBOL int 
mq_xa_prepare(XID *xid, int rmid, long flags)
{
  char * key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMFAIL );

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_prepare(%s, %d[%d], %ld), openState=%d\n", 
              key, rmid, _myrmid, flags, openState ));

  if (openState != SUN_MQ_OPENED) {
    DELETE( key );
    if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
    return XAER_PROTO;
  }
  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  MQStatus status = _mqPrepareXATransaction(connectionHandle, xid, flags);
  MQError errorCode = MQGetStatusCode(status);
  if (errorCode != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
              "mq_xa_prepare(%s, %d[%d], %ld) failed because '%s' (%d)\n", 
               key, rmid, _myrmid, flags, errorStr(errorCode), errorCode ));
	DELETE( key );
    return XAER_RMFAIL;
  }
  DELETE( key );
  return XA_OK;
}


EXPORTED_SYMBOL int 
mq_xa_rollback(XID *xid, int rmid, long flags)
{
  char * key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMFAIL );

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_rollback(%s, %d[%d], %ld), openState=%d\n", 
              key, rmid, _myrmid, flags, openState ));

  if (openState != SUN_MQ_OPENED) {
    DELETE( key );
    if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
    return XAER_PROTO;
  }
  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  MQStatus status = _mqRollbackXATransaction(connectionHandle, xid, flags);
  MQError errorCode = MQGetStatusCode(status);
  if (errorCode != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
                 "mq_xa_rollback(%s, %d[%d], %ld) failed because '%s' (%d)\n", 
                  key, rmid, _myrmid, flags, errorStr(errorCode), errorCode ));
    DELETE( key );
    return XAER_RMFAIL;	
  }
  DELETE( key );
  return XA_OK;
}


EXPORTED_SYMBOL int 
mq_xa_commit(XID *xid, int rmid, long flags)
{
  char * key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMFAIL );

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XA_OK,
             "mq_xa_commit(%s, %d[%d], %ld), openState=%d", 
              key, rmid, _myrmid, flags, openState ));

  if (openState != SUN_MQ_OPENED) {
    DELETE( key );
    if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
    return XAER_PROTO;
  }
  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  MQStatus status = _mqCommitXATransaction(connectionHandle, xid, flags);
  MQError errorCode = MQGetStatusCode(status); 
  if (errorCode != MQ_SUCCESS) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
              "mq_xa_commit(%s, %d[%d], %ld) failed because '%s' (%d)", 
               key, rmid, _myrmid, flags, errorStr(errorCode), errorCode ));
    DELETE( key );
    return XAER_RMFAIL;     
  }
  DELETE( key );
  return XA_OK;
}


EXPORTED_SYMBOL int 
mq_xa_recover(XID *xids, long count, int rmid, long flags)
{
  MQError errorCode = MQ_SUCCESS;
  char tid[20];
  ObjectVector * xidv = NULL;

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_OK,
             "mq_xa_recover(%ld, %d[%d], %ld), openState=%d", 
              count, rmid, _myrmid, flags, openState ));

  if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
  if (openState != SUN_MQ_OPENED) return XAER_PROTO;

  if ((xids == NULL) || (count < 0)) return XAER_INVAL;

  tid[0] ='\0';
  SNPRINTF(tid, 20, "0x%p", PR_GetCurrentThread());

  if ((flags == TMNOFLAGS)) {
    return _mq_recoveryScan(tid, xids, count, rmid, flags);
  }

  if ((flags & TMSTARTRSCAN)) {
    errorCode = recoveryTable.remove(tid);
    if (errorCode != MQ_SUCCESS && errorCode != MQ_NOT_FOUND) {
       LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
           "mq_xa_recover(%ld, %d[%d], %ld): failed to remove previous scan for thread %s because '%s' (%d)", 
            count, rmid, _myrmid, flags, tid, errorStr(errorCode), errorCode ));
       return XAER_RMERR;
    }
    xidv = NULL;
    MQStatus status = _mqRecoverXATransaction(connectionHandle, flags, &xidv);
    MQError errorCode = MQGetStatusCode(status);
    if (errorCode != MQ_SUCCESS) {
      LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
      "mq_xa_recover(%ld, %d[%d], %ld): Failed to get a recovery scan from broker for thread %s because '%s' (%d)", 
       count, rmid, _myrmid, flags, tid, errorStr(errorCode), errorCode ));
      return XAER_RMERR;
    }
    errorCode = recoveryTable.add(tid, xidv);
    if (errorCode != MQ_SUCCESS) {
      DELETE( xidv );
      return XAER_RMERR;
    }
    return _mq_recoveryScan(tid, xids, count, rmid, flags);
  }

  if ((flags & TMENDRSCAN)) {
    return _mq_recoveryScan(tid, xids, count, rmid, flags); 
  }

  return XAER_INVAL; 
}

int
_mq_recoveryScan(char * tid, XID *xids, long count, int rmid, long flags) 
{
  MQError errorCode = MQ_SUCCESS;
  ObjectVector * xidv = NULL;
  int cnt = 0;

  errorCode = recoveryTable.get(tid, (Object **)&xidv);
  if (errorCode == MQ_NOT_FOUND) {
    if (!(flags & TMSTARTRSCAN) && (flags & TMENDRSCAN)) return 0;

    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
        "mq_xa_recover(%ld, %d[%d], %ld): recovery scan not found for thread %s",
                  count, rmid, _myrmid, flags, tid ));
    if (flags == TMNOFLAGS) return XAER_INVAL;
    if ((flags & TMSTARTRSCAN) == PR_TRUE) return XAER_RMERR;
    return XAER_INVAL;
  }
  if (errorCode != MQ_SUCCESS) return XAER_RMERR;

  int xidvsize = xidv->size();
  XIDObject *xidobj = NULL;
  for (cnt = 0; cnt < count && cnt < xidvsize; cnt++) {
    errorCode = xidv->remove(0, (void ** const)&xidobj);
    if (errorCode != MQ_SUCCESS) {
      LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
          "mq_xa_recover(%ld, %d[%d], %ld): failed to remove a XID entry %d[%d] for thread %s because '%s' (%d)",
           count, rmid, _myrmid, flags, cnt, xidvsize, tid, errorStr(errorCode), errorCode ));
      return XAER_RMERR;
    }
    LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
               "mq_xa_recover(%ld, %d[%d], %ld): recovered %d[%d] for thread %s: %s",
                count, rmid, _myrmid, flags, cnt, xidvsize, tid, xidobj->toString() ));
    xids[cnt] = *(xidobj->getXID());
    DELETE( xidobj );
  }
  if (xidvsize < count || (flags & TMENDRSCAN)) {
    errorCode = recoveryTable.remove(tid);
    if (errorCode != MQ_SUCCESS) {
       LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, errorCode,
           "mq_xa_recover(%ld[%d], %d[%d], %ld): failed to remove current scan for thread %s because '%s' (%d)",
            count, xidvsize, rmid, _myrmid, flags, tid, errorStr(errorCode), errorCode ));
    }
  }
  return cnt;
}


EXPORTED_SYMBOL int 
mq_xa_forget(XID *xid, int rmid, long flags)
{
  char *key = XIDObject::toString(xid);
  RETURN_ERROR_IF_OOM( key, XAER_RMFAIL );

  LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_OK,
             "mq_xa_forget(%s, %d[%d], %ld), openState=%d", 
              key, rmid, _myrmid, flags, openState ));

  if (openState != SUN_MQ_OPENED) {
    DELETE( key );
    if (openState == SUN_MQ_BROKEN) return XAER_RMFAIL;
    return XAER_PROTO;
  }
  if (flags & TMASYNC) {
    DELETE( key );
    return XAER_ASYNC;
  }

  DELETE( key );

  /* broker currently does not support forget */
  return XA_OK;
}

/**
 */
EXPORTED_SYMBOL int 
mq_xa_complete(int *handle, int *retval, int rmid, long flags)
{
  LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, XAER_PROTO,
               "mq_xa_complete(%d[%d], %ld): Unexpected call",
                rmid, _myrmid, flags ));

  return XAER_PROTO;
}

void
_mqexceptionListenerFunc(const MQConnectionHandle  connectionHandle,
                         MQStatus exception,
                          void * callbackData) 
{
  MQString estr = MQGetStatusString(exception);
  LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(exception),
               "Connection exception occurred[rmid=%d]: %s",  _myrmid, ((estr == NULL) ? "NULL":estr) ));
  MQFreeString(estr);
  if (openState != SUN_MQ_CLOSED) {
    openState = SUN_MQ_BROKEN;
  }
}

MQStatus
_mqCloseConnection()
{
  MQStatus status = MQCloseXAConnection(connectionHandle);
  if (MQGetStatusCode(status) != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
                  "Failed to close connection to broker: %s", MQGetStatusString(status) ));
    return status;
  }
  status = MQFreeConnection(connectionHandle);
  if (MQGetStatusCode(status) != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQGetStatusCode(status),
                  "Failed to free connection: %s", MQGetStatusString(status) ));
    status.errorCode = MQ_SUCCESS;
  }
  connectionHandle = invalidHandle;

  LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
             "Closed connection to broker" ));

  return status;
}

/**
 * The format of the xa_info is:
 *
 * xa_info::= NVPair* TerminalNVPair
 * NVPair::=  TerminalNVPair ';'
 * TerminalNVPair::= Name '=' Value
 * Name::= {address | username | password | conntype | trustedhost | clientid | reconnects}
 * Value::= STRING
 *
 */
MQStatus 
_mqOpenOpenConnection(char *xa_info) 
{
  int retryCnt = 0;
  MQStatus status;

  status.errorCode = MQ_SUCCESS;
  do {
    if (retryCnt > 0) {
      LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "Retry %d[%d] connecting to broker ...", retryCnt, _maxReconnects  ));
      if (PR_Sleep(PR_SecondsToInterval(5)) == PR_FAILURE) {
        LOG_WARNING(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, PR_GetError(),
             "PR_Sleep() failed error=%d, oserror=%d before retry %d[%d] connecting",
              PR_GetError(), PR_GetOSError(), retryCnt, _maxReconnects ));
      }
    }
    retryCnt++;
    status = _mqOpenConnection(xa_info);
  } while (MQGetStatusCode(status) == MQ_COULD_NOT_CONNECT_TO_BROKER &&
           (retryCnt < _maxReconnects || _maxReconnects == -1));

  return status;
}


MQStatus 
_mqOpenConnection(char *xa_info) 
{
  char host[] = {"localhost"};
  int port = 7676;
  char guestu[] = {"guest"};
  char guestp[] = {"guest"};

  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;
  MQStatus status;

  MQString username = NULL, un = NULL;
  MQString password = NULL, up = NULL;
  MQString clientid = NULL, ci = NULL;
  MQString certdbpath  = NULL, cer = NULL;
  MQString hostname = NULL, hn = NULL;
  PRInt32 hostport = 0;
  PRInt32 retryCnt = 0, maxRetryCnt = 0;
  ConstMQString tmpstr;

  status.errorCode = _mq_parseXAInfo(xa_info, &propertiesHandle, 
                                     &hostname, &hostport, 
                                     &username, &password, &clientid, 
                                     &certdbpath, &maxRetryCnt);
  MQ_ERR_CHK( status );

  un = username;
  up = password;
  hn = hostname;
  ci = clientid; 
  cer = certdbpath; 

  if (un ==  NULL) {
    un = guestu;
    up = guestp;
  }
  if (hn == NULL) hn = host; 
  if (hostport == 0) hostport = port;
  _maxReconnects = maxRetryCnt;

  if (_nssInitialized == PR_FALSE) {
    status = MQGetStringProperty(propertiesHandle, MQ_CONNECTION_TYPE_PROPERTY, &tmpstr);
    if (status.errorCode != MQ_NOT_FOUND) {
      MQ_ERR_CHK( status );
      if (STRCMP(tmpstr, SSL_CONNECTION_TYPE) == 0 || 
          STRCMP(tmpstr, TLS_CONNECTION_TYPE) == 0) { 
        if (cer == NULL) {
          LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SSL_INIT_ERROR,
                       "NSS certificate database directory is not specified" ));
          status.errorCode = MQ_SSL_INIT_ERROR;
          MQ_ERR_CHK( status );
        }
        MQ_ERR_CHK( MQInitializeSSL(certdbpath) );
        _nssInitialized = PR_TRUE;
      }
    }
  }

  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, MQ_BROKER_HOST_PROPERTY, hn) );
  MQ_ERR_CHK( MQSetInt32Property(propertiesHandle, MQ_BROKER_PORT_PROPERTY,  hostport) );
  MQ_ERR_CHK( MQCreateXAConnection(propertiesHandle, un, up, 
                     ci, _mqexceptionListenerFunc, NULL, &connectionHandle) );

  if (ci == NULL) {
    LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "Opened XA connection to broker at %s:%d with username %s", 
                hn, hostport, un ));
  } else {
    LOG_INFO(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "Opened XA connection to broker at %s:%d with username %s and clientid %s", 
                hn, hostport, un, ci ));
  }
 
Cleanup:
  DELETE( username );
  DELETE( password );
  DELETE( hostname );
  DELETE( clientid );
  DELETE( certdbpath );
  MQ_ERR_LOG("_mqOpenConnection", status);
  return status;
}


MQError
_mq_parseXAInfo(char *xa_info, MQPropertiesHandle * props, 
                char **hostname, PRInt32 * hostport,
                char ** username, char ** password,
                char ** clientid, char ** certdbpath, PRInt32 * maxRetryCnt)
{
  ObjectVector *  nvv = NULL;
  UTF8String *nvpu = NULL;
  char *nvp = NULL, *ptr = NULL, *cp = NULL;
  char *name, *value;
  int i;
  MQError errorCode = MQ_SUCCESS;

  *hostname = NULL;
  *hostport = 0;
  *username = NULL;
  *password = NULL;
  *clientid = NULL;
  *certdbpath = NULL;
  *maxRetryCnt = 0;

  MQStatus status = MQCreateProperties(props);	
  if (MQGetStatusCode(status) != MQ_SUCCESS) { 
    MQFreeProperties(*props);
    return MQGetStatusCode(status);
  }

  if (xa_info == NULL || STRLEN(xa_info) == 0) {
     return MQ_SUCCESS;
  }

  int size;
  UTF8String xainfo(xa_info);
  ERRCHK( xainfo.tokenize(";",  &nvv) );

  size = nvv->size();
  for (i = 0; i < size; i++) {
    ERRCHK( nvv->remove(0, (void **)&nvpu) );
    nvp = (char *)nvpu->getCharStr();
    LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "parse xa_info %d[%d]element: %s", i, size, nvp ));
	ptr = strchr(nvp, '=');
	if (ptr == NULL || (size_t)(ptr-nvp) == (STRLEN(nvp)-1)) {
      DELETE( nvpu );
      continue;
    }
    name = nvp;
    *ptr = '\0';
    value = ++ptr;

    LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
               "parse xa_info %s=%s", name, value ));

    if (STRCMP(name, "address") == 0) {
	  cp = strchr(value, ':');
      if (cp == NULL || (size_t)(cp-value) == (STRLEN(value)-1)) {
        value[STRLEN(value)-1] = '\0'; 
        *hostname = new char[STRLEN(value)+1];
        if ((*hostname) == NULL) {
          ERRCHK( MQ_OUT_OF_MEMORY );
        }
        STRCPY(*hostname, value);
      } else {
        *cp = '\0';
        *hostname = new char[STRLEN(value)+1];
        if ((*hostname) == NULL) {
          ERRCHK( MQ_OUT_OF_MEMORY );
        }
        STRCPY(*hostname, value);
        *hostport = ATOI32( ++cp );
        if (*hostport <= 0) {
          ERRCHK( MQ_INVALID_PORT );
        }
      }
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info address: %s:%d", *hostname, *hostport ));

    } else if (STRCMP(name, "username") == 0) {
      *username = new char[STRLEN(value)+1];
      if ((*username) == NULL) {
        ERRCHK( MQ_OUT_OF_MEMORY );
      }
      STRCPY(*username, value);
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info username: %s", *username ));

    } else if (STRCMP(nvp, "password") == 0) {
      *password = new char[STRLEN(value)+1];
      if ((*password) == NULL) {
        ERRCHK( MQ_OUT_OF_MEMORY );
      }
      STRCPY(*password, value);
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info password" ));

    } else if (STRCMP(name, "conntype") == 0) {
      if (STRCMP(value, TCP_CONNECTION_TYPE) == 0 || 
          STRCMP(value, SSL_CONNECTION_TYPE) == 0 ||
          STRCMP(value, TLS_CONNECTION_TYPE) == 0) {
        status = MQSetStringProperty(*props, MQ_CONNECTION_TYPE_PROPERTY, (const char *)value); 
		ERRCHK( (errorCode = MQGetStatusCode(status)) );
      }
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info conntype %s", value ));

    } else if (STRCMP(name, "trustedhost") == 0) {
      if (STRCMP(value, "true") == 0 || STRCMP(value, "TRUE") == 0) {
        status = MQSetBoolProperty(*props, MQ_SSL_BROKER_IS_TRUSTED, MQ_TRUE);
		ERRCHK( (errorCode = MQGetStatusCode(status)) );
      }
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info trustedhost %s", value ));

    } else if (STRCMP(name, "clientid") == 0) {
      *clientid = new char[STRLEN(value)+20+1]; /* 20 for pid len */
      if ((*clientid) == NULL) {
        ERRCHK( MQ_OUT_OF_MEMORY );
      }
      SNPRINTF(*clientid,  (STRLEN(value)+20+1), "%s_%d", value, GETPID());
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info clientid %s", clientid ));

    } else if (STRCMP(name, "certdbpath") == 0) {
      *certdbpath = new char[STRLEN(value)+1];
      if ((*certdbpath) == NULL) {
        ERRCHK( MQ_OUT_OF_MEMORY );
      }
      STRCPY(*certdbpath, value);
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info certdbpath %s", certdbpath ));

    } else if (STRCMP(name, "reconnects") == 0) {
      *maxRetryCnt = ATOI32( value );
      LOG_FINE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_SUCCESS,
                 "parse xa_info reconnects %d", *maxRetryCnt ));
      if (*maxRetryCnt < 0) *maxRetryCnt = -1; 
    }

    DELETE( nvpu );
  }
  if (*username == NULL) {
    DELETE( *password );  
  }

  DELETE( nvv );
  DELETE( nvpu );
  return MQ_SUCCESS;

Cleanup:
  DELETE( *hostname );
  *hostport = 0;
  DELETE( *username );
  DELETE( *password );
  DELETE( *clientid );
  DELETE( *certdbpath );
  DELETE( nvv );
  DELETE( nvpu );
  MQFreeProperties(*props);
  return errorCode;
}


PRIntn
mq_rm_compare_mqxid_data(const void *d1, const void *d2)
{
  if (d1 == d2) return 1;

  MQXID *e1 = (MQXID *) d1;
  MQXID *e2 = (MQXID *) d2;

  if (LL_EQ(e1->transactionID, e2->transactionID) == 0) return 0; 

  char *key1 = XIDObject::toString(e1->xid);
  if (key1 == NULL) {
    PRINT_ERROR_IF_OOM( key1 );
    return 0;
  }
  char *key2 = XIDObject::toString(e2->xid);
  if (key2 == NULL) {
    PRINT_ERROR_IF_OOM( key2 );
    DELETE( key1 );
    return 0;
  }

  int result;
  if (STRCMP(key1, key2)) {
      result = 0;
  } else {
      result = 1;
  }
  DELETE( key1 );
  DELETE( key2 );

  return result;
}

/**
 */
XID *
_mq_copyXID(XID *xid)
{
  XID *copy = (XID *)malloc(sizeof(XID));
  if (copy == NULL) {
    LOG_SEVERE(( CODELOC, XA_SWITCH_LOG_MASK, NULL_CONN_ID, MQ_OUT_OF_MEMORY,
                 "_mq_copyXID(): malloc(%d) failed [rmid:%d]: %s", 
                  sizeof(XID), _myrmid, errorStr(MQ_OUT_OF_MEMORY) ));
    return NULL;
  }
  copy->formatID = xid->formatID;
  copy->gtrid_length = xid->gtrid_length;
  copy->bqual_length = xid->bqual_length;
  memcpy(copy->data, xid->data, (xid->gtrid_length + xid->bqual_length));

  return copy;
}

MQXID *
_mq_makeMQXID(XID *xid, PRInt64 transactionID)
{
  MQXID *copy = (MQXID *) malloc(sizeof(MQXID));
  if (copy == NULL) return NULL;

  copy->xid = _mq_copyXID(xid); 
  if (copy->xid == NULL) {
    free(copy);
    return NULL;
  }
  copy->transactionID = transactionID;

  return copy;
}


/**
 */
void 
_mqxid_dtor(void *mqxid)
{
  if (mqxid != NULL) {
    if (((MQXID *)mqxid)->xid != NULL) {
      free((XID *)(((MQXID *)mqxid)->xid));
    }
    free((MQXID *)mqxid);
  }
}


MQConnectionHandle 
mq_getXAConnection()
{
  return connectionHandle;
}

PRUintn
mq_getXidIndex()
{
  return _xidIndex;
}


MQStatus
_mqStartXATransaction(MQConnectionHandle connectionHandle, 
                      XID *xid, long xaflags, PRInt64 * transactionID)
{
  static const char FUNCNAME[] = "_mqStartXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE );
  ERRCHK( connection->startTransaction(xid, xaflags, transactionID) );


  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


MQStatus
_mqEndXATransaction(MQConnectionHandle connectionHandle,
                    XID *xid, long xaflags)
{
  static const char FUNCNAME[] = "_mqEndXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE);
  ERRCHK(connection->endTransaction((PRInt64)0, xid, xaflags) );


  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

MQStatus
_mqPrepareXATransaction(MQConnectionHandle connectionHandle,
                        XID *xid, long xaflags)
{
  static const char FUNCNAME[] = "_mqPrepareXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE);
  ERRCHK( connection->prepareTransaction((PRInt64)0, xid) );


  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

MQStatus
_mqCommitXATransaction(MQConnectionHandle connectionHandle,
                        XID *xid, long xaflags)
{
  static const char FUNCNAME[] = "_mqCommitXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;
  PRInt32 replyStatus = STATUS_ERROR;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE);
  ERRCHK( connection->commitTransaction((PRInt64)0, xid, xaflags, &replyStatus) );

  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


MQStatus
_mqRollbackXATransaction(MQConnectionHandle connectionHandle,
                        XID *xid, long xaflags)
{
  static const char FUNCNAME[] = "_mqRollbackXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE);
  ERRCHK( connection->rollbackTransaction((PRInt64)0, xid) );


  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}


MQStatus
_mqRecoverXATransaction(MQConnectionHandle connectionHandle,
                        long xaflags, ObjectVector ** const xidv)
{
  static const char FUNCNAME[] = "_mqRecoverXATransaction";
  MQError errorCode = MQ_SUCCESS;
  Connection * connection = NULL;

  CLEAR_ERROR_TRACE(PR_FALSE);

  // Convert connectionHandle to a Connection pointer
  connection = (Connection*)getHandledObject(connectionHandle.handle,
                                             CONNECTION_OBJECT);
  CNDCHK( connection == NULL, MQ_STATUS_INVALID_HANDLE);
  ERRCHK( connection->recoverTransaction(xaflags, xidv) );


  // Release our pointer to the connection.
  releaseHandledObject(connection);

  RETURN_STATUS( MQ_SUCCESS );
Cleanup:
  releaseHandledObject(connection);
  MQ_ERROR_TRACE( FUNCNAME, errorCode );
  RETURN_STATUS( errorCode );
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

