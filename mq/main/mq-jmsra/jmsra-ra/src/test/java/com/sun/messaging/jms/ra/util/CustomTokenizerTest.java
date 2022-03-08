/*
 * Copyright (c) 2022 Contributors to Eclipse Foundation
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

package com.sun.messaging.jms.ra.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import jakarta.resource.spi.InvalidPropertyException;

class CustomTokenizerTest {

    @Test
    void testConvertedFromMainMethod() throws InvalidPropertyException {
        String str = "imqConsumerFlowThreshold=78,imqAddressList=tcp://localhost:7676\\,tcp://localhost:7677";
        String delimiter = ",";
        var tokens = new ArrayList<String>();

        CustomTokenizer tokenList = new CustomTokenizer(str, delimiter);
        while (tokenList.hasMoreTokens()) {
            tokens.add(tokenList.nextTokenWithoutEscapeAndQuoteChars());
        }

        assertThat(tokens).containsExactly("imqConsumerFlowThreshold=78", "imqAddressList=tcp://localhost:7676,tcp://localhost:7677");
    }
}
