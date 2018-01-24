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

package com.sun.messaging.bridge.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import com.sun.messaging.jmq.util.LoggerWrapper;

/**
 * @author amyk 
 */
public abstract class StompFrameMessage {

    private LoggerWrapper logger = null;

    public static final String STOMP_PROTOCOL_VERSION_10 = "1.0";
    public static final String STOMP_PROTOCOL_VERSION_11 = "1.1";
    public static final String STOMP_PROTOCOL_VERSION_12 = "1.2";

    public static final String HEADER_SEPERATOR = ":";
    private static final String NEWLINESTR = "\n";

    private static final byte NEWLINE_BYTE = '\n';
    private static final byte NULL_BYTE = '\0';
    private static final byte[] END_OF_FRAME = new byte[]{0, '\n'};

    public static final int MIN_COMMAND_LEN = 3;
    protected static final int MAX_COMMAND_LEN = 1024;
    protected static final int MAX_HEADER_LEN = 1024 * 10;
    private static final int MAX_HEADERS = 1000;

    public enum Command {
        STOMP/*STOMP spec:1.1*/, CONNECT, DISCONNECT, 

        SEND, 
        SUBSCRIBE, UNSUBSCRIBE, 
        BEGIN, COMMIT, ABORT, 

        ACK, NACK/*STOMP spec:1.1 not implemented*/, 

        UNKNOWN,

        /* responses */
        CONNECTED, MESSAGE, RECEIPT, ERROR
    };	

    public enum CommonHeader { 
        ;
        public final static String RECEIPT = "receipt";
        public final static String TRANSACTION = "transaction";
        public final static String CONTENTLENGTH = "content-length";

        //STOMP spec:1.1 for SEND, MESSAGE, ERROR, not implemented
        public final static String CONTENTTYPE = "content-type"; 
    }   

    public enum ResponseCommonHeader {
        ;
        public final static String RECEIPTID = "receipt-id";
    }

    public enum SendHeader {
        ;
        public final static String DESTINATION = "destination";
        public final static String EXPIRES = "expires";
        public final static String PRIORITY = "priority";
        public final static String TYPE = "type";
        public final static String PERSISTENT = "persistent";
        public final static String REPLYTO = "reply-to";
        public final static String CORRELATIONID = "correlation-id";
    }

    public enum MessageHeader {
        ;
        public final static String DESTINATION = "destination";
        public final static String MESSAGEID = "message-id";
        public final static String TIMESTAMP = "timestamp";
        public final static String EXPIRES = "expires";
        public final static String PRORITY = "priority";
        public final static String REDELIVERED = "redelivered";
        public final static String TYPE = "type";
        public final static String REPLYTO = "reply-to";
        public final static String CORRELATIONID = "correlation-id";
        public final static String SUBSCRIPTION = "subscription";

        //STOMP spec:1.2 for client or client-individual ack
        public final static String ACK = "ack";
    }

    public enum SubscribeHeader {
        ;
        public final static String DESTINATION = "destination";
        public final static String SELECTOR = "selector";
        public final static String ACK = "ack";
        public final static String ID = "id";
        public final static String DURASUBNAME = "durable-subscriber-name";
        public final static String NOLOCAL = "no-local";
    }

    public enum AckMode {
        ;
        public final static String AUTO = "auto";
        public final static String CLIENT = "client";

        //STOMP spec:1.1 
        public final static String CLIENT_INDIVIDUAL = "client-individual";
    }

    public enum UnsubscribeHeader {
        ;
        //STOMP spec:1.0 optional, obsoleted 1.1
        public final static String DESTINATION = "destination";

        //STOMP spec:1.1 required; 1.0 optional
        public final static String ID = "id";
    }

    public enum ConnectHeader {
        ;
        public final static String LOGIN = "login";
        public final static String PASSCODE = "passcode";
        public final static String CLIENTID = "client-id";

        //STOMP spec:1.1
        public final static String ACCEPT_VERSION = "accept-version";

        //STOMP spec:1.1 not implemented
        public final static String HEART_BEAT = "heart-beat";
    }

    public enum ErrorHeader {
        ;
        public final static String MESSAGE = "message";

        //optional, implemented
        public final static String CONTENT_LENGTH = "content-length";

        //STOMP spec:1.1, not implemented
        public final static String CONTENT_TYPE = "content-type";

        //STOMP spec:1.1, optional, not implemented
        public final static String RECEIPTID = "receipt-id";
    }

