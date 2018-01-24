/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.service.imq.JMSServiceImpl;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.MQWebSocket;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.MQWebSocketServiceApp;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompFrameParseException;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompOutputHandler;


/**
 * @author amyk
 */
public class STOMPWebSocket extends MQWebSocket implements StompOutputHandler {

    protected static final Logger logger = Globals.getLogger();

    private final FrameParseState parseState = new FrameParseState();
    private StompProtocolHandler stompProtocolHandler = null;

    static class FrameParseState {
        public Buffer buf = null;
        public StompFrameMessageImpl message = null;

         public void reset() {
             buf = null;
             message = null;
         }
    }

    public STOMPWebSocket(MQWebSocketServiceApp app, 
                       ProtocolHandler protocolHandler,
                       HttpRequestPacket request,
                       WebSocketListener... listeners) {
        super(app, protocolHandler, request, listeners);
	JMSService jmss = new JMSServiceImpl(app.getMQService(),
                              Globals.getProtocol(), true/*XXimpl*/);
        stompProtocolHandler = new StompProtocolHandlerImpl(this, jmss);
    }

    @Override
    protected String getLogString() {
        return super.getLogString()+"["+stompProtocolHandler.toString()+"]";
    }

    @Override
    public String toString() {
        return "["+getClass().getSimpleName()+"@"+hashCode()+
            "["+super.getLogString()+"["+stompProtocolHandler.toString()+"]]]";
    }

    @Override
    public void onClose(final DataFrame frame) {
        try {
            stompProtocolHandler.close(false);
        }finally {
	    super.onClose(frame);
	}
    }

    @Override
    protected void writePacket(Packet pkt) throws IOException {
        String[] args = { getClass().getSimpleName()+
                          ".writPacket(): unexpected call" };
        throw new IOException(getLogString()+
        br.getKTString(br.E_INTERNAL_BROKER_ERROR, args)); 
    }

    @Override
    protected void processData(String text) throws Exception {
        if (DEBUG) {
            logger.log(Logger.INFO, toString()+".processData(text="+text+")");
        }
        byte[] data = text.getBytes("UTF-8");
        Buffer buf = Buffers.wrap(memManager, data);
        processData(buf);
    }

    @Override
    protected void processData(byte[] data) throws Exception {
        Buffer buf = Buffers.wrap(memManager, data);
        processData(buf);
    }

    private void processData(Buffer buf) throws Exception {
        if (DEBUG) {
            logger.log(Logger.INFO,
                Thread.currentThread()+" processData:buf.remaining="+buf.remaining());
        }

        if (parseState.buf != null) {
            parseState.buf.shrink();
        }
        parseState.buf = Buffers.appendBuffers(memManager, parseState.buf, buf);
        Buffer input = parseState.buf;
        if (parseState.message == null) {
            if (input.remaining() >= StompFrameMessage.MIN_COMMAND_LEN) {
                parseState.message = StompFrameMessageImpl.parseCommand(input);
                if (DEBUG) {
                    logger.log(logger.INFO, "returned from parseCommand with "+parseState.message);
                }
            }
        }
        if (parseState.message == null) {
            return;        
        }

        StompFrameMessageImpl message = parseState.message;
        if (message.getNextParseStage() == StompFrameMessage.ParseStage.HEADER) {
            message.parseHeader(input);
            if (DEBUG) {
                logger.log(logger.INFO, "returned from parseHeader");
            }

	}
        if (message.getNextParseStage() == StompFrameMessage.ParseStage.BODY) {
            message.readBody(input);
	}
        if (message.getNextParseStage() == StompFrameMessage.ParseStage.NULL) {
            message.readNULL(input);
	}
        if (DEBUG) {
            logger.log(logger.INFO,
            "position="+buf.position()+", input="+input+", nextParseState="+message.getNextParseStage());
        }

	if (message.getNextParseStage() != StompFrameMessage.ParseStage.DONE) {
            if (DEBUG) {
                logger.log(logger.INFO,
                "parseData with position="+input.position()+", hasRemaining="+input.hasRemaining());
            }
            return;
        }
        Exception ex = message.getParseException();
	if (ex != null) {
            if (ex instanceof StompFrameParseException) {
		message = (StompFrameMessageImpl)((StompFrameParseException)ex).
                          getStompMessageERROR(StompFrameMessageImpl.getFactory(), logger);
            } else {
                message = (StompFrameMessageImpl)(new StompFrameParseException(ex.getMessage(), ex)).
                          getStompMessageERROR(StompFrameMessageImpl.getFactory(), logger);
            }
            message.setFatalERROR(); 
            parseState.reset();
            sendToClient(message, stompProtocolHandler, null);
            return;
	}
        final Buffer remainder = input.split(input.position());
	parseState.reset();
        parseState.buf = remainder;

        dispatchMessage(message);
    }

