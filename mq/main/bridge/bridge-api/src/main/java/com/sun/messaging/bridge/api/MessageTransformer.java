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

import java.util.Properties;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

/**
 *
 * The message transformer class to be extended by user. 
 * Its implementation must provide a public zero-argument constructor.
 *
 * The following is an example usage of this class for MQ STOMP bridge
 * <pre>
 * import java.util.*;
 * import javax.jms.*;
 * import com.sun.messaging.bridge.api.MessageTransformer;
 *
 * public class MessageTran extends MessageTransformer &lt;Message, Message&gt; {
 *
 * public Message transform(Message message, 
 *                          boolean readOnly,
 *                          String charsetName,
 *                          String source, 
 *                          String target,
 *                          Properties properties)
 *                          throws Exception {
 *
 *    Message m = message;
 *    if (source.equals(STOMP)) { //from STOMP client to Java Message Queue
 *        //convert any invalid headers from STOMP SEND frame
 *        if (properties != null) {
 *            ......
 *            //convert key to valid JMS message property name, then call m.setStringProperty()
 *            ......
 *        }
 *     
 *    } else if (source.equals(SUN_MQ)) { //from Java Message Queue to STOMP client
 *
 *        if (message instanceof ObjectMessage) {
 *
 *            //create a new BytesMessage for <i>message</i> to be transformed to 
 *            BytesMessage bm = (BytesMessage)createJMSMessage(JMSMessageType.BYTESMESSAGE);
 *               
 *            //convert <i>message</i> to the BytesMessage
 *            ......
 *            m = bm;
 *        } else {
 *            ....
 *        }
 *    }
 *    return m;
 * }
 *</pre>
 *
 * @author amyk
 */
public abstract class MessageTransformer <T, S>
{
    /**
     * The predefined provider name for JMS message to/from Sun Java Message Queue
     */
    public static final String SUN_MQ = "SUN_MQ";

    /**
     * The predefined provider name for JMS message to/from STOMP client
     */
    public static final String STOMP = "STOMP";

    private Object _obj = null;
    private Object _branchTo = null;
    private String _bridgeType = null;
    private boolean _notransfer = false;

    public enum JMSMessageType {
        MESSAGE, TEXTMESSAGE, BYTESMESSAGE, MAPMESSAGE, STREAMMESSAGE, OBJECTMESSAGE 
    }

    /**
     * This method is called by the bridge service before transform() is called.
     *
     * A message transformer object is initialized by init() each time 
     * before transform() is called. After transform() returns, it's back  
     * to uninitialized state.
     *
     */
    public final void init(Object obj, String bridgeType) {
        _obj = obj;
        _branchTo = null;
        _bridgeType = bridgeType;
        _notransfer = false;
    }

    /**
     * This method is called by the bridge service after transform()
     * is returned for bridge types that support branchTo()
     */
    public final Object getBranchTo() {
        return _branchTo;
    }

    /**
     * This method is called by the bridge service after transform()
     * is returned for bridge types that support noTransfer()
     */
    public final boolean isNoTransfer() {
        return _notransfer;
    }

   

    /**
     * Create a JMS message object.
     *
     * This method is to be used in tranform() method implemenation
     * when it needs to create a new JMS message 
     *
     * @param type the type of the JMS message to be created
     *
     * @return a newly created uninitialized JMS message object 
     *
     * @exception IllegalStateException if this MessageTransfomer object is not initialized
     *
     * @exception Exception if fails to create the JMS Message
     */
    protected final Message createJMSMessage(JMSMessageType type) throws Exception {
        javax.jms.Session ss = (javax.jms.Session)_obj; 
        if (ss == null) {
            throw new IllegalStateException("The MessageTransformer is not initialized !");
        }
        switch (type) {
            case MESSAGE:
                 return ss.createMessage();
            case TEXTMESSAGE:
                 return ss.createTextMessage();
            case BYTESMESSAGE:
                 return ss.createBytesMessage();
            case MAPMESSAGE:
                 return ss.createMapMessage();
            case STREAMMESSAGE:
                 return ss.createStreamMessage();
            case OBJECTMESSAGE:
                 return ss.createObjectMessage();
            default: throw new IllegalArgumentException("Unexpected message type "+type);
        }
    }