    public enum ConnectedHeader {
        ;
        public final static String SESSION = "session";

        //STOMP spec:1.1
        public final static String VERSION = "version";

        //STOMP spec:1.1
        public final static String SERVER = "server";

        //STOMP spec:1.1 not implemented
        public final static String HEART_BEAT = "heart-beat";
    }

    public enum AckHeader {
        ;
        //STOMP spec:1.2 obsolete
        public final static String MESSAGEID = "message-id";

        //STOMP spec:1.1, obsoleted 1.2
        public final static String SUBSCRIPTION = "subscription";

        //for transacted ack
        public final static String TRANSACTION = "transaction";

        //STOMP spec:1.2, correlates to ack header in MESSAGE 
        public final static String ID = "id";
    }

    public static enum ParseStage { COMMAND, HEADER, BODY, NULL, DONE };

    private Command _command = Command.UNKNOWN;

    private ArrayList<String> _requiredHeaders = new ArrayList<String>();
    private LinkedHashMap<String, String> _headers = new LinkedHashMap<String, String>();

    private Integer _contentLength = null; 

    protected ParseStage _parseStage = ParseStage.COMMAND;

    private ByteArrayOutputStream _bao = null;
    private byte[] _body = null;
    private Exception _parseException = null;

    private boolean _fatalERROR = false;
    private boolean isTextMessage = false;

    protected StompFrameMessage(Command cmd, LoggerWrapper logger) {
        this.logger = logger;

        _command = cmd;

        switch (cmd) {
            case CONNECT:
            case STOMP:
                _requiredHeaders.add((ConnectHeader.LOGIN));
                _requiredHeaders.add((ConnectHeader.PASSCODE));
                break;
            case SEND:
                _requiredHeaders.add((SendHeader.DESTINATION));
                break;
            default:
        }
    }

    public boolean isTextMessage() {
        return isTextMessage;
    }
    public void setTextMessageFlag() {
        isTextMessage = true;
    }
    /**
     * to be used only for ERROR frame
     */
    public void setFatalERROR() {
        _fatalERROR = true;
    }

    public boolean isFatalERROR() {
        return _fatalERROR;
    }

    public Exception getParseException() {
        return _parseException;
    }

    public Command getCommand() {
        return _command;
    }
    
    public void addHeader(String key, String val) {
        _headers.put(key, val);
    }

    public LinkedHashMap<String, String> getHeaders() {
         return _headers;
    }

    public String getHeader(String key) {
         return _headers.get(key);
    }

    public byte[] getBody() {
        if (_body != null) return _body;
        if (_bao == null) return (new byte[]{});
        _body = _bao.toByteArray();
        return _body;
    }

    public String getBodyText() throws StompFrameParseException {
        String text = "";
         
        if (_body != null)  {
            try {
                return new String(_body, "UTF-8");
            } catch (Exception e) {
                throw new StompFrameParseException(e.getMessage(), e);
            }
        }

        if (_bao == null) return text;

        _body = _bao.toByteArray();

        try {
            text =  new String(_body, "UTF-8");
            return text;
        } catch (Exception e) {
            throw new StompFrameParseException(
            getKStringX_CANNOT_PARSE_BODY_TO_TEXT(getCommand().toString(), e.getMessage()));
        }
    }

    
    private void writeByteToBody(byte b) throws Exception {
        if (_bao == null) {
            if (getContentLength() != -1) {
                _bao = new ByteArrayOutputStream(getContentLength());
            } else {
                _bao = new ByteArrayOutputStream();
            }
        }
        _bao.write(b);
    }

    public void setBody(byte[] data) {
        _body = data;
    }

    protected void writeExceptionToBody(Throwable t) throws Exception {
        if (t == null) {
            return;
        }
        if (_bao == null) {
            _bao = new ByteArrayOutputStream();
        }
        t.printStackTrace(new PrintStream(_bao, true, "UTF-8"));
        addHeader(CommonHeader.CONTENTLENGTH, String.valueOf(getBodySize()));
    }

    private int getBodySize() {
        if (_bao == null) return 0;
        return _bao.size();
    }


    protected void setNextParseStage(ParseStage s) {
        _parseStage = s;
        if (s == ParseStage.BODY) {
            for (String key: _requiredHeaders) {
                if (_headers.get(key) == null) {
                    if (_parseException == null) {
                     _parseException = new StompFrameParseException(
                         getKStringX_HEADER_NOT_SPECIFIED_FOR(key, getCommand().toString()));
                     logger.logSevere(_parseException.getMessage(), null);
                    }
                }
            }
        }
        if (s == ParseStage.DONE) {
            try {
                if (_bao != null) _bao.close();
            } catch (Exception e) {
                logger.logWarn(
                "Exception in closing ByteArrayOutputStream:"+e.getMessage(), null);
            }
        }
    }

