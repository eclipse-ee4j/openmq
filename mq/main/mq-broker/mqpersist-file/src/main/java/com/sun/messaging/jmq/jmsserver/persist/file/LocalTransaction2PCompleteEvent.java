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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.LocalTransaction;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

class LocalTransaction2PCompleteEvent extends LocalTransactionEvent {

    @Override
    int getSubType() {
        return Type2PCompleteEvent;
    }

    @Override
    byte[] writeToBytes() throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeByte(BaseTransaction.LOCAL_TRANSACTION_TYPE);
        dos.writeByte(Type2PCompleteEvent);
        localTransaction.getTransactionDetails().writeContent(dos);

        byte[] data = bos.toByteArray();
        dos.close();
        bos.close();
        return data;
    }

    @Override
    void readFromBytes(byte[] data) throws IOException, BrokerException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        localTransaction = new LocalTransaction();
        dis.skip(2);
        localTransaction.getTransactionDetails().readContent(dis);

        dis.close();
        bais.close();
    }
}
