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

import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.RemoteTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

class RemoteTransaction2PPrepareEvent extends RemoteTransactionEvent {
    @Override
    int getSubType() {
        return Type2PPrepareEvent;
    }

    @Override
    public byte[] writeToBytes() throws IOException {
        // Log all msgs and acks for producing and consuming txn
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(BaseTransaction.REMOTE_TRANSACTION_TYPE);
        dos.writeByte(Type2PPrepareEvent);

        remoteTransaction.getTransactionDetails().writeContent(dos);

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream(1024);
        ObjectOutputStream oos = new ObjectOutputStream(baos2);

        oos.writeObject(remoteTransaction.getTransactionState());
        oos.writeObject(remoteTransaction.getTxnHomeBroker());
        oos.writeObject(remoteTransaction.getTxnAcks());
        oos.writeObject(remoteTransaction.getDestIds());
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
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        remoteTransaction = new RemoteTransaction();
        dis.skip(2);

        remoteTransaction.getTransactionDetails().readContent(dis);

        int objectBodySize = dis.readInt();

        byte[] objectBody = new byte[objectBodySize];
        dis.read(objectBody);

        ByteArrayInputStream bais2 = new ByteArrayInputStream(objectBody);
        ObjectInputStream ois = new FilteringObjectInputStream(bais2);

        try {

            remoteTransaction.setTransactionState((TransactionState) ois.readObject());
            remoteTransaction.setTxnHomeBroker((BrokerAddress) ois.readObject());
            remoteTransaction.setTxnAcks((TransactionAcknowledgement[]) ois.readObject());
            remoteTransaction.setDestIds((DestinationUID[]) ois.readObject());
        } catch (ClassNotFoundException e) {
            System.getLogger(this.getClass().getName()).log(ERROR, e.getMessage(), e);
        }
        ois.close();
        bais2.close();

        dis.close();
        bais.close();
    }

}

