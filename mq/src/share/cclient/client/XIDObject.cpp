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
 * @(#)XIDObject.cpp	1.2 10/23/07
 */ 

#include "XIDObject.hpp"
#include "../util/UtilityMacros.h"

#define NULLXID     -1L
#define NULLXID_STR "NULLXID_STR" 

/**
 * Construct an unititialized XID to be used by readID
 */
XIDObject::XIDObject()
{
  CHECK_OBJECT_VALIDITY();
  this->init();
}

XIDObject::~XIDObject()
{
  CHECK_OBJECT_VALIDITY();

  reset();
}


void
XIDObject::init()
{
  CHECK_OBJECT_VALIDITY();

  xid.formatID  = NULLXID;
  xid.gtrid_length = 0L;
  xid.bqual_length =0L;
  memset(xid.data, 0, XIDDATASIZE);

  xidStr = NULL;
}

void
XIDObject::reset()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( xidStr );
  init();
}


MQError
XIDObject::write(IMQDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  RETURN_IF_ERROR( out->writeUint32(xid.formatID) );
  RETURN_IF_ERROR( out->writeUint16((PRUint16)xid.gtrid_length) );
  RETURN_IF_ERROR( out->writeUint16((PRUint16)xid.bqual_length) );
  RETURN_IF_ERROR( out->writeUint8Array((PRUint8 *)xid.data, XIDDATASIZE) );
  
  return MQ_SUCCESS;
}


MQError
XIDObject::read(IMQDataInputStream * const in)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  RETURN_ERROR_IF_NULL( in );
  reset();

  PRUint16 v = 0;
  ERRCHK( in->readUint32((PRUint32 *)&(xid.formatID)) );
  ERRCHK( in->readUint16(&v) );
  xid.gtrid_length = v;
  ERRCHK( in->readUint16(&v) );
  xid.bqual_length = v;
  ERRCHK( in->readUint8Array((PRUint8 *)xid.data, XIDDATASIZE) );

  return MQ_SUCCESS;

Cleanup:
  reset();
  return errorCode;
}

MQError
XIDObject::copy(const XID * xidin)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( xidin );
  reset();

  xid.formatID = xidin->formatID;
  xid.gtrid_length = xidin->gtrid_length;
  xid.bqual_length = xidin->bqual_length;
  memcpy(xid.data, xidin->data, xidin->gtrid_length+xidin->bqual_length);
  return MQ_SUCCESS;
}

/**
 *
 */
XID *
XIDObject::getXID()
{
  CHECK_OBJECT_VALIDITY();

  return &(this->xid);
}


PRInt32
XIDObject::xidSize()
{
  return sizeof(PRUint32) + 2*sizeof(PRUint16)+XIDDATASIZE*sizeof(PRUint8);
}

char *
XIDObject::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (xidStr != NULL) {
    return xidStr;
  }
  xidStr = XIDObject::toString(this->getXID());
  return xidStr;

}


static const char _hextab[] = "0123456789ABCDEF";

/**
 * make a null-terminated string
 * caller is responsible to free the returned string
 *
 * @return NULL if out of memory or xid is NULL
 */
char *
XIDObject::toString(XID *xidin) 
{
  char * key = NULL;

  if (xidin == NULL) return NULL;

  if (xidin->formatID == NULLXID) {
    key = new char[STRLEN(NULLXID_STR)+1];
    if (key == NULL) return NULL;

    STRCPY(key, NULLXID_STR);
    return key;
  }

  int i, j, value;
  size_t len = (size_t)(xidin->gtrid_length + xidin->bqual_length);

  key = new char[2*len+1];
  if (key == NULL) return NULL;
  memset(key, 0, 2*len+1);

  j = 0;
  for (i = 0; i < (int)len; i++) {
    value = xidin->data[i]&0xff;
    key[j++] = _hextab[value/16];
    key[j++] = _hextab[value&15];
  }
  key[2*len] = '\0';
  return key;
}