    public ParseStage getNextParseStage() {
        return _parseStage;
    }

    public int getContentLength() {
        if (_contentLength != null) return _contentLength.intValue(); 

        String val = _headers.get(CommonHeader.CONTENTLENGTH);
        if (val == null) return -1;
        int len = -1;
        try {
            len =Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            if (_parseException == null) {
                _parseException = new StompFrameParseException(
                    getKStringX_INVALID_HEADER_VALUE(val, CommonHeader.CONTENTLENGTH));
                len = -1;
                logger.logSevere(_parseException.getMessage(), null);
            }
        }
        _contentLength = Integer.valueOf(len); 
        return len;
    }


    public ByteBufferWrapper marshall(Object obj) throws IOException {
        OutputStream bos = null;
        DataOutputStream dos = null;

        try {
        bos = newBufferOutputStream(obj);
        dos = new DataOutputStream(bos);
      

        StringBuffer sbuf = new StringBuffer();
        sbuf.append(getCommand());
        sbuf.append(NEWLINESTR);
        for (String key: _headers.keySet()) { 
            sbuf.append(key);
            sbuf.append(HEADER_SEPERATOR);
            sbuf.append(_headers.get(key));
            sbuf.append(NEWLINESTR);
        }
        sbuf.append(NEWLINESTR);

        dos.write(sbuf.toString().getBytes("UTF-8"));
        dos.write(getBody());
        dos.write(END_OF_FRAME);
        dos.flush();
        ByteBufferWrapper bb = getBuffer(bos);
        bb.flip();
        return bb;

        } finally {
        if (dos != null) dos.close();
        if (bos != null) bos.close();
        }
    }


    /**
     */
    public void parseHeader(ByteBufferWrapper buf) throws Exception {
        String header = null;

        if (logger.isFineLoggable()) {
            logger.logFine( 
            "in parseHeader: position="+buf.position()+", remaining="+buf.remaining(), null);
        }

        try {

        while (buf.hasRemaining()) {
            byte[] line = parseLine(buf, MAX_HEADER_LEN, logger);
            if (line == null) {
                return;
            }
            header = new String(line, "UTF-8");

            if (logger.isFineLoggable()) {
                logger.logFine( 
               "parseHeader: got line byte-length="+line.length+
               ", header=:"+header+", header-length="+header.length()+", position="+buf.position(), null);
            }

            if (header.trim().length() == 0) {
                setNextParseStage(ParseStage.BODY);
                if (logger.isFinestLoggable()) {
                    logger.logFinest("parseHeader: DONE - position="+buf.position(), null);
                }
                return;
            }
            int index = header.indexOf(HEADER_SEPERATOR);
            if (index == -1) {
                if (_parseException == null) {
                    _parseException = new StompFrameParseException(getKStringX_INVALID_HEADER(header));
                    logger.logSevere(_parseException.getMessage(), null);
                }
                index = header.length()-1;
            }
            String key = header.substring(0, index).trim();
            String val = header.substring(index+1, header.length()).trim();
            addHeader(key, val);
            if (_headers.size() > MAX_HEADERS) { //XXX
                throw new StompFrameParseException(getKStringX_MAX_HEADERS_EXCEEDED(MAX_HEADERS));
            }
        }

        } catch (Exception e) {
            if (e instanceof StompFrameParseException) {
                throw e;
            }
            throw new StompFrameParseException(getKStringX_EXCEPTION_PARSE_HEADER(header, e.getMessage()), e);
        }
    }

