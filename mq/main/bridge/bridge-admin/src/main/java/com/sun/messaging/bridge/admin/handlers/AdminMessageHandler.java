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

/*
 */ 

package com.sun.messaging.bridge.admin.handlers;

import java.util.Locale;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.MessageProducer;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import com.sun.messaging.bridge.api.BridgeBaseContext;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.api.BridgeServiceManager;
import com.sun.messaging.bridge.admin.BridgeServiceManagerImpl;
import com.sun.messaging.bridge.admin.resources.BridgeManagerResources;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.jmq.io.Status;


/**
 */
public class AdminMessageHandler
{
    private static boolean DEBUG = false;

    private BridgeManagerResources _br = null;
    private BridgeBaseContext _bc = null;
    private BridgeServiceManagerImpl _bsm = null;

    private AdminCmdHandler[] _handlers = null;

    public AdminMessageHandler(BridgeServiceManagerImpl bsm) {
        DEBUG = bsm.getDEBUG();
        _bc = bsm.getBridgeBaseContext();
        _br = bsm.getBridgeManagerResources();
        _bsm = bsm;
        initHandlers();
    }

    protected boolean getDEBUG() {
        return DEBUG;
    }

    /**
     */
    public void handle(Session session, ObjectMessage msg) {

        BridgeManagerResources bmr = _br;

        ObjectMessage reply = null;
	    int msgType = AdminMessageType.Type.LAST;

        try {

        reply = session.createObjectMessage();

        if (DEBUG) {
            _bc.logInfo("BridgeAdminMessageHandler.handle:\n<<<<****"+msg.toString(), null);
        }

	    try {

            String lang = msg.getStringProperty(AdminMessageType.PropName.LOCALE_LANG);
            String country = msg.getStringProperty(AdminMessageType.PropName.LOCALE_COUNTRY);
            String variant = msg.getStringProperty(AdminMessageType.PropName.LOCALE_VARIANT);
            Locale locale = new  Locale(lang, country, variant);
            bmr = _bsm.getBridgeManagerResources(locale);
        } catch (Exception e) {
            bmr = _br;
            String emsg = _br.getKString(_br.E_GET_LOCALE_FAILED, e.toString(), msg.toString());
	        _bc.logWarn(emsg, null);
        }
        
        Destination dest = msg.getJMSDestination();
        if (!(dest instanceof Queue)) { 
            throw new BridgeException(_br.getKString(_br.X_ADMIN_MSG_NOT_QUEUE, dest));
        }
        if (!((Queue)dest).getQueueName().equals(_bsm.getAdminDestinationName())) {
            throw new BridgeException(_br.getKString(_br.X_ADMIN_MSG_UNEXPECTED_DEST, dest));
        }
        
	    try {
	        msgType = msg.getIntProperty(AdminMessageType.PropName.MESSAGE_TYPE);
	    } catch (Exception e) {
            msgType = AdminMessageType.Type.LAST;
            String emsg = _br.getKString(_br.X_EXCEPTION_PROCESSING_ADMIN_MSG, msg.toString());
	        _bc.logError(emsg, e);
            throw new BridgeException(emsg, e);
	    }

        AdminCmdHandler ach = null;
	    try {
	        ach = _handlers[msgType];
	    } catch (IndexOutOfBoundsException e) {
            String emsg = _br.getKString(_br.X_UNEXPECTED_ADMIN_MSG_TYPE, AdminMessageType.getString(msgType));
            throw new BridgeException(emsg);
        }
	    if (ach == null) {
            String emsg = "No bridge admin handler for message type "+AdminMessageType.getString(msgType);
            throw new BridgeException(emsg);
	    }

        reply.setIntProperty(AdminMessageType.PropName.MESSAGE_TYPE, ++msgType);

        ach.handle(session, msg, reply, bmr);

        } catch (Throwable t) {

        int status = Status.ERROR;
        if (t instanceof BridgeException) {
            BridgeException be = (BridgeException)t;
            status = be.getStatus();
            if (be.getCause() == null) {//else should already logged
                _bc.logError(t.getMessage(), null);
            }
        } else {
            _bc.logError(t.getMessage(), t);
        }
        if (DEBUG)  {
            _bc.logInfo("BridgeAdminMessageHandler exception: "+t.getMessage()+ 
                         (reply == null ? "no reply created":"sending error reply"), t);
        }
        if (reply != null) {
            sendReply(session, msg, reply, status, AdminCmdHandler.getMessageFromThrowable(t), bmr);
        }
        
        }
    }

    /**
     */
    private void initHandlers() {

        _handlers = new AdminCmdHandler[AdminMessageType.Type.LAST];

        _handlers[AdminMessageType.Type.LIST] = new ListHandler(this, _bsm);
        _handlers[AdminMessageType.Type.PAUSE] = new PauseHandler(this, _bsm);
        _handlers[AdminMessageType.Type.RESUME] = new ResumeHandler(this, _bsm);
        _handlers[AdminMessageType.Type.START] = new StartHandler(this, _bsm);
        _handlers[AdminMessageType.Type.STOP] = new StopHandler(this, _bsm);
        _handlers[AdminMessageType.Type.HELLO] = new HelloHandler(this, _bsm);
        _handlers[AdminMessageType.Type.DEBUG] = new DebugHandler(this, _bsm);
    }


    /**
     */
    public void sendReply(Session session, 
                          Message msg, ObjectMessage replyMsg,
                          int status, String emsg, BridgeManagerResources bmr) {

        ObjectMessage reply = replyMsg;
        try {


        Destination replyTo = msg.getJMSReplyTo();
        if (replyTo == null) {
            _bc.logError("Bridge admin message has no JMSReplyTo destination. Not replying.", null);
            return;
        }
        if (reply == null) {
            reply = session.createObjectMessage();
        }
        reply.setIntProperty(AdminMessageType.PropName.STATUS, status);
        if (emsg != null) {
            reply.setStringProperty(AdminMessageType.PropName.ERROR_STRING, emsg);
        }

        MessageProducer sender = session.createProducer(replyTo);
        sender.send(reply, DeliveryMode.NON_PERSISTENT, 4, 0);
        if (DEBUG) {
            try {
	        _bc.logInfo("BridgeAdminMessageHandler: sent REPLY\n>>>>****" + reply+"Body:"+reply.getObject(), null);
            } catch (Throwable t) {
	        _bc.logWarn("Unexpected exception : "+t.getMessage(), t);
            }
        }


        } catch (Throwable t) {
            _bc.logError(_br.getKString(_br.E_UNABLE_SEND_ADMIN_REPLY, ""+reply, msg.toString()), t);
        }
	}
}
