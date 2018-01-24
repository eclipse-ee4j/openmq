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
 * @(#)PacketFlag.cpp	1.4 06/26/07
 */ 

#include "PacketFlag.hpp"

/**
 *
 */
const char * 
PacketFlag::isQueueStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_IS_QUEUE) {
    return "IS_QUEUE ";
  } else {
    return "";
  }
}


/**
 *
 */
const char * 
PacketFlag::isRedeliveredStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_REDELIVERED) {
    return "REDLIVERED ";
  } else {
    return "";
  }
}



/**
 *
 */
const char * 
PacketFlag::isPersistentStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_PERSISTENT) {
    return "PERSISTENT ";
  } else {
    return "";
  }
}

/**
 *
 */
const char * 
PacketFlag::isSelectorsProcessedStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_SELECTORS_PROCESSED) {
    return "SELECTORS_PROCESSED ";
  } else {
    return "";
  }
}

/**
 *
 */
const char * 
PacketFlag::isSendAckStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_SEND_ACK) {
    return "SEND_ACK ";
  } else {
    return "";
  }
}

/**
 *
 */
const char * 
PacketFlag::isLastMessageStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_LAST_MESSAGE) {
    return "LAST_MESSAGE ";
  } else {
    return "";
  }
}

/**
 *
 */
const char * 
PacketFlag::isFlowPausedStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_FLOW_PAUSED) {
    return "FLOW_PAUSED ";
  } else {
    return "";
  }
}

/**
 *
 */
const char *
PacketFlag::isPartOfTransactionStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_PART_OF_TRANSACTION) {
    return "PART_OF_TRANSACTION ";
  } else {
    return "";
  }
}

/**
 *
 */
const char *
PacketFlag::isConsumerFlowPausedStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_CONSUMER_FLOW_PAUSED) {
    return "CONSUMER_FLOW_PAUSED ";
  } else {
    return "";
  }
}

/**
 *
 */
const char *
PacketFlag::isServerPacketStr(const PRUint16 bitFlags)
{
  if (bitFlags & PACKET_FLAG_SERVER_PACKET) {
    return "SERVER_PACKET ";
  } else {
    return "";
  }
}


