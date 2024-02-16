/*
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

import static java.lang.System.Logger.Level.ERROR;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.ClusterTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;
import com.sun.messaging.jmq.util.log.Logger;

class ClusterTransaction2PPrepareEvent extends ClusterTransactionEvent {
    @Override
    int getSubType() {
        return Type2PPrepareEvent;
    }

    String getPrefix() {
        return "ClusterTransaction2PPrepareEvent: " + Thread.currentThread().getName();
    }

    @Override
    public byte[] writeToBytes() throws IOException {
        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + " writeToBytes");
        }
        // Log all msgs and acks for producing and consuming txn
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(BaseTransaction.CLUSTER_TRANSACTION_TYPE);
        dos.writeByte(Type2PPrepareEvent);

        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + "post write types  size= " + dos.size());
        }

        clusterTransaction.getTransactionDetails().writeContent(dos);

        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + "post write content  size= " + dos.size());
        }
        clusterTransaction.getTransactionWork().writeWork(dos);

        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + "post write work  size= " + dos.size());
        }

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream(1024);
        ObjectOutputStream oos = new ObjectOutputStream(baos2);

        oos.writeObject(clusterTransaction.getTransactionState());
        oos.writeObject(clusterTransaction.getTransactionBrokers());
        oos.close();

        byte[] data = baos2.toByteArray();
        int length = data.length;
        dos.writeInt(length);
        dos.write(data);

        baos2.close();

        dos.close();
        baos.close();

        byte[] data2 = baos.toByteArray();
        return data2;

    }

    @Override
    public void readFromBytes(byte[] data) throws IOException, BrokerException {
        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + "readFromBytes");
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        clusterTransaction = new ClusterTransaction();
        dis.skip(2);

        clusterTransaction.getTransactionDetails().readContent(dis);
        if (Store.getDEBUG()) {
            Globals.getLogger().log(Logger.DEBUG, getPrefix() + "read details " + clusterTransaction.getTransactionDetails());
        }
        TransactionWork work = new TransactionWork();
        work.readWork(dis);
        clusterTransaction.setTransactionWork(work);

        int objectBodySize = dis.readInt();

        byte[] objectBody = new byte[objectBodySize];
        dis.read(objectBody);

        ByteArrayInputStream bais2 = new ByteArrayInputStream(objectBody);
        ObjectInputStream ois = new FilteringObjectInputStream(bais2);

        try {

            clusterTransaction.setTransactionState((TransactionState) ois.readObject());
            clusterTransaction.setTransactionBrokers((TransactionBroker[]) ois.readObject());
        } catch (ClassNotFoundException e) {
            System.getLogger(this.getClass().getName()).log(ERROR, e.getMessage(), e);
        }
        ois.close();
        bais2.close();

        dis.close();
        bais.close();
    }

}

