/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.data;

import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class TransactionWorkFactory {
	
	
	
	
	static public BaseTransaction readFromBytes(byte[] data) throws IOException,BrokerException
	{
		
		BaseTransaction result = null;
		byte type = data[0];
		result = create(type);
		result.readFromBytes(data);						
		return result;		
	}
	
	static BaseTransaction create(byte type) {
		BaseTransaction result = null;
		switch (type) {
                default:
		case BaseTransaction.LOCAL_TRANSACTION_TYPE:
			result = new LocalTransaction();
			break;
		case BaseTransaction.CLUSTER_TRANSACTION_TYPE:
			result = new ClusterTransaction();
			break;
		case BaseTransaction.REMOTE_TRANSACTION_TYPE:
			result = new RemoteTransaction();
			break;
		}
		return result;
	}

}