    /**
     *
     */
    public void readBody(ByteBufferWrapper buf) throws Exception {

        int clen = getContentLength();

        if (logger.isFinestLoggable()) {
            logger.logFinest( 
            "in readBody:contentLen="+_contentLength+", position="+buf.position()+
            ", remaining="+buf.remaining() + ", bodySize=" + getBodySize(), null);
        }

        byte b;
        while (buf.hasRemaining()) {
            if (clen != -1 && clen == getBodySize()) { 
                if (logger.isFinestLoggable()) {
                    logger.logFinest("Body has beed read!", null);
                }
                setNextParseStage(ParseStage.NULL);
                return;
            }
            b = buf.get();
            if (b == NULL_BYTE && clen == -1) {

                if (buf.hasRemaining()) {
                    int pos = buf.position();
                    byte bb = buf.get();
                    if (bb != '\n' && bb != '\r') {
                        buf.position(pos);
                    }
                }
                if (buf.hasRemaining()) {
                    int pos = buf.position();
                    byte bb = buf.get();
                    if (bb != '\n') {
                        buf.position(pos);
                    }
                }
                if (logger.isFinestLoggable()) {
                    logger.logFinest(
                    "readBody: DONE - position="+buf.position()+", remaining="+buf.remaining(), null);
                }

                setNextParseStage(ParseStage.DONE);
                return;
            }
            writeByteToBody(b);
        }
        if (logger.isFinestLoggable()) {
            logger.logFinest("leaving readBody(): BODY_SIZE=" + getBodySize(), null);
        }
        return;
    }


    /**
     */
    public void readNULL(ByteBufferWrapper buf) throws Exception {

        if (logger.isFinestLoggable()) {
        logger.logFinest("in readNULL:"+buf.position()+":"+buf.remaining(), null);
        }
        if (buf.remaining() <= 0) {
            return; 
        }
        byte b = buf.get();
        if (b != 0) {
            throw new StompFrameParseException(
                getKStringX_NO_NULL_TERMINATOR(CommonHeader.CONTENTLENGTH+" "+getContentLength()));
        }

        if (logger.isFinestLoggable()) {
        logger.logFinest("got NULL readNULL:"+buf.position()+":"+buf.remaining(), null);
        }

        setNextParseStage(ParseStage.DONE);
        return;
    }

    public String toString() {
        return _command+"["+_headers+"]";
    }

    public String toLongString() throws Exception {
        return _command+"["+_headers+"]["+getBodyText()+"]";
    }

    /**
     */
    public static StompFrameMessage parseCommand(
        ByteBufferWrapper buf, LoggerWrapper logger, 
        StompFrameMessageFactory factory)
        throws Exception {

        StompFrameMessage message = null;
        String cmd = "";

        if (logger.isFinestLoggable()) {
            logger.logFinest( 
            "parseCommand: pos:remaining["+buf.position()+":"+buf.remaining()+"]", null);
        }

        try {

        while (cmd.trim().length() == 0) {
            byte[] line = parseLine(buf, MAX_COMMAND_LEN, logger);
            if (line == null) {
                if (logger.isFinestLoggable()) {
                    logger.logFinest( 
                    "parseCommand: position["+buf.position()+"] command line not found", null);
                }
                return null;
            }
            cmd = new String(line, "UTF-8");

            if (logger.isFinestLoggable()) {
                logger.logFinest( 
                "parseCommand: got line:"+cmd+", position="+buf.position(), null);
            }
        }
        

        if (cmd.startsWith((Command.CONNECTED).toString())) { 
            message = factory.newStompFrameMessage(Command.CONNECTED, logger);
        } else if (cmd.startsWith((Command.RECEIPT).toString())) { 
            message = factory.newStompFrameMessage(Command.RECEIPT, logger);
        } else if (cmd.startsWith((Command.MESSAGE).toString())) { 
            message = factory.newStompFrameMessage(Command.MESSAGE, logger);
        } else if (cmd.startsWith((Command.ERROR).toString())) { 
            message = factory.newStompFrameMessage(Command.ERROR, logger);

        } else if (cmd.startsWith((Command.CONNECT).toString()) ||
            cmd.startsWith((Command.STOMP).toString())) {
            message = factory.newStompFrameMessage(Command.CONNECT, logger);
        } else if (cmd.startsWith((Command.SEND).toString())) {
            message = factory.newStompFrameMessage(Command.SEND, logger);
        } else if (cmd.startsWith((Command.SUBSCRIBE).toString())) {
            message = factory.newStompFrameMessage(Command.SUBSCRIBE, logger);
        } else if (cmd.startsWith((Command.ACK).toString())) {
            message = factory.newStompFrameMessage(Command.ACK, logger);
        } else if (cmd.startsWith((Command.NACK).toString())) {
            message = factory.newStompFrameMessage(Command.NACK, logger);
        } else if (cmd.startsWith((Command.UNSUBSCRIBE).toString())) {
            message = factory.newStompFrameMessage(Command.UNSUBSCRIBE, logger);
        } else if (cmd.startsWith((Command.BEGIN).toString())) {
            message = factory.newStompFrameMessage(Command.BEGIN, logger);
        } else if (cmd.startsWith((Command.COMMIT).toString())) {
            message = factory.newStompFrameMessage(Command.COMMIT, logger);
        } else if (cmd.startsWith((Command.ABORT).toString())) {
            message = factory.newStompFrameMessage(Command.ABORT, logger);
        } else if (cmd.startsWith((Command.DISCONNECT).toString())) {
            message = factory.newStompFrameMessage(Command.DISCONNECT, logger);
        } else {
            message = factory.newStompFrameMessage(Command.ERROR, logger);
            String emsg = message.getKStringX_UNKNOWN_STOMP_CMD(cmd);
            message._parseException = new StompFrameParseException(emsg);
            logger.logSevere(emsg, null);
        }

        if (logger.isFinestLoggable()) {
            logger.logFinest(
            "parseCommand: DONE - cmd="+cmd+", position="+buf.position(), null);
        }

        message.setNextParseStage(ParseStage.HEADER);

        } catch (Exception e) {
            if (e instanceof StompFrameParseException) {
                throw e;
            }
            throw new StompFrameParseException(e.getMessage(), e);
        }

        return message;
    }


