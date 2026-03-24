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

package com.sun.messaging.jmq.util.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.sun.messaging.jmq.util.DestType;

class DestinationInfo_Serializable_Test {
    @Test
    void serializabilityWithFullType() throws IOException, ClassNotFoundException {
        var destinationInfo = makeDestinationInfo("Test", true, DestType.DEST_TYPE_QUEUE, DestType.DEST_INTERNAL | DestType.DEST_TYPE_QUEUE);

        try (var baos = new ByteArrayOutputStream();
                var output = new ObjectOutputStream(baos)) {
            output.writeObject(destinationInfo);
            try (var bais = new ByteArrayInputStream(baos.toByteArray());
                    var input = new ObjectInputStream(bais)) {
                var readObject = input.readObject();
                assertThat(readObject).isNotNull();
                assertThat(readObject).isInstanceOf(DestinationInfo.class);
                var readInfo = (DestinationInfo)readObject;
                assertThat(readInfo.name).isEqualTo("Test");
                assertThat(readInfo.autocreated).isTrue();
                assertThat(readInfo.type).isEqualTo(DestType.DEST_TYPE_QUEUE);
                assertThat(readInfo.fulltype).isEqualTo(DestType.DEST_INTERNAL | DestType.DEST_TYPE_QUEUE);
            }
        }
    }

    @Test
    void serializabilityWithoutFullType() throws IOException, ClassNotFoundException {
        var destinationInfo = makeDestinationInfo("Test", true, DestType.DEST_TYPE_QUEUE, 0);

        try (var baos = new ByteArrayOutputStream();
                var output = new ObjectOutputStream(baos)) {
            output.writeObject(destinationInfo);
            try (var bais = new ByteArrayInputStream(baos.toByteArray());
                    var input = new ObjectInputStream(bais)) {
                var readObject = input.readObject();
                assertThat(readObject).isNotNull();
                assertThat(readObject).isInstanceOf(DestinationInfo.class);
                var readInfo = (DestinationInfo)readObject;
                assertThat(readInfo.name).isEqualTo("Test");
                assertThat(readInfo.autocreated).isTrue();
                assertThat(readInfo.type).isEqualTo(DestType.DEST_TYPE_QUEUE);
                assertThat(readInfo.fulltype).isEqualTo(DestType.DEST_TYPE_QUEUE);
            }
        }
    }

    static DestinationInfo makeDestinationInfo(String name,
                                               boolean auto,
                                               int type,
                                               int fulltype) {
        var destinationInfo = new DestinationInfo();
        destinationInfo.name = name;
        destinationInfo.autocreated = auto;
        destinationInfo.type = type;
        destinationInfo.fulltype = fulltype;
        return destinationInfo;
    }
}
