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

package com.sun.messaging.jms.ra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;

import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsservice.JMSService;
//import com.sun.messaging.jmq.io.JMQByteArrayOutputStream;
import com.sun.messaging.jmq.util.io.ClassFilter;

/**
 *
 */
public class DirectObjectPacket
        extends DirectPacket
        implements javax.jms.ObjectMessage {

    /** The messageBody of this JMS ObjectMessage */
    private byte[] messageBody = null;

    /** The OutputStream used to buffer the written data */
    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    /** The InputStream used to buffer the data to be read */
    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectObjectPacket";

    /** 
     *  Create a new instance of DirectObjectPacket.<p>
     *
     *  Used by the createObjectMessage API
     */
    public DirectObjectPacket(DirectSession ds,
            Serializable obj)
    throws JMSException {
        super(ds);
        if (_logFINE){
            Object params[] = new Object[3];
            params[0] = ds;
            params[2] = obj;
            _loggerOC.entering(_className, "constructor()", params);
        }
        if (obj != null) {
            this.setObject(obj);
        }
    }

    /**
     *  Create a new instance of DirectObjectPacket.
     *  Used by Consumer.deliver.
     */
    public DirectObjectPacket(JMSPacket jmsPacket, long consumerId,
            DirectSession ds, JMSService jmsservice)
    throws JMSException {
        super(jmsPacket, consumerId, ds, jmsservice);
        this._getMessageBodyFromPacket();
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.ObjectMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Clear out the message body .
     */
    public void clearBody()
    throws JMSException {
        super.clearBody();
        this.messageBody = null;
    }

    /**
     *  Get the serializable object containing this message's data. The 
     *  default value is null.
     *
     *  @return The serializable object containing this message's data
     *  
     *  @throws JMSException if the JMS provider fails to get the object
     *          due to some internal error.
     *  @throws MessageFormatException if object deserialization fails.
     */
    public Serializable getObject()
    throws JMSException {
        String methodName = "getObject()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        Serializable object = null;
        if (this.messageBody != null){
            try {
                byteArrayInputStream = new ByteArrayInputStream(messageBody);
                objectInputStream = new 
                        ObjectInputStreamWithContextLoader(byteArrayInputStream);
                object = (Serializable) objectInputStream.readObject();
                return object;
            } catch (Exception e) {
                JMSException jmse = null;
                String eMsg = _lgrMID_EXC +
                        ":Exception:ObjectMessage." + methodName + 
                        "DeSerializing object:" + 
                        ":message="+ e.getMessage();
                _loggerJM.severe(eMsg);
                if ((e instanceof InvalidClassException) ||
                        (e instanceof OptionalDataException) ||
                        (e instanceof ClassNotFoundException)){
            
            
                    jmse = new MessageFormatException(eMsg);
                } else {
                    jmse = new JMSException(eMsg);
                }
                jmse.initCause(e);
                throw jmse;
            }
        }
        return null;
    }

    /**
     *  Set the serializable object containing this message's data.
     *  It is important to note that an <CODE>ObjectMessage</CODE>
     *  contains a snapshot of the object at the time <CODE>setObject()</CODE>
     *  is called; subsequent modifications of the object will have no 
     *  effect on the <CODE>ObjectMessage</CODE> body.
     *
     *  @param  object The message's data
     *  
     *  @throws JMSException if the JMS provider fails to set the object
     *          due to some internal error.
     *  @throws MessageFormatException if object serialization fails.
     *  @throws MessageNotWriteableException if the message is in read-only
     *          mode.
     */
    public void setObject(Serializable object)
    throws JMSException {
        String methodName = "setObject()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

            objectOutputStream.writeObject(object);
            objectOutputStream.flush();

            messageBody = byteArrayOutputStream.toByteArray();

            objectOutputStream.close();
            byteArrayOutputStream.close();
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:ObjectMessage."+methodName+"object="+object+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.ObjectMessage
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods DirectMapPacket / javax.jms.ObjectMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Set the JMS default values on this JMS ObjectMessage
     */
    protected void _setDefaultValues()
    throws JMSException {
        super._setDefaultValues();
        this.pkt.setPacketType(PacketType.OBJECT_MESSAGE);
    }

    /**
     *  Set the JMS Message body into the packet
     */
    protected void _setBodyToPacket()
    throws JMSException {
        if (this.messageBody != null) {
            try {
                super._setMessageBodyOfPacket(this.messageBody);
            } catch (Exception ex) {
                String errMsg = _lgrMID_EXC +
                        ":ERROR setting ObjectMessage body"+
                        ":Exception="+ ex.getMessage();
                _loggerJM.severe(errMsg);
                JMSException jmse = new javax.jms.JMSException(errMsg);
                jmse.initCause(ex);
                throw jmse;
            }
        }
    }

    /**
     *  Get the message body from the packet
     */
    protected void _getMessageBodyFromPacket()
    throws JMSException {
        this.messageBody = super._getMessageBodyByteArray();
    }

    /**
     *  This class handles class loading, if a ContextClassLoader
     *  is set on the current Thread.<p>
     *  This strategy is only attempted if the ObjectInputStream
     *  class loading mechanism fails.
     */
    static class ObjectInputStreamWithContextLoader
            extends ObjectInputStream {

        /** Contructs an ObjectInputStreamWithContextLoaded */
        public ObjectInputStreamWithContextLoader(InputStream in)
        throws IOException, StreamCorruptedException {
            super(in);
        }

        /**
         *  Override the default
         */
        protected Class resolveClass(ObjectStreamClass classDesc)
        throws IOException, ClassNotFoundException {
          
            String className = classDesc.getName();
            if (className != null && !className.isEmpty() && ClassFilter.isBlackListed(className)) {
              throw new InvalidClassException("Unauthorized deserialization attempt", classDesc.getName());
            }
          
            try {
                return super.resolveClass(classDesc);
            } catch (ClassNotFoundException e) {
                //try Thread.ContextClassLoader
                ClassLoader ctxcl = null;
                try {
                    ctxcl = Thread.currentThread().getContextClassLoader();
                } catch (SecurityException se) {
                    throw new ClassNotFoundException(e.getMessage() +
                            "; " + se.getMessage());
                }
                if (ctxcl == null)  {
                    throw e;
                }
                return Class.forName(classDesc.getName(), false, ctxcl);
            }
        }
    }
}
