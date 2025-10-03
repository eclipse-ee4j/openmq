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

import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.LocalTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

class LocalTransaction2PPrepareEvent extends LocalTransactionEvent {

    @Override
    int getSubType() {
        return Type2PPrepareEvent;
    }

    @Override
    byte[] writeToBytes() throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeByte(BaseTransaction.LOCAL_TRANSACTION_TYPE);
        dos.writeByte(Type2PPrepareEvent);
        localTransaction.getTransactionDetails().writeContent(dos);
        localTransaction.getTransactionWork().writeWork(dos);

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream(1024);
        ObjectOutputStream oos = new ObjectOutputStream(baos2);

        oos.writeObject(localTransaction.getTransactionState());
        oos.close();

        byte[] data = baos2.toByteArray();
        int length = data.length;
        dos.writeInt(length);
        dos.write(data);

        baos2.close();

        byte[] data2 = bos.toByteArray();
        dos.close();
        bos.close();
        return data2;
    }

    @Override
    void readFromBytes(byte[] data) throws IOException, BrokerException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        localTransaction = new LocalTransaction();
        dis.skip(2);
        localTransaction.getTransactionDetails().readContent(dis);
        TransactionWork work = new TransactionWork();
        work.readWork(dis);
        localTransaction.setTransactionWork(work);

        // need to write transaction info here
        int objectBodySize = dis.readInt();

        byte[] objectBody = new byte[objectBodySize];
        dis.read(objectBody);

        ByteArrayInputStream bais2 = new ByteArrayInputStream(objectBody);
        ObjectInputStream ois = new FilteringObjectInputStream(bais2);

        try {

            TransactionState ts = (TransactionState) ois.readObject();

            localTransaction.setTransactionState(ts);

        } catch (ClassNotFoundException e) {
            System.getLogger(this.getClass().getName()).log(ERROR, e.getMessage(), e);
        }
        ois.close();
        bais2.close();

        dis.close();
        bais.close();
    }
}