    /**
     */
    private static byte[] parseLine(ByteBufferWrapper buf, int maxbytes, LoggerWrapper logger) 
    throws Exception {

        byte[] line = new byte[maxbytes];
        int pos = buf.position();
        boolean foundline = false;
        int i = 0;
        byte b;
        while (buf.hasRemaining()) {
            b = buf.get();
/*
            if (logger.isFinestLoggable()) {
                logger.logFinest("parseLine: byte="+Byte.valueOf(b), null);
            }
*/
            if (b == NEWLINE_BYTE) {
                foundline = true; 
                break;
            }
            line[i++] = b;
            if (i >= (maxbytes-1)) {
                StompFrameMessage em = newStompFrameMessageERROR();
                throw new StompFrameParseException(
                    em.getKStringX_MAX_LINELEN_EXCEEDED(maxbytes));
            }
        }
        if (!foundline) {
            buf.position(pos);
            return null;
        }
        byte[] tmp = new byte[i];
        System.arraycopy(line, 0, tmp, 0, i);
        return tmp;
    }

    protected static StompFrameMessage newStompFrameMessageERROR() {
       return new StompFrameMessage(StompFrameMessage.Command.ERROR, null) {
            protected OutputStream newBufferOutputStream(Object obj) throws IOException {
                throw new RuntimeException("Unexpected call");
            }
            protected ByteBufferWrapper getBuffer(OutputStream os) throws IOException {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_CANNOT_PARSE_BODY_TO_TEXT(String command, String emsg) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_HEADER_NOT_SPECIFIED_FOR(String header, String command) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_INVALID_HEADER_VALUE(String headerValue, String command) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_INVALID_HEADER(String headerName) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_MAX_HEADERS_EXCEEDED(int maxHeaders) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_EXCEPTION_PARSE_HEADER(String headerName, String emsg) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_NO_NULL_TERMINATOR(String contentlen) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_UNKNOWN_STOMP_CMD(String cmd) {
                throw new RuntimeException("Unexpected call");
            }
            protected String getKStringX_MAX_LINELEN_EXCEEDED(int maxbytes) {
                throw new RuntimeException("Unexpected call");
            }
            };
    }
         
    protected abstract OutputStream newBufferOutputStream(Object obj) throws IOException;
    protected abstract ByteBufferWrapper getBuffer(OutputStream os) throws IOException;

    protected abstract String getKStringX_CANNOT_PARSE_BODY_TO_TEXT(String cmd, String emsg);
    protected abstract String getKStringX_HEADER_NOT_SPECIFIED_FOR(String headerName, String cmd);
    protected abstract String getKStringX_INVALID_HEADER_VALUE(String headerValue, String cmd);
    protected abstract String getKStringX_INVALID_HEADER(String headerName);
    protected abstract String getKStringX_MAX_HEADERS_EXCEEDED(int maxHeaders);
    protected abstract String getKStringX_EXCEPTION_PARSE_HEADER(String headerName, String emsg);
    protected abstract String getKStringX_NO_NULL_TERMINATOR(String contentlen);
    protected abstract String getKStringX_UNKNOWN_STOMP_CMD(String cmd);
    protected abstract String getKStringX_MAX_LINELEN_EXCEEDED(int maxbytes);
}
