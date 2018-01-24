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
 * @(#)DedicatedService.java	1.21 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.dedicated;

import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import java.nio.channels.*;
import java.io.IOException;
import com.sun.messaging.jmq.util.GoodbyeReason;
import java.util.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.pool.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.net.ProtocolStreams;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.util.log.Logger;


public class DedicatedService extends IMQIPService
{

    public DedicatedService(String name, Protocol protocol,
        int type, PacketRouter router, int min, int max) {
        super(name, protocol, type, router, min, max);
    }

    public RunnableFactory getRunnableFactory() {
        return new OperationRunnableFactory(true /* blocking */);
    }

    public Hashtable getDebugState()
    {
//XXX
        return super.getDebugState();
    }


    public void acceptConnection(IMQIPConnection con)
        throws IOException, BrokerException
    {
        // Get a thread for read

        OperationRunnable read = (OperationRunnable)
                   pool.getAvailRunnable(false);
        OperationRunnable write = (OperationRunnable)
                   pool.getAvailRunnable(false);
        if (read == null || write == null) {
            if (read != null) {
                read.release();
            }
            if (write != null) {
                write.release();
            }

            String args[] = {this.toString(),
                       String.valueOf(pool.getAssignedCnt()),
                       String.valueOf(pool.getMaximum())};
            logger.log(Logger.WARNING, 
                BrokerResources.E_NOT_ENOUGH_THREADS,
                args);

            pool.debug();
            con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, 
                 Globals.getBrokerResources().getKString(
                 BrokerResources.E_NOT_ENOUGH_THREADS, args));
            throw new BrokerException( 
                Globals.getBrokerResources().getKString(
                    BrokerResources.E_NOT_ENOUGH_THREADS,
                    args),
                BrokerResources.E_NOT_ENOUGH_THREADS,
                (Throwable) null,
                Status.NOT_ALLOWED);

        }
        
        // CR 6798565: start writer thread before reader thread
        startWriterThread(con,read,write);
        startReaderThread(con,read,write);
        
    }
    
    private void startReaderThread(IMQIPConnection con, OperationRunnable read, OperationRunnable write) throws MissingResourceException, BrokerException{

//XXX - workaround to prevent code from breaking when bug 4616064 occurs and
//      provide better output
// XXX

// XXX-start Workaround
        // XXX- workaround for bug (will provide info to track it down)
        boolean assigned = false; 
        while (!assigned) {
            try {
// XXX-end Workaround
                read.assignOperation(con, SelectionKey.OP_READ, 
                                   OperationRunnable.FOREVER);
// XXX-start Workaround
                assigned = true;
            } catch (IllegalAccessException ex) {
                logger.logStack(Logger.ERROR, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                   "assigning read for " + con + " to available thread " 
                   + read, ex);
                pool.debug();
                read = (OperationRunnable)pool.getAvailRunnable(false);
                logger.log(Logger.DEBUG,
                        "Recovering: Assigning new read for " 
                        + con + " to available thread " + read);
                if (read == null) { // bummer
                    String args[] = {this.toString(),
                               String.valueOf(pool.getAssignedCnt()),
                               String.valueOf(pool.getMaximum())};
                    if (write != null) {
                        write.destroy();
                    }
                    logger.log(Logger.ERROR, 
                         BrokerResources.E_NOT_ENOUGH_THREADS, args);
                    pool.debug();
                    con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, 
                         Globals.getBrokerResources().getKString(
                         BrokerResources.E_NOT_ENOUGH_THREADS));
                    throw new BrokerException( 
                        Globals.getBrokerResources().getKString(
                            BrokerResources.E_NOT_ENOUGH_THREADS,
                            args),
                        BrokerResources.E_NOT_ENOUGH_THREADS,
                        (Throwable) null,
                        Status.ERROR);
                }
            }
        }
        
    }
        
    
    private void startWriterThread(IMQIPConnection con, OperationRunnable read, OperationRunnable write) throws MissingResourceException, BrokerException{
    
        boolean assigned = false;
        while (!assigned) {
            try {
//XXX-end Workaround
                write.assignOperation(con, SelectionKey.OP_WRITE, 
                                OperationRunnable.FOREVER);
//XXX-start Workaround
                assigned = true;
//XXX-end Workaround
            } catch (IllegalAccessException ex) {
                logger.logStack(Logger.ERROR, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                   "assigning write for " + con 
                   + " to available thread " + read, ex);
                pool.debug();
                write = (OperationRunnable)pool.getAvailRunnable(false);
                logger.log(Logger.DEBUG,
                     "Recovering: Assigning new write for " 
                     + con + " to available thread " + read);
                if (write == null) { // bummer
                    String args[] = {this.toString(),
                               String.valueOf(pool.getAssignedCnt()),
                               String.valueOf(pool.getMaximum())};
                    if (read != null) { //reader hasn't assigned, so just release  
                        read.release();
                    }
                    logger.log(Logger.ERROR,
                         BrokerResources.E_NOT_ENOUGH_THREADS, args);
                    pool.debug();
                    con.destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR, 
                         Globals.getBrokerResources().getKString(
                         BrokerResources.E_NOT_ENOUGH_THREADS));
                    throw new BrokerException( 
                        Globals.getBrokerResources().getKString(
                            BrokerResources.E_NOT_ENOUGH_THREADS,
                            args),
                        BrokerResources.E_NOT_ENOUGH_THREADS,
                        (Throwable) null,
                        Status.ERROR);
                }
            }
        }
//XXX-end Workaround

    }


}

