/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.util.log.Logger;

public class CheckpointManager implements Runnable {
	
	TransactionLogManager transactionLogManager;
	private BlockingQueue<Checkpoint> checkpointQueue = new ArrayBlockingQueue<Checkpoint>(
			5);
	int numCheckpoints;
	private Thread runner;
	public static final Logger logger = Globals.getLogger();

	
	CheckpointManager(TransactionLogManager transactionLogManager)
	{
		this.transactionLogManager=transactionLogManager;
	}
	
	String getPrefix() {
		return "CheckpointManager: " + Thread.currentThread().getName();
	}
	

	public void run() {
		while (true) {
			try {
				checkpointQueue.take();
				transactionLogManager.doCheckpoint();
			} catch (Throwable e) {
				logger.logStack(Logger.ERROR,
						"exception when doing checkpoint", e);

			}
		}
	}
	
	public synchronized void enqueueCheckpoint() {

		if (runner == null) {
			if (Store.getDEBUG()) {
				String msg = getPrefix() + " starting checkpoint runner";
				logger.log(Logger.DEBUG, msg);
			}

			runner = new Thread(this, "Checkpoint runner");
			runner.setDaemon(true);
			runner.start();
		}

		Checkpoint checkpoint = new Checkpoint();
		int queueSize = checkpointQueue.size();
		if (queueSize > 0) {
			logger.log(Logger.ERROR, "enqueued checkpoint request "
					+ numCheckpoints + " when there are still " + queueSize
					+ " request(s) in process");

		}

		try {
			checkpointQueue.put(checkpoint);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		numCheckpoints++;
	}
	
	public static class Checkpoint {
	}

}
