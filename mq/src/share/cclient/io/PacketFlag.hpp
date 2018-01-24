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
 * @(#)PacketFlag.hpp	1.4 06/26/07
 */ 

#ifndef PACKETFLAG_HPP
#define PACKETFLAG_HPP

#include <nspr.h>

static const PRUint16 PACKET_FLAG_IS_QUEUE            =  1 << 0;  // Q_FLAG
static const PRUint16 PACKET_FLAG_REDELIVERED         =  1 << 1;  // R_FLAG
static const PRUint16 PACKET_FLAG_PERSISTENT          =  1 << 2;  // P_FLAG
static const PRUint16 PACKET_FLAG_SELECTORS_PROCESSED =  1 << 3;  // S_FLAG
static const PRUint16 PACKET_FLAG_SEND_ACK            =  1 << 4;  // A_FLAG
static const PRUint16 PACKET_FLAG_LAST_MESSAGE        =  1 << 5;  // L_FLAG
static const PRUint16 PACKET_FLAG_FLOW_PAUSED         =  1 << 6;  // F_FLAG
static const PRUint16 PACKET_FLAG_PART_OF_TRANSACTION =  1 << 7;  // T_FLAG
static const PRUint16 PACKET_FLAG_CONSUMER_FLOW_PAUSED = 1 << 8;  // C_FLAG
static const PRUint16 PACKET_FLAG_SERVER_PACKET        = 1 << 9;  // B_FLAG


/**
 * This class defines bit masks for the iMQ packet header flags.
 */
class PacketFlag {
public:
  static const char * isQueueStr(const PRUint16 bitFlags);
  static const char * isRedeliveredStr(const PRUint16 bitFlags);
  static const char * isPersistentStr(const PRUint16 bitFlags);
  static const char * isSelectorsProcessedStr(const PRUint16 bitFlags);
  static const char * isSendAckStr(const PRUint16 bitFlags);
  static const char * isLastMessageStr(const PRUint16 bitFlags);
  static const char * isFlowPausedStr(const PRUint16 bitFlags);
  static const char * isPartOfTransactionStr(const PRUint16 bitFlags);
  static const char * isConsumerFlowPausedStr(const PRUint16 bitFlags);
  static const char * isServerPacketStr(const PRUint16 bitFlags);
};


#endif //  PACKETFLAG_HPP
