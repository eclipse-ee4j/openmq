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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.json;

import java.util.Map;
import java.util.Iterator;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.JsonObjectBuilder;
import javax.json.JsonBuilderFactory;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import com.sun.messaging.jmq.util.BASE64Encoder;
import com.sun.messaging.jmq.util.BASE64Decoder;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.MQWebSocketServiceApp;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp.STOMPWebSocket;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp.StompFrameMessageImpl;

/**
 * @author amyk
 */
public class JSONWebSocket extends STOMPWebSocket {

    private Class base64Class = null;

    public JSONWebSocket(MQWebSocketServiceApp app, 
                       ProtocolHandler protocolHandler,
                       HttpRequestPacket request,
                       WebSocketListener... listeners) {
        super(app, protocolHandler, request, listeners);
        base64Class = app.getBase64Class();
    }

    @Override
    protected void processData(byte[] data) throws Exception {
        String[] args = { getClass().getSimpleName()+
                          ".processData(byte[]): unexpected call" };
        throw new IOException(getLogString()+
            br.getKTString(br.E_INTERNAL_BROKER_ERROR, args));
    }


    @Override
    protected void processData(String text) throws Exception {
        if (DEBUG) {
            logger.log(logger.INFO, toString()+".processData(text="+text+")");
        }

        JsonObject joreply = null;
        try {

        JsonReader jsonReader = Json.createReader(new StringReader(text)); 
        JsonObject jo = jsonReader.readObject();
        String command = jo.getString(JsonMessage.Key.COMMAND); 
        JsonObject headers = jo.getJsonObject(JsonMessage.Key.HEADERS); 
        JsonObject body = jo.getJsonObject(JsonMessage.Key.BODY); 
        StompFrameMessage frame = StompFrameMessageImpl.getFactory().
            newStompFrameMessage(StompFrameMessage.Command.valueOf(command), logger);
        Iterator<String> itr =  headers.keySet().iterator(); 
        String key;
        String val;
        while (itr.hasNext()) {
            key = itr.next();
            val = headers.getString(key);
            if (val != null) {
                frame.addHeader(key, val);
            }
        }
        if (body != null) {
            JsonString btype = body.getJsonString(JsonMessage.BodySubKey.TYPE);
            if (btype == null || btype.getString().equals(JsonMessage.BODY_TYPE_TEXT)) {
                JsonString msg = body.getJsonString(JsonMessage.BodySubKey.TEXT);
                if (msg != null) {
                    frame.setBody(msg.getString().getBytes("UTF-8"));
                }
            } else if (btype.getString().equals(JsonMessage.BODY_TYPE_BYTES)) {
                JsonString enc = body.getJsonString("encoder");
                if (enc == null || enc.getString().equals(JsonMessage.ENCODER_BASE64)) {
                    JsonString msg = body.getJsonString(JsonMessage.BodySubKey.TEXT);
                    if (msg != null) {
                        byte[] bytes = null;
                        if (base64Class == null) {
                            BASE64Decoder decoder = new BASE64Decoder();
                            bytes = decoder.decodeBuffer(msg.getString());
                        } else {
                            Method gm = base64Class.getMethod(
                                        "getDecoder", (new Class[]{}));
                            Object o =  gm.invoke(null);
                            Method dm = o.getClass().getMethod(
                                "decode", (new Class[]{String.class}));
                            bytes = (byte[])dm.invoke(o, msg.getString()); 
                        }
                        frame.setBody(bytes);
                        frame.addHeader(
                            StompFrameMessage.CommonHeader.CONTENTLENGTH, 
                            String.valueOf(bytes.length));
                    }
                } else {
                    throw new IOException("encoder "+enc+" not supported");
                } 
            } else {
                throw new IOException("body type:"+btype+" not supported");
            }
        }

        dispatchMessage((StompFrameMessageImpl)frame);

        } catch (Exception e) {
            logger.logStack(logger.ERROR, e.getMessage(), e);
            sendFatalError(e);
        }
    }

    @Override
    protected void doSend(StompFrameMessage frame) throws Exception {
        JsonBuilderFactory jsonfactory = Json.createBuilderFactory(null);
        JsonObjectBuilder obuilder= jsonfactory.createObjectBuilder();
        JsonObjectBuilder hbuilder = jsonfactory.createObjectBuilder(); 
        JsonObjectBuilder bbuilder = jsonfactory.createObjectBuilder(); 
        obuilder = obuilder.add(JsonMessage.Key.COMMAND, frame.getCommand().toString());
        Iterator<Map.Entry<String, String>> itr = 
                frame.getHeaders().entrySet().iterator();
        Map.Entry<String, String> pair;
        String key, val;
        while (itr.hasNext()) {
            pair = itr.next();
            key = pair.getKey();
            val = pair.getValue();
            hbuilder.add(key, val);
        }
        obuilder.add(JsonMessage.Key.HEADERS, hbuilder.build());
        if (frame.getCommand().equals(StompFrameMessage.Command.MESSAGE)) {
            if (frame.isTextMessage()) {
                String body = frame.getBodyText();
                bbuilder.add(JsonMessage.BodySubKey.TYPE, JsonMessage.BODY_TYPE_TEXT);
                if (body !=  null) {
                    bbuilder.add(JsonMessage.BodySubKey.TEXT, body);
                } else {
                    bbuilder.add(JsonMessage.BodySubKey.TEXT, "");
                }
            } else {
                byte[] body = frame.getBody();
                bbuilder.add(JsonMessage.BodySubKey.TYPE, JsonMessage.BODY_TYPE_BYTES);
                bbuilder.add(JsonMessage.BodySubKey.ENCODER, JsonMessage.ENCODER_BASE64);
                String textbody = "";
                if (body !=  null) {
                    if (base64Class == null) {
                        BASE64Encoder encoder = new BASE64Encoder();
                        textbody = encoder.encode(body);
                    } else {
                        Method gm = base64Class.getMethod(
                                    "getEncoder", (new Class[]{}));
                        Object o = gm.invoke(null);
                        Method em = o.getClass().getMethod(
                            "encodeToString", (new Class[]{byte[].class}));
                        textbody = (String)em.invoke(o, body); 
                    }
                }
                bbuilder.add(JsonMessage.BodySubKey.TEXT, textbody);
            }
        } else {
            byte[] body = frame.getBody();
            bbuilder.add(JsonMessage.BodySubKey.TYPE, JsonMessage.BODY_TYPE_TEXT);
            if (body !=  null) {
                bbuilder.add(JsonMessage.BodySubKey.TEXT, new String(body, "UTF-8"));
            } else {
                bbuilder.add(JsonMessage.BodySubKey.TEXT, "");
            }
        }
        JsonObject jo = obuilder.add(JsonMessage.Key.BODY, bbuilder.build()).build();
        send(jo.toString());
        if (DEBUG) {
            logger.log(logger.INFO, toString()+" SENT JsonObject["+jo+"]");
        }
    }
}
