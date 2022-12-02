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

package com.sun.messaging.ums.service;

import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;

class SecuredSidTest {

    @Test
    void testInsteadOfMain() throws JMSException {
        SecuredSid ssid = new SecuredSid();

        for (int i = 0; i < 1; i++) {
            String sid = ssid.nextSid();
            System.out.println("**** sid = " + sid);

            // sid = sid + 1;
            // sid = 1 + sid;

            // int index = sid.indexOf('-');

            // get sequence
            // String sequence = sid.substring(0, index);

            // index ++;

            // get signature string - base 64
            // String sigstr = sid.substring(index);

            // sigstr = 1+sigstr;

            // String badsid = sequence + "-" + sigstr;

            // ssid.verifySid(sid);
            // ssid.verifySid(badsid);

            // Thread.sleep (100);
            // System.out.println ("**** sid verified, sid= " + sid);
        }
    }
}
