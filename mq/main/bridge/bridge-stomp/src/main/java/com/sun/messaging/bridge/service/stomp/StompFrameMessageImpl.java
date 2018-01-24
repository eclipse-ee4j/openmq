/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.BufferOutputStream;
import com.sun.messaging.jmq.util.LoggerWrapper;
import com.sun.messaging.bridge.api.ByteBufferWrapper;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompFrameMessageFactory;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 * @author amyk 
 */
public class StompFrameMessageImpl extends StompFrameMessage {

    private static final StompBridgeResources sbr = StompServer.getStompBridgeResources();
    private static final StompFrameMessageFactory factory = new StompFrameMessageFactoryImpl();

    static class StompFrameMessageFactoryImpl implements StompFrameMessageFactory { 
        public StompFrameMessage newStompFrameMessage(Command cmd, LoggerWrapper logger) {
            return new StompFrameMessageImpl(cmd, logger);
        }
    }

    protected static StompFrameMessageFactory getFactory() {
        return factory;
    }

    protected StompFrameMessageImpl(Command cmd, LoggerWrapper logger) {
        super(cmd, logger);
    } 

    public static StompFrameMessageImpl parseCommand(
        Buffer buf, LoggerWrapper logger) throws Exception {
        return (StompFrameMessageImpl)StompFrameMessage.parseCommand(
                new ByteBufferWrapperImpl(buf), logger, factory);
    }
         
    public void parseHeader(Buffer buf) throws Exception {
        super.parseHeader(new ByteBufferWrapperImpl(buf));
    }

    public void readBody(Buffer buf) throws Exception {
        super.readBody(new ByteBufferWrapperImpl(buf));
    } 

    public void readNULL(Buffer buf) throws Exception {
        super.readNULL(new ByteBufferWrapperImpl(buf));
    }

    @Override
    protected OutputStream newBufferOutputStream(Object obj) throws IOException {
        MemoryManager mm = (MemoryManager)obj;
        return new BufferOutputStream(mm);
    }

    @Override
    protected ByteBufferWrapper getBuffer(OutputStream os) throws IOException {
        BufferOutputStream bos = (BufferOutputStream)os;
        return new ByteBufferWrapperImpl(bos.getBuffer());
    }

    @Override
    protected String getKStringX_CANNOT_PARSE_BODY_TO_TEXT(String cmd, String emsg) {
        return sbr.getKString(sbr.X_CANNOT_PARSE_BODY_TO_TEXT, cmd, emsg);
    }
    @Override
    protected String getKStringX_HEADER_NOT_SPECIFIED_FOR(String headerName, String cmd) {
        return sbr.getKString(sbr.X_HEADER_NOT_SPECIFIED_FOR, headerName, cmd);
    }
    @Override
    protected String getKStringX_INVALID_HEADER_VALUE(String headerValue, String cmd) {
        return sbr.getKString(sbr.X_INVALID_HEADER_VALUE, headerValue, cmd);
    }
    @Override
    protected String getKStringX_INVALID_HEADER(String headerName) {
        return sbr.getKString(sbr.X_INVALID_HEADER, headerName);
    }
    @Override
    protected String getKStringX_MAX_HEADERS_EXCEEDED(int maxHeaders) {
        return sbr.getKString(sbr.X_MAX_HEADERS_EXCEEDED, maxHeaders);
    }
    @Override
    protected String getKStringX_EXCEPTION_PARSE_HEADER(String headerName, String emsg) {
        return sbr.getKString(sbr.X_EXCEPTION_PARSE_HEADER, headerName, emsg);
    }
    @Override
    protected String getKStringX_NO_NULL_TERMINATOR(String contentlen) {
        return sbr.getKString(sbr.X_NO_NULL_TERMINATOR, contentlen);
    }
    @Override
    protected String getKStringX_UNKNOWN_STOMP_CMD(String cmd) {
        return sbr.getKString(sbr.X_UNKNOWN_STOMP_CMD, cmd);
    }
    @Override
    protected String getKStringX_MAX_LINELEN_EXCEEDED(int maxbytes) {
        return sbr.getKString(sbr.X_MAX_LINELEN_EXCEEDED, Integer.valueOf(maxbytes));
    }

    private static class ByteBufferWrapperImpl implements ByteBufferWrapper<Buffer> {
        private Buffer buf =  null;

        public ByteBufferWrapperImpl(Buffer buf) {
            this.buf = buf;
        }

        public Buffer getWrapped() {
            return buf;
        }

        public int position() {
            return buf.position();
        }

        public ByteBufferWrapper position(int newPosition) {
            buf.position(newPosition);
            return this;
        }

        public boolean hasRemaining() {
            return buf.hasRemaining();
        }

        public int remaining() {
            return buf.remaining();
        }

        public ByteBufferWrapper flip() {
            buf.flip();
            return this;
        }

        public byte get() {
            return buf.get();
        }
    }
}


