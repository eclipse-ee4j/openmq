/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.bridge.service.stomp;

import java.util.logging.Level;
import jakarta.jms.*;
import com.sun.messaging.bridge.api.StompSubscriber;

class TransactedSubscriber implements StompSubscriber, MessageListener {

    private String _subid = null;
    private StompTransactedSession _parent = null;
    private MessageConsumer _subscriber = null;
    private String _duraName = null;

    TransactedSubscriber(String subid, MessageConsumer sub, String duraname, StompTransactedSession parent) {
        _subid = subid;
        _subscriber = sub;
        _parent = parent;
        _duraName = duraname;

    }

    @Override
    public void startDelivery() throws Exception {
        _subscriber.setMessageListener(this);
    }

    public String getDuraName() {
        return _duraName;
    }

    public void startMessageDelivery() throws Exception {
        _subscriber.setMessageListener(this);
    }

    @Override
    public void onMessage(Message msg) {

        try {
            _parent.logger.log(Level.FINE, "onMessage message " + msg.getJMSMessageID() + " for STOMP subscriber " + _subid);
            _parent.enqueue(_subid, msg);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    public void close() throws Exception {
        _subscriber.close();
    }
}
