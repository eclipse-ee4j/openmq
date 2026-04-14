/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.persist.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

class HABrokerInfo_toString_Test {
    @SuppressWarnings("JavaUtilDate")
    @Test
    void testToString() {
        var dateInCurrentLocale = "" + new Date(5);
        assertThat(HABrokerInfo.toString("abcd", "https://localhost", 2, 3, 4, 5, "efgh"))
            .isEqualTo("(brokerID=abcd, URL=https://localhost, version=2, state=3 [BrokerState[QUIESCE_COMPLETED]], sessionID=4, heartbeatTS=5 [" + dateInCurrentLocale + "], takeoverBrokerID=efgh)");
    }

    @Test
    void testToStringHeartBeat0() {
        assertThat(HABrokerInfo.toString("abcd", "https://localhost", 2, 3, 4, 0, "efgh"))
            .isEqualTo("(brokerID=abcd, URL=https://localhost, version=2, state=3 [BrokerState[QUIESCE_COMPLETED]], sessionID=4, heartbeatTS=0, takeoverBrokerID=efgh)");
    }
}
