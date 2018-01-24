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
 * @(#)BrokerAdminException.java	1.14 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import javax.jms.Message;

/**
 *  This class used by imqcmd, imqadmin ,imqbridgemgr
 *  Please create subcass if need individual admin tool specific references 
 */
public class BrokerAdminException extends Exception {

    public static final int	CONNECT_ERROR		= 0;
    public static final int	MSG_SEND_ERROR		= 1;
    public static final int	MSG_REPLY_ERROR		= 2;
    public static final int	CLOSE_ERROR		= 3;
    public static final int	PROB_GETTING_MSG_TYPE	= 4;
    public static final int	PROB_GETTING_STATUS	= 5;
    public static final int	REPLY_NOT_RECEIVED	= 6;
    public static final int	INVALID_OPERATION	= 7;
    public static final int	INVALID_PORT_VALUE	= 8;
    public static final int	BAD_HOSTNAME_SPECIFIED	= 9;
    public static final int	BAD_PORT_SPECIFIED	= 10;
    public static final int	INVALID_LOGIN		= 11;
    public static final int	SECURITY_PROB		= 12;
    public static final int	BUSY_WAIT_FOR_REPLY	= 13;
    public static final int	IGNORE_REPLY_IF_RCVD	= 14;
    public static final int	PROB_SETTING_SSL	= 15;
    public static final int	BAD_ADDR_SPECIFIED	= 16;

    private BrokerAdminConn ba;
    private Exception	linkedException;
    private String	brokerErrorStr,
			badValue,
			brokerHost,
			brokerPort,
			brokerAddr;
    private int		type,
			replyStatus = -1,
    			replyMsgType;

    private Message replyMsg = null;

    public BrokerAdminException(int type) {
	super();
	this.type = type;
    }

    public int getType()  {
	return (type);
    }

    public void setBrokerErrorStr(String errorStr)  {
	brokerErrorStr = errorStr;
    }
    public String getBrokerErrorStr()  {
	return (brokerErrorStr);
    }

    public void setReplyStatus(int replyStatus)  {
	this.replyStatus = replyStatus;
    }
    public int getReplyStatus()  {
	return (replyStatus);
    }

    public void setReplyMsgType(int replyMsgType)  {
	this.replyMsgType = replyMsgType;
    }
    public int getReplyMsgType()  {
	return (replyMsgType);
    }

    public void setReplyMsg(Message msg)  {
	this.replyMsg = msg;
    }

    public Message getReplyMsg()  {
	return replyMsg;
    }

    public void setBadValue(String badValue)  {
	this.badValue = badValue;
    }
    public String getBadValue()  {
	return (badValue);
    }

    public void setBrokerAdminConn(BrokerAdminConn ba)  {
	this.ba = ba;
    }
    public BrokerAdminConn getBrokerAdminConn()  {
	return (ba);
    }

    public void setLinkedException(Exception e)  {
	linkedException = e;
    }
    public Exception getLinkedException()  {
	return (linkedException);
    }

    public void setBrokerHost(String s)  {
	brokerHost = s;
    }
    public String getBrokerHost()  {
	return (brokerHost);
    }

    public void setBrokerPort(String s)  {
	brokerPort = s;
    }
    public String getBrokerPort()  {
	return (brokerPort);
    }

    public void setBrokerAddress(String s)  {
	brokerAddr = s;
    }
    public String getBrokerAddress()  {
	return (brokerAddr);
    }

}
