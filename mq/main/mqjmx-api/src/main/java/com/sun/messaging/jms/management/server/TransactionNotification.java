/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.management.server;

import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * Class containing information on transaction notifications.
 *
 * <P>
 * The MQ specific fields in this notification is TBD.
 */
public class TransactionNotification extends MQNotification {
    @Serial
    private static final long serialVersionUID = 1053479492195775559L;

    /**
     * A transaction was committed.
     */
    public static final String TRANSACTION_COMMIT = MQNotification.PREFIX + "transaction.commit";

    /**
     * A transaction has entered the prepared state.
     */
    public static final String TRANSACTION_PREPARE = MQNotification.PREFIX + "transaction.prepare";

    /**
     * A transaction was rolled back.
     */
    public static final String TRANSACTION_ROLLBACK = MQNotification.PREFIX + "transaction.rollback";

    @Getter
    @Setter
    private String transactionID;


    /**
     * Creates a TransactionNotification object.
     *
     * @param type The notification type.
     * @param source The notification source.
     * @param sequenceNumber The notification sequence number within the source object.
     */
    public TransactionNotification(String type, Object source, long sequenceNumber) {
        super(type, source, sequenceNumber);
    }
}