    /**
     * To be called from the transform() method when needs to create a JMS Queue 
     * object to the target provider	
     *
     * @param queueName the name of the Queue 
     *
     * @return a javax.jms.Queue object 
     *
     * @exception IllegalStateException if this MessageTransfomer object is not initialized
     * @exception Exception if fails to create the Queue object
     */
    protected final Queue createQueue(String queueName) throws Exception {
        javax.jms.Session ss = (javax.jms.Session)_obj; 
        if (ss == null) {
            throw new IllegalStateException("The MessageTransformer is not initialized !");
        }
        return ss.createQueue(queueName);
    }

    /**
     * To be called from the transform() method when needs to create a JMS Topic 
     * object to the target provider	
     *
     * @param topicName the name of the Topic 
     *
     * @return a javax.jms.Topic object 
     *
     * @exception IllegalStateException if this MessageTransfomer object is not initialized
     * @exception Exception if fails to create the Topic object
     */
    protected final Topic createTopic(String topicName) throws Exception {
        javax.jms.Session ss = (javax.jms.Session)_obj; 
        if (ss == null) {
            throw new IllegalStateException("The MessageTransformer is not initialized !");
        }
        return ss.createTopic(topicName);
    }

    /**
     * To be called from the transform() method when needs to tell the bridge to
     * branch the message that is to be returned by the transform() call to a 
     * different destination in the target provider
     *
     * @param d a java.lang.String or javax.jms.Destination object that specifies
     *          the destination in target provider to branch the message to
     *
     * @exception IllegalStateException if this MessageTransfomer object is not initialized
     * @exception IllegalArgumentException if null or unexpected object type passed in
     * @exception UnsupportedOperationException if the operation is not supported for the bridge type
     *
     * @exception Exception if fails to create the JMS Message
     */
    protected final void branchTo(Object d) throws Exception {
        if (_obj == null) {
            throw new IllegalStateException(
            "The MessageTransformer is not initialized !");
        }
        if (!_bridgeType.equals(Bridge.JMS_TYPE)) {
            throw new UnsupportedOperationException(
            "MessageTransformer.branchTo() is not supported for bridge type "+_bridgeType);
        }
        if (d == null) {
            throw new IllegalArgumentException("null passed to MessageTransformer.branchTo()");
        }
        if (!(d instanceof String) && !(d instanceof javax.jms.Destination)) {
            throw new IllegalArgumentException(
            "Unexpected branchTo object type: "+d.getClass().getName());
        }
        _branchTo = d;
    }


    /**
     * To be called from the transform() method when needs to tell the bridge
     * to consume from source and not transfer to target the message that is 
     * to be returned by the transform() call
     *
     * @exception IllegalStateException if this MessageTransfomer object is not initialized
     * @exception UnsupportedOperationException if the operation is not supported for the bridge type
     *
     */
    protected final void noTransfer() throws Exception { 
        if (_obj == null) {
            throw new IllegalStateException(
            "The MessageTransformer is not initialized !");
        }
        if (!_bridgeType.equals(Bridge.JMS_TYPE)) {
            throw new UnsupportedOperationException(
            "MessageTransformer.noTransfer() is not supported for bridge type "+_bridgeType);
        }
        _notransfer = true; 
    }

    /**
     * To be implemented by user 
     *
     * @param message the message object to be tranformed. 
     * @param readOnly if the <i>message</i> is in read-only mode 
     * @param charsetName the charset name for <i>message</i> if applicable, null if not available 
     * @param source the source provider name 
     * @param target the target provider name 
     * @param properties any properties for the transform() call, null if none
     *
     * @return a message object that is transformed from the passed in <i>message</i>
     *
     * @throws Exception if unable to transform <i>message</i>
     */
    public abstract T transform(S message, 
                                boolean readOnly,
                                String charsetName, 
                                String source, 
                                String target,
                                Properties properties) 
                                throws Exception;

}