    protected void dispatchMessage(StompFrameMessageImpl m) 
    throws Exception {
	final StompFrameMessageImpl msg = m;
	switch (msg.getCommand()) {
            case CONNECT:
            case STOMP:
                ClusterBroadcast cbc = Globals.getClusterBroadcast();
                if (cbc.waitForConfigSync()) {
                    String emsg = br.getKString(
                        br.X_CLUSTER_NO_SYNC_WITH_MASTER_BROKER_RETRY_CONNECT,
                        Globals.getClusterManager().getMasterBroker()); 
                    sendFatalError(new BrokerException(
                             "CONNECT: "+emsg+", "+Status.RETRY));
                    break;
                }
		stompProtocolHandler.onCONNECT(msg, this, null);
		break;
            case SEND:
                if (DEBUG) {
                    logger.log(logger.INFO, "StompWebSocket.processData(SEND): "+msg);
                }
		stompProtocolHandler.onSEND(msg, this, null);
		break;
            case SUBSCRIBE:
		stompProtocolHandler.onSUBSCRIBE(msg, this, this, null);
                break;
            case UNSUBSCRIBE:
		stompProtocolHandler.onUNSUBSCRIBE(msg, this, null);
		break;
            case BEGIN:
		stompProtocolHandler.onBEGIN(msg, this, null);
		break;
            case COMMIT:
		stompProtocolHandler.onCOMMIT(msg, this, null);
		break;
            case ABORT:
		stompProtocolHandler.onABORT(msg, this, null);
		break;
            case ACK:
		stompProtocolHandler.onACK(msg, this, null);
		break;
            case NACK:
		stompProtocolHandler.onNACK(msg, this, null);
		break;
            case DISCONNECT:
		stompProtocolHandler.onDISCONNECT(msg, this, null);
                this.close(WebSocket.NORMAL_CLOSURE,  "DISCONNECT");
		break;
            case ERROR:
		sendToClient(msg, stompProtocolHandler, null);
		break;
            default:
	        Exception e = new IOException(
                msg.getKStringX_UNKNOWN_STOMP_CMD(msg.getCommand().toString()));
                logger.log(logger.ERROR, e.getMessage());
                sendFatalError(e);
     
	}
    }

    protected void sendFatalError(Exception e) {
        logger.log(logger.ERROR,  e.getMessage());
        try {
            StompFrameMessage err = stompProtocolHandler.
                toStompErrorMessage(getClass().getSimpleName(), e);
            err.setFatalERROR();
            sendToClient(err, stompProtocolHandler, null);
        } catch (Exception ee) {
            logger.log(logger.ERROR, "br.E_UNABLE_SEND_ERROR_MSG: "+e.toString()+": "+ee.toString(), ee);
            this.close();
        }
    }

    /******************************************
     * Implements StompOutputHandler interface
     ********************************************************/

    public void sendToClient(final StompFrameMessage msg,
                             StompProtocolHandler sph, Object ctx)
                             throws Exception {

        if (DEBUG) {
            logger.log(logger.INFO, toString()+" sendToClient("+msg+")");
        }
	boolean closechannel = false;
	try {
            if (msg.getCommand() == StompFrameMessage.Command.ERROR) {
		if (msg.isFatalERROR()) {
                    closechannel = true;
		}
            }
            synchronized(closeLock) {
                if (isClosed()) {
                    logger.log(logger.INFO, toString()+" closed");
                    return;
                }
                doSend(msg);
            }
	} catch (Exception e) {
            if (e instanceof java.nio.channels.ClosedChannelException ||
		e.getCause() instanceof java.nio.channels.ClosedChannelException) {
		logger.logStack(logger.WARNING,  
                    "I18NXX-Exception on sending message stomp websocket client:"+msg, e);
		if (sph != null) {
                    sph.close(false);
		}
            }
            throw e;
	} finally {
            if (closechannel) {
		close();
            }
	}
    }

    protected void doSend(StompFrameMessage frame) throws Exception {
        Buffer buf = (Buffer)frame.marshall(memManager).getWrapped();
        byte[] bb = new byte[buf.remaining()]; //XXopt
        buf.get(bb);
        send(bb);
        if (DEBUG) {
            logger.log(logger.INFO, 
                getClass().getSimpleName()+"@"+hashCode()+
                " SENT "+bb.length+" bytes");
        }
    }

    public void sendToClient(StompFrameMessage msg) throws Exception {
        sendToClient(msg, null, null);
    }

}
