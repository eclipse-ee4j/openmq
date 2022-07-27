/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2022 Contributors to the Eclipse Foundation
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

import com.sun.messaging.bridge.api.ByteBufferWrapper;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompFrameMessageFactory;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.ByteBufferManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class StompFrameMessageImplTest {

    private static final java.util.logging.Logger newLogger = Logger.getAnonymousLogger();
    private static final ByteBufferManager mm = new ByteBufferManager();
    private static final LoggerWrapperImpl loggerWrapper = new LoggerWrapperImpl(newLogger);
    private static final StompFrameMessageFactory factory = StompFrameMessageImpl.getFactory();

    @Test
    public void marshal_CONNECTED_withoutEscaping_Stomp12() throws IOException {

        StompFrameMessage m = factory.newStompFrameMessage(StompFrameMessage.Command.CONNECTED, loggerWrapper);
        m.getHeaders().put("key with \\ backslash", "value with \\ backslash");

        Buffer buf = (Buffer) m.marshall(mm, StompFrameMessage.STOMP_PROTOCOL_VERSION_12).getWrapped();
        byte[] bb = new byte[buf.remaining()];
        buf.get(bb);
        String frame = new String(bb, StandardCharsets.UTF_8);

        assertThat(frame).isEqualTo("CONNECTED\n" +
                "key with \\ backslash:value with \\ backslash\n" +
                "\n\u0000\n");
    }

    @Test
    public void marshal_MESSAGE_withEscaping_Stomp12() throws IOException {

        StompFrameMessage m = factory.newStompFrameMessage(StompFrameMessage.Command.MESSAGE, loggerWrapper);
        m.getHeaders().put("key with : colon", "value with : colon");
        m.getHeaders().put("key with \\ backslash", "value with \\ backslash");

        Buffer buf = (Buffer) m.marshall(mm, StompFrameMessage.STOMP_PROTOCOL_VERSION_12).getWrapped();
        byte[] bb = new byte[buf.remaining()];
        buf.get(bb);
        String frame = new String(bb, StandardCharsets.UTF_8);

        assertThat(frame).isEqualTo("MESSAGE\n" +
                "key with \\c colon:value with \\c colon\n" +
                "key with \\\\ backslash:value with \\\\ backslash\n" +
                "\n\u0000\n");
    }

    @Test
    public void parseHeader_withoutUnescaping_Stomp10() throws Exception {

        String headerLine = "key without colon:value with : colon\n";
        ByteBuffer bb = ByteBuffer.wrap(headerLine.getBytes(StandardCharsets.UTF_8));
        Buffer buf = new org.glassfish.grizzly.memory.ByteBufferWrapper(bb);
        StompFrameMessage m = factory.newStompFrameMessage(StompFrameMessage.Command.UNKNOWN, loggerWrapper);

        m.parseHeader(new ByteBufferWrapperImpl(buf), StompFrameMessage.STOMP_PROTOCOL_VERSION_12);

        assertThat(m.getHeader("key without colon")).isEqualTo("value with : colon");
    }

    @Test
    public void parseHeader_withUnescaping_Stomp12() throws Exception {

        String headerLine = "key with \\c colon:value with \\c colon\n";
        ByteBuffer bb = ByteBuffer.wrap(headerLine.getBytes(StandardCharsets.UTF_8));
        Buffer buf = new org.glassfish.grizzly.memory.ByteBufferWrapper(bb);
        StompFrameMessage m = factory.newStompFrameMessage(StompFrameMessage.Command.UNKNOWN, loggerWrapper);

        m.parseHeader(new ByteBufferWrapperImpl(buf), StompFrameMessage.STOMP_PROTOCOL_VERSION_12);

        assertThat(m.getHeader("key with : colon")).isEqualTo("value with : colon");
    }

    private static class ByteBufferWrapperImpl implements ByteBufferWrapper<Buffer> {
        private final Buffer buf;

        ByteBufferWrapperImpl(Buffer buf) {
            this.buf = buf;
        }

        @Override
        public Buffer getWrapped() {
            return buf;
        }

        @Override
        public int position() {
            return buf.position();
        }

        @Override
        public ByteBufferWrapper position(int newPosition) {
            buf.position(newPosition);
            return this;
        }

        @Override
        public boolean hasRemaining() {
            return buf.hasRemaining();
        }

        @Override
        public int remaining() {
            return buf.remaining();
        }

        @Override
        public ByteBufferWrapper flip() {
            buf.flip();
            return this;
        }

        @Override
        public byte get() {
            return buf.get();
        }
    }

}