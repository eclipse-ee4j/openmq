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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Logger;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import com.sun.messaging.DestinationConfiguration;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.io.JMQByteArrayOutputStream;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketFlag;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsclient.MessageImpl;
import com.sun.messaging.jmq.jmsclient.zip.Compressor;
import com.sun.messaging.jmq.jmsclient.zip.Decompressor;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.util.net.IPAddress;

//import com.sun.messaging.jmq.jmssclient.ConvertValue;

/**
 *  DirectPacket encapsulates the JMS Message and Sun MQ Packet for DIRECT Mode.
 *  <p>
 */
public class DirectPacket
        implements JMSPacket,
        javax.jms.Message,
        com.sun.messaging.jms.Message {

    /** The Sun MQ Packet that is associated with this DirectPacket */
    protected Packet pkt = null;

    /** The SysMessageID of this DirectPacket when it is received */
    private SysMessageID receivedSysMessageID = null;

    /** The Properties of the JMS Message */
    private Hashtable<String, Object> properties = null;

    /** The consumerId sent by the broker if this is a delivered JMS Message */
    private long consumerId = 0L;

    /** The JMS Destination of the JMS Message - usable via JMS API */
    private javax.jms.Destination jmsDestination = null;

    /** The JMS ReplyTo Destination of the JMS Message - usable via JMS API */
    private javax.jms.Destination jmsReplyTo = null;

    /** The JMS MessageID of the JMS Message - usable via JMS API */
    private String jmsMessageID = null;

    /**
     *  Indicates whether the client application has programatically set the
     *  JMS MessageID
     */
    private boolean jmsMessageIDSet = false;

    /** The flags controlling read-only mode for this JMS Message */
    private boolean readOnlyBody = false;
    private boolean readOnlyProperties = false;

    /** The DirectSession that is associated with this JMS Message  
     * This is only used with messages which have been received,
     * not for messages which are being sent 
     */
    private DirectSession ds = null;

    /** Flags whether this JMS Message is a QueueBrowser message or not */
    private boolean browserMessage = false;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectPacket";
    private static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSMessage =
            "javax.jms.Message.mqjmsra";
    protected static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    protected static transient final Logger _loggerJM =
            Logger.getLogger(_lgrNameJMSMessage);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_DM";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix+"1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix+"1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix+"2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix+"3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix+"4001: ";

    /** For optimized logging while messaging */
    protected static final int _logLevel;
    protected static final boolean _logFINE;

    protected final static String UTF8 = "UTF8";

    protected final static int DIRECT_PACKET_LOCAL_PORT = 1;

    private final static byte[] pktIPAddress;
    private final static byte[] pktMacAddress;
    
    protected boolean shouldCompress = false;

    protected int clientRetries = 0;

    public static final String JMS_SUN_COMPRESS = "JMS_SUN_COMPRESS";

    public static final String JMS_SUN_UNCOMPRESSED_SIZE =
        "JMS_SUN_UNCOMPRESSED_SIZE";

    public static final String JMS_SUN_COMPRESSED_SIZE =
        "JMS_SUN_COMPRESSED_SIZE";    
  
    private boolean enableZip = Boolean.getBoolean("imq.zip.enable");      

    static {                                                                        
//        _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
//        _loggerJM = Logger.getLogger(_lgrNameJMSMessage);
        pktMacAddress = 
            ((System.getProperty("imq.useMac", "true")).equalsIgnoreCase("true")
            ? IPAddress.getRandomMac()
            : null);
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
        }
        pktIPAddress = (addr == null ? null : addr.getAddress());

        java.util.logging.Level _level = _loggerJM.getLevel();
        int tmplevel = java.util.logging.Level.INFO.intValue();
        boolean tmplogfine = false;
        if (_level != null) {
            tmplevel = _level.intValue();
            if (tmplevel <= java.util.logging.Level.FINE.intValue()){
                tmplogfine = true;
            }
        }
        _logLevel = tmplevel;
        _logFINE = tmplogfine;
    }

    /** Create a new instance of DirectPacket - used by createMessage APIs */
    public DirectPacket(DirectSession ds)
    throws JMSException {
        if (_logFINE){
            Object params[] = new Object[2];
            params[0] = ds;
            _loggerOC.entering(_className, "constructor()", params);
        }
        //Use the default (i.e. using Direct ByteBuffer)
        this.ds = ds;
        this.pkt = new Packet();
        this._setDefaultValues();
    }

    /** Create a new instance of DirectPacket - used by Consumer.deliver */
    public DirectPacket(JMSPacket jmsPacket, long consumerId,
            DirectSession ds, JMSService jmsservice)
            throws JMSException {
        if (jmsPacket != null) {
            this.pkt = jmsPacket.getPacket();
            this.consumerId = consumerId;
            this.ds = ds;
            try {
                this.properties = (Hashtable<String, Object>)this.pkt.getProperties();
            } catch (Exception ex) {
                this.properties = null;
                ex.printStackTrace();
                String exerrmsg = _lgrMID_EXC + "DirectPacket:Constructor on deliver:"
                    + "Unable to get properties from JMSPacket.";
                JMSException jmse = new JMSException(exerrmsg);
                throw jmse;
            }
            this.setIntProperty(JMSService.JMSXProperties.JMSXDeliveryCount.toString(),
                                jmsPacket.getPacket().getDeliveryCount());
            this.readOnlyProperties = true;
            this.readOnlyBody = true;
        } else {
            String exerrmsg = _lgrMID_EXC + "DirectPacket:Construct on deliver:"
                    + "Failed due to invalid JMSPacket.";
            JMSException jmse = new JMSException(exerrmsg);
            throw jmse;
        }
    }

    /**
     * Factory to construct the right type of JMS Message from a received 
     * JMSPacket
     *
     *  If browserMessage is set to false, this is a message that is
     *  received by a consumer and receivedSysMessageIDwill be initialized
     *  from the Packet for use when acknowledging the message.
     *
     *  If browserMessage is set to true, them receivedSysMessageID is left as
     *  null
     */
    protected static final javax.jms.Message constructMessage(JMSPacket jmsPacket,
            long consumerId, DirectSession ds, JMSService jmsservice,
            boolean browserMessage)
    throws JMSException {
        javax.jms.Message jmsMsg = null;
        boolean valid = true;
        Throwable t = null;
        int pType = 0;
        try {
            pType = jmsPacket.getPacket().getPacketType();
            switch (pType) {
                case PacketType.MESSAGE:
                    jmsMsg = new DirectPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                case PacketType.BYTES_MESSAGE:
                    jmsMsg = new DirectBytesPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                case PacketType.MAP_MESSAGE:
                    jmsMsg = new DirectMapPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                case PacketType.OBJECT_MESSAGE:
                    jmsMsg = new DirectObjectPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                case PacketType.STREAM_MESSAGE:
                    jmsMsg = new DirectStreamPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                case PacketType.TEXT_MESSAGE:
                    jmsMsg = new DirectTextPacket(jmsPacket, consumerId,
                            ds, jmsservice);
                    break;
                default:
                    valid = false;
            }
        } catch (Exception e) {
            valid = false;
            t = e;
        }
        if (!valid) {
            String exerrmsg = _lgrMID_EXC + "DirectPacket:constructMessage():"+
                    "Failed on invalid " + "PacketType="+pType +
                    ((t != null) ? " due to Exception=" + t.getMessage() : "" )
                    + ".";
            JMSException jmse = new JMSException(exerrmsg);
            if (t != null) {
                jmse.initCause(t);
            }
            throw jmse;
        }
        //Finally set whether this is a Browser Message or not
        ((DirectPacket)jmsMsg).browserMessage = browserMessage;
        if (browserMessage != true) {
            ((DirectPacket)jmsMsg).setReceivedSysMessageID((SysMessageID)
                jmsPacket.getPacket().getSysMessageID().clone());
        }
        return jmsMsg;
    }

    /** Factory to construct our JMS Message from a foreign JMS Message */
    protected static final DirectPacket constructFromForeignMessage(
            JMSService jmsservice, DirectSession ds, 
            javax.jms.Message foreignMessage)
    throws JMSException {
        DirectPacket jmsMsg = null;
        boolean valid = true;
        Throwable t = null;

        if (foreignMessage instanceof javax.jms.TextMessage) {
            DirectTextPacket dtp = new DirectTextPacket(ds, 
                    ((javax.jms.TextMessage)foreignMessage).getText());
            jmsMsg = dtp;
        } else if (foreignMessage instanceof javax.jms.MapMessage) {
            DirectMapPacket dmp = new DirectMapPacket(ds);
            String tkey = null;
            Enumeration keys =
                    ((javax.jms.MapMessage)foreignMessage).getMapNames();
            while (keys.hasMoreElements()) {
                tkey = (String)keys.nextElement();
                dmp.setObject(tkey, 
                        ((javax.jms.MapMessage)foreignMessage).getObject(tkey));
            }
            jmsMsg = dmp;
        } else if (foreignMessage instanceof javax.jms.ObjectMessage) {
            DirectObjectPacket dop = new DirectObjectPacket(ds, 
                    ((javax.jms.ObjectMessage)foreignMessage).getObject());
            jmsMsg = dop;
        } else if (foreignMessage instanceof javax.jms.BytesMessage) {
            DirectBytesPacket dbp = new DirectBytesPacket(ds);
            ((javax.jms.BytesMessage)foreignMessage).reset();
            try {
                byte b;
                while (true) {
                    b = ((javax.jms.BytesMessage)foreignMessage).readByte();
                    dbp.writeByte(b);
                }
            } catch (javax.jms.MessageEOFException meofe){
                //ok - since the read to eof will end here
            } catch (Exception e){
                String exerrmsg = _lgrMID_EXC + "DirectPacket:+" +
                        "constructFromForeignMessage():"+
                        "Failed on converting foreign BytesMessage" +
                        " due to Exception=" + e.getMessage();
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(e);
                throw jmse;
            }
            jmsMsg = dbp;
        } else if (foreignMessage instanceof javax.jms.StreamMessage) {
            DirectStreamPacket dsp = new DirectStreamPacket(ds);
            ((javax.jms.StreamMessage)foreignMessage).reset();
            Object obj = null;
            try{
                while (true) {
                    obj = ((javax.jms.StreamMessage)foreignMessage).readObject();
                    dsp.writeObject(obj);
                }
            } catch (javax.jms.MessageEOFException meofe){
                //ok - since the read to eof will end here
            } catch (Exception e){
                String exerrmsg = _lgrMID_EXC + "DirectPacket:+" +
                        "constructFromForeignMessage():"+
                        "Failed on converting foreign StreamMessage" +
                        " due to Exception=" + e.getMessage();
                JMSException jmse = new JMSException(exerrmsg);
                jmse.initCause(e);
                throw jmse;
            }
            jmsMsg = dsp;
        } else {
            DirectPacket dp = new DirectPacket(ds);
            jmsMsg = dp;
        }
        if (jmsMsg != null) {
            //Assign JMS Headers from the foreignMessage to our JMS Message
            //that need to be the same as what was set by the application
            jmsMsg.setJMSCorrelationID(foreignMessage.getJMSCorrelationID());
            jmsMsg.setJMSType(foreignMessage.getJMSType());
            //JMS Headers that ned to be set after the send operation
            //are
            //JMSDeliveryMode
            //JMSExpiration
            //JMSDeliveryTime
            //JMSPriority
            //JMSTimestamp
            //JMSMessageID
            //JMSDestination
            //
            
            //The JMSReplyTo JMS Header is specifically excluded from having
            //to be handled for a foreign JMS Message
            //
            
            //Finally, assign JMS Message Properties from the foreignMessage
            //to our JMS Message
            Enumeration keys = foreignMessage.getPropertyNames();
            String tkey = null;
            Object tvalue= null;
            while (keys.hasMoreElements()) {
                tkey = (String)keys.nextElement();
                tvalue = foreignMessage.getObjectProperty(tkey);
                jmsMsg.setObjectProperty(tkey, tvalue);
            }
        }
        return jmsMsg;
    }

    /** Method to update the foreign JMS Message after it is used in a send */
    protected static final void updateForeignMessageAfterSend(
            DirectPacket jmsPacket, javax.jms.Message foreignMessage)
    throws JMSException {
        foreignMessage.setJMSDeliveryMode(jmsPacket.getJMSDeliveryMode());
        foreignMessage.setJMSExpiration(jmsPacket.getJMSExpiration());
	Method m = null;
	try {
            Class c = foreignMessage.getClass();
            m = c.getMethod("getJMSDeliveryTime", (Class[])null);
            if (Modifier.isAbstract(m.getModifiers())) {
		m = null;
            }
	} catch (NoSuchMethodException e) {
            m = null;
	}
        if (m != null && jmsPacket.getJMSDeliveryTime() != 0L) {
            foreignMessage.setJMSDeliveryTime(jmsPacket.getJMSDeliveryTime());
	}
        foreignMessage.setJMSPriority(jmsPacket.getJMSPriority());
        foreignMessage.setJMSTimestamp(jmsPacket.getJMSTimestamp());
        foreignMessage.setJMSMessageID(jmsPacket.getJMSMessageID());
        //The JMS Destination will be set to be that which has a name
        //in our JMS provider and it may not make any sense in the
        //foreign JMS provider
        foreignMessage.setJMSDestination(jmsPacket.getJMSDestination());
        //The JMSReplyTo JMS Header is excluded from having to be handled
        //when a foreign JMS Message is used.
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement com.sun.messaging.jmq.jmsservice.JMSPacket
    /////////////////////////////////////////////////////////////////////////
    public Packet getPacket(){
        return this.pkt;
    }

    public Message getMessage() {
        return (javax.jms.Message)this;
    }

    protected SysMessageID getReceivedSysMessageID() {
        return receivedSysMessageID;
    }

    protected void setReceivedSysMessageID(SysMessageID sysMsgId) {
        this.receivedSysMessageID = sysMsgId;
    }
    /////////////////////////////////////////////////////////////////////////
    //  end com.sun.messaging.jmq.jmsservice.JMSPacket
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.Message
    /////////////////////////////////////////////////////////////////////////
    /** 
     *  Acknowledge this and all previous messages received.
     *
     *  <P>All JMS messages support the acknowledge() method for use when a
     *  client has specified that a JMS consumers messages are to be
     *  explicitly acknowledged.
     *
     *  <P>JMS defaults to implicit message acknowledgement. In this mode,
     *  calls to acknowledge() are ignored.
     *
     *  <P>Acknowledgment of a message automatically acknowledges all
     *  messages previously received by the session. Clients may
     *  individually acknowledge messages or they may choose to acknowledge
     *  messages in application defined groups (which is done by acknowledging
     *  the last received message in the group).
     *
     *  <P>Messages that have been received but not acknowledged may be
     *  redelivered to the consumer.
     *
     *  @throws JMSException if JMS fails to acknowledge due to some
     *          internal JMS error.
     *  @throws IllegalStateException if this method is called on a closed
     *          session.
     */
    public void acknowledge()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"acknowledge()");
        }
        if (this.browserMessage) {
            return;
        }
    }

    /** 
     *  Clear out the message body. Clearing a message's body does not clear
     *  its header values or property entries.
     *
     *  <P>If this message body was read-only, calling this method leaves
     *  the message body is in the same state as an empty body in a newly
     *  created message.
     *
     *
     *  @throws JMSException if JMS fails to due to some internal JMS error.
     */
     public void clearBody()
     throws JMSException {
         if (_logFINE){
             _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"clearBody()");
         }
         //clear out the body of the underlying packet
         //sub classes must call this as well as clear out message level data
         this.pkt.clearMessageBody();
         this._setReadOnlyBody(false);
     }
    
    /**
     *  Clear a message's properties.
     *
     *  <P>The message's header fields and body are not cleared.
     *
     *  @throws JMSException if the JMS provider fails to clear the message 
     *          properties due to some internal error.
     */
    public void clearProperties()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"clearProperties()");
        }
        this._setReadOnlyProperties(false);
        if (this.properties != null){
            this.properties.clear();
        }
        
        //set shouldCompress compress flag to false.
        shouldCompress = false;
    }

    /**
     *  Return the value of the <CODE>boolean</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>boolean</CODE> property
     *  
     *  @return the <CODE>boolean</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public boolean getBooleanProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getBooleanProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toBoolean(obj);
    }

    /**
     *  Return the value of the <CODE>byte</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>byte</CODE> property
     *  
     *  @return the <CODE>byte</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public byte getByteProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getByteProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
                
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toByte(obj);
    }

    /**
     *  Return the value of the <CODE>double</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>double</CODE> property
     *  
     *  @return the <CODE>double</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public double getDoubleProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getDoubleProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toDouble(obj);
    }

    /**
     *  Return the value of the <CODE>float</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>float</CODE> property
     *  
     *  @return the <CODE>float</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public float getFloatProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getFloatProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toFloat(obj);
    }

    /**
     *  Return the value of the <CODE>int</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>int</CODE> property
     *  
     *  @return the <CODE>int</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public int getIntProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getIntProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toInt(obj);
    }

    /**
     *  Get the correlation ID for the message.
     *  
     *  <P>This method is used to return correlation ID values that are 
     *  either provider-specific message IDs or application-specific 
     *  <CODE>String</CODE> values.
     *
     *  @return The correlation ID of a message as a <CODE>String</CODE>
     *
     *  @throws JMSException if the JMS provider fails to get the correlation
     *                         ID due to some internal error.
     *
     *  @see javax.jms.Message#setJMSCorrelationID(String)
     *  @see javax.jms.Message#getJMSCorrelationIDAsBytes()
     *  @see javax.jms.Message#setJMSCorrelationIDAsBytes(byte[])
     */ 
    public String getJMSCorrelationID()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSCorrelationID()");
        }
        return pkt.getCorrelationID();
    }

    /**
     *  Gets the correlation ID as an array of bytes for the message.
     *  
     *  <P>The use of a <CODE>byte[]</CODE> value for 
     *  <CODE>JMSCorrelationID</CODE> is non-portable.
     *
     *  @return the correlation ID of a message as an array of bytes
     *
     *  @throws JMSException if the JMS provider fails to get the correlation
     *          ID due to some internal error.
     *  
     *  @see javax.jms.Message#setJMSCorrelationID(String)
     *  @see javax.jms.Message#getJMSCorrelationID()
     *  @see javax.jms.Message#setJMSCorrelationIDAsBytes(byte[])
     */
    public byte [] getJMSCorrelationIDAsBytes()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSCorrelationIDAsBytes()");
        }
        byte [] bytes = null;
        try {
            bytes = pkt.getCorrelationID().getBytes(UTF8);
        } catch (Exception e) {
            String exerrmsg = _lgrMID_EXC + "getJMSCorrelationIDAsBytes()" +
                    " failed "/*for connectionId:"+ connectionId*/ +
                    ":due to " + e.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            _loggerJM.severe(exerrmsg);
            jmse.initCause(e);
            throw jmse;
        }
        return bytes;
    }

    /**
     *  Get the <CODE>DeliveryMode</CODE> value specified for this message.
     *  
     *  @return the delivery mode for this message
     *  
     *  @throws JMSException if the JMS provider fails to get the 
     *          delivery mode due to some internal error.
     *  
     * @see javax.jms.Message#setJMSDeliveryMode(int)
     * @see javax.jms.DeliveryMode
     */
    public int getJMSDeliveryMode()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+
                    /*"messageId="+messageId+":"+*/"getJMSDeliveryMode()"+
                    "="+( pkt.getPersistent()
                    ? DeliveryMode.PERSISTENT
                    : DeliveryMode.NON_PERSISTENT ));
        }
        if (pkt.getPersistent()) {
            return DeliveryMode.PERSISTENT;
        } else {
            return DeliveryMode.NON_PERSISTENT;
        }
    }

    /**
     *  Get the <CODE>Destination</CODE> object for this message.
     *  
     *  <P>The <CODE>JMSDestination</CODE> header field contains the 
     *  destination to which the message is being sent.
     *  
     *  <P>When a message is sent, this field is ignored. After completion
     *  of the <CODE>send</CODE> or <CODE>publish</CODE> method, the field 
     *  holds the destination specified by the method.
     *  
     *  <P>When a message is received, its <CODE>JMSDestination</CODE> value 
     *  must be equivalent to the value assigned when it was sent.
     *
     *  @return the destination of this message
     *  
     *  @throws JMSException if the JMS provider fails to get the destination
     *                         due to some internal error.
     *  
     *  @see javax.jms.Message#setJMSDestination(Destination)
     */
    public Destination getJMSDestination()
            throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF +
                    /*"messageId="+messageId+":"+*/"getJMSDestination()"
                    );
        }
        String destName = null;
        String className = null;

        if (this.jmsDestination == null) {
            //Initialize when accessed for the first time
            //if (destination == null && messageID != null),
            //this is a received message.
            //we need to construct the dest object if this is a received
            //message and when accessed for the first time.
            if ((this.getReceivedSysMessageID() != null) || browserMessage) {
                try {
                    destName = this.pkt.getDestination();
                    className = this.pkt.getDestinationClass();
                    //instantiate destination object
                    this.jmsDestination = (com.sun.messaging.Destination)
                            Class.forName(className).newInstance();
                    //set destination name
                    ((com.sun.messaging.Destination)
                    this.jmsDestination).setProperty(
                            DestinationConfiguration.imqDestinationName,
                            destName);
                } catch (Exception e) {
                    //e.printStackTrace();
                    //if there is a problem, we create a default one
                    this.jmsDestination = this._constructMQDestination(destName);
                }
            }
        }
        return this.jmsDestination;
    }

    /**
     *  Get the message's expiration value.
     *  
     *  <P>When a message is sent, the <CODE>JMSExpiration</CODE> header field 
     *  is left unassigned. After completion of the <CODE>send</CODE> or 
     *  <CODE>publish</CODE> method, it holds the expiration time of the
     *  message. This is the sum of the time-to-live value specified by the
     *  client and the GMT at the time of the <CODE>send</CODE> or 
     *  <CODE>publish</CODE>.
     *
     *  <P>If the time-to-live is specified as zero, <CODE>JMSExpiration</CODE> 
     *  is set to zero to indicate that the message does not expire.
     *
     *  <P>When a message's expiration time is reached, a provider should
     *  discard it. The JMS API does not define any form of notification of 
     *  message expiration.
     *
     *  <P>Clients should not receive messages that have expired; however,
     *  the JMS API does not guarantee that this will not happen.
     *
     *  @return the time the message expires, which is the sum of the
     *  time-to-live value specified by the client and the GMT at the
     *  time of the send
     *  
     *  @throws JMSException if the JMS provider fails to get the message 
     *                         expiration due to some internal error.
     *
     *  @see javax.jms.Message#setJMSExpiration(long)
     */
    public long getJMSExpiration()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSExpiration()");
        }
        return this.pkt.getExpiration();
    }

    /** Sets the message's delivery time value.
     *
     * <P>This method is for use by JMS providers only to set this field
     * when a message is sent. This message cannot be used by clients
     * to configure the delivery time of the message. This method is public
     * to allow one JMS provider to set this field when sending a message
     * whose implementation is not its own.
     *
     * @param expiration the message's delivery time value
     *
     * @exception JMSException if the JMS provider fails to set the delivery
     *			       time due to some internal error.
     *
     * @see javax.jms.Message#getJMSDeliveryTime()
     *
     * @since 2.0
    */
    public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSDeliveryTime()" + deliveryTime);
        }
        this.pkt.setDeliveryTime(deliveryTime);
    }

    /** Gets the message's delivery time value.
     *
     * <P>When a message is sent, the <CODE>JMSDeliveryTime</CODE> header field
     * is left unassigned. After completion of the <CODE>send</CODE> or
     * <CODE>publish</CODE> method, it holds the delivery time of the
     * message. This is the sum of the deliveryDelay value specified by the
     * client and the GMT at the time of the <CODE>send</CODE> or
     * <CODE>publish</CODE>.
     *
     * <P>A message's delivery time is the earliest time when a provider may
     * make the message visible on the target destination and available for
     * delivery to consumers.
     *
     * <P>Clients must not receive messages before the delivery time has been reached.
     *
     * @return the message's delivery time, which is the sum of the deliveryDelay
     * value specified by the client and the GMT at the time of the <CODE>send</CODE> or
     * <CODE>publish</CODE>.
     *
     * @exception JMSException if the JMS provider fails to get the message
     *			       expiration due to some internal error.
     *
     * @see javax.jms.Message#setJMSDeliveryTime(long)
     *
     * @since 2.0
     */
    public long getJMSDeliveryTime() throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSJMSDeliveryTime()");
        }
        return this.pkt.getDeliveryTime();
    }

    /**
     *  Get the message ID.
     *
     *  <P>The <CODE>JMSMessageID</CODE> header field contains a value that 
     *  uniquely identifies each message sent by a provider.
     * 
     *  <P>When a message is sent, <CODE>JMSMessageID</CODE> can be ignored. 
     *  When the <CODE>send</CODE> or <CODE>publish</CODE> method returns, it 
     *  contains a provider-assigned value.
     *
     *  <P>A <CODE>JMSMessageID</CODE> is a <CODE>String</CODE> value that 
     *  should function as a 
     *  unique key for identifying messages in a historical repository. 
     *  The exact scope of uniqueness is provider-defined. It should at 
     *  least cover all messages for a specific installation of a 
     *  provider, where an installation is some connected set of message 
     *  routers.
     *
     *  <P>All <CODE>JMSMessageID</CODE> values must start with the prefix 
     *  <CODE>'ID:'</CODE>. 
     *  Uniqueness of message ID values across different providers is 
     *  not required.
     *
     *  <P>Since message IDs take some effort to create and increase a
     *  message's size, some JMS providers may be able to optimize message
     *  overhead if they are given a hint that the message ID is not used by
     *  an application. By calling the 
     *  <CODE>MessageProducer.setDisableMessageID</CODE> method, a JMS client 
     *  enables this potential optimization for all messages sent by that 
     *  message producer. If the JMS provider accepts this
     *  hint, these messages must have the message ID set to null; if the 
     *  provider ignores the hint, the message ID must be set to its normal 
     *  unique value.
     *
     *  @return the message ID
     *
     *  @throws JMSException if the JMS provider fails to get the message ID 
     *          due to some internal error.
     *  @see javax.jms.Message#setJMSMessageID(String)
     *  @see javax.jms.MessageProducer#setDisableMessageID(boolean)
     */
    public String getJMSMessageID()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSMessageID()");
        }
        if (this.jmsMessageIDSet) {
            return this.jmsMessageID;
        } else {
            return "ID:" + this.pkt.getMessageID();
        }
    }

    /**
     *  Get the message priority level.
     *  
     *  <P>The JMS API defines ten levels of priority value, with 0 as the 
     *  lowest
     *  priority and 9 as the highest. In addition, clients should consider
     *  priorities 0-4 as gradations of normal priority and priorities 5-9
     *  as gradations of expedited priority.
     *  
     *  <P>The JMS API does not require that a provider strictly implement 
     *  priority 
     *  ordering of messages; however, it should do its best to deliver 
     *  expedited messages ahead of normal messages.
     *  
     *  @return the default message priority
     *  
     *  @throws JMSException if the JMS provider fails to get the message 
     *          priority due to some internal error.
     *
     *  @see javax.jms.Message#setJMSPriority(int) 
     */
    public int getJMSPriority()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSPriority()");
        }
        return this.pkt.getPriority();
    }

    /**
     *  Get an indication of whether this message is being redelivered.
     *
     *  <P>If a client receives a message with the <CODE>JMSRedelivered</CODE> 
     *  field set,
     *  it is likely, but not guaranteed, that this message was delivered
     *  earlier but that its receipt was not acknowledged
     *  at that time.
     *
     *  @return true if this message is being redelivered
     *  
     *  @throws JMSException if the JMS provider fails to get the redelivered
     *          state due to some internal error.
     *
     *  @see javax.jms.Message#setJMSRedelivered(boolean)
     */
    public boolean getJMSRedelivered()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSRedelivered()");
        }
        return this.pkt.getRedelivered();
    }

    /**
     *  Get the <CODE>Destination</CODE> object to which a reply to this 
     *  message should be sent.
     *  
     *  @return <CODE>Destination</CODE> to which to send a response to this 
     *          message
     *
     *  @throws JMSException if the JMS provider fails to get the  
     *          <CODE>JMSReplyTo</CODE> destination due to some 
     *          internal error.
     *
     *  @see javax.jms.Message#setJMSReplyTo(Destination)
     */
    public Destination getJMSReplyTo()
    throws JMSException{
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSReplyTo()");
        }
        String destName = null;
        String className = null;

        if (this.jmsReplyTo == null) {
            //construct new reply to only for a received msg
            if ((this.getReceivedSysMessageID() != null) || browserMessage) {
                //if not set, return null
                if (this.pkt.getReplyTo() == null) {
                    return null;
                }
                //construct dest obj based on bits set in the pkt
                try {
                    destName = this.pkt.getReplyTo();
                    className = this.pkt.getReplyToClass();
                    //instantiate replyTo destination obj
                    this.jmsReplyTo = (com.sun.messaging.Destination)
                        Class.forName(className).newInstance();
                    //set the destination name
                    ((com.sun.messaging.Destination)
                        this.jmsReplyTo).setProperty(
                            DestinationConfiguration.imqDestinationName,
                            destName);
                } catch (Exception e) {
                    //e.printStackTrace();
                    //if there is a problem, we create a default one
                    this.jmsReplyTo = this._constructMQDestination(destName);
                }
            }
        }
        return this.jmsReplyTo;
    }

    /**
     *  Get the message timestamp.
     *  
     *  <P>The <CODE>JMSTimestamp</CODE> header field contains the time a 
     *  message was 
     *  handed off to a provider to be sent. It is not the time the 
     *  message was actually transmitted, because the actual send may occur 
     *  later due to transactions or other client-side queueing of messages.
     *
     *  <P>When a message is sent, <CODE>JMSTimestamp</CODE> is ignored. When 
     *  the <CODE>send</CODE> or <CODE>publish</CODE>
     *  method returns, it contains a time value somewhere in the interval 
     *  between the call and the return. The value is in the format of a normal 
     *  millis time value in the Java programming language.
     *
     *  <P>Since timestamps take some effort to create and increase a 
     *  message's size, some JMS providers may be able to optimize message 
     *  overhead if they are given a hint that the timestamp is not used by an 
     *  application. By calling the
     *  <CODE>MessageProducer.setDisableMessageTimestamp</CODE> method, a JMS 
     *  client enables this potential optimization for all messages sent by 
     *  that message producer. If the JMS provider accepts this
     *  hint, these messages must have the timestamp set to zero; if the 
     *  provider ignores the hint, the timestamp must be set to its normal 
     *  value.
     *
     *  @return the message timestamp
     *
     *  @throws JMSException if the JMS provider fails to get the timestamp
     *          due to some internal error.
     *
     *  @see javax.jms.Message#setJMSTimestamp(long)
     *  @see javax.jms.MessageProducer#setDisableMessageTimestamp(boolean)
     */
    public long getJMSTimestamp()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSTimestamp()");
        }
        return this.pkt.getTimestamp();
    }

    /**
     *  Get the message type identifier supplied by the client when the
     *  message was sent.
     *
     *  @return the message type
     *  
     *  @throws JMSException if the JMS provider fails to get the message 
     *          type due to some internal error.
     *
     *  @see javax.jms.Message#setJMSType(String)
     */
    public String getJMSType()
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"getJMSType()");
        }
        return this.pkt.getMessageType();
    }

    /**
     *  Return the value of the <CODE>long</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>long</CODE> property
     *  
     *  @return the <CODE>long</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public long getLongProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getLongProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toLong(obj);
    }

    /**
     *  Return the value of the Java object property with the specified name.
     *  
     *  <P>This method can be used to return, in objectified format,
     *  an object that has been stored as a property in the message with the 
     *  equivalent <CODE>setObjectProperty</CODE> method call, or its equivalent
     *  primitive <CODE>set<I>type</I>Property</CODE> method.
     *  
     *  @param name the name of the Java object property
     *  
     *  @return the Java object property value with the specified name, in 
     *  objectified format (for example, if the property was set as an 
     *  <CODE>int</CODE>, an <CODE>Integer</CODE> is 
     *  returned); if there is no property by this name, a null value 
     *  is returned
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     */
    public Object getObjectProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getObjectProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        //String and Object properties return null if the value doesn't exist
        if (this.properties == null) {
            return null;
        }
        return this.properties.get(name);
    }

    /** 
     *  Return an <CODE>Enumeration</CODE> of all the property names.
     *
     *  <P>Note that JMS standard header fields are not considered
     *  properties and are not returned in this enumeration.
     *  
     *  @return an enumeration of all the names of property values
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          names due to some internal error.
     */
    public Enumeration getPropertyNames()
    throws JMSException{
        if (_logFINE){
            String methodName = "getPropertyNames()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        if (this.properties == null) {
            //Returns empty Enumeration
            this.properties = new Hashtable<String, Object>();
        }
        return this.properties.keys();
    }

    /**
     *  Return the value of the <CODE>short</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>short</CODE> property
     *  
     *  @return the <CODE>short</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public short getShortProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getShortProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        Object obj = null;
        if (this.properties != null) {
            obj = this.properties.get(name);
        }
        return ConvertValue.toShort(obj);
    }

    /**
     *  Return the value of the <CODE>String</CODE> property with the  
     *  specified name.
     *  
     *  @param name the name of the <CODE>String</CODE> property
     *  
     *  @return the <CODE>String</CODE> property value for the specified name
     *  
     *  @throws JMSException if the JMS provider fails to get the property
     *          value due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid. 
     */
    public String getStringProperty(String name)
    throws JMSException {
        if (_logFINE){
            String methodName = "getStringProperty()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        //String and Object properties return null if the value doesn't exist
        Object obj;
        if ((this.properties == null) ||
                ((obj = properties.get(name)) == null)) {
            return null;
        }
        return ConvertValue.toString(obj);
    }

    /**
     *  Indicate whether a property value exists.
     *
     *  @param name the name of the property to test
     *
     *  @return true if the property exists
     *  
     *  @throws JMSException if the JMS provider fails to determine if the 
     *          property exists due to some internal error.
     */
    public boolean propertyExists(String name)
    throws JMSException{
        if (_logFINE){
            String methodName = "propertyExists()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + name);
        }
        if (this.properties == null) {
            return false;
        }
        try {
            if (this.properties.containsKey(name)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            String exerrmsg = _lgrMID_EXC + "propertyExists()" +
                    " failed "/*for connectionId:"+ connectionId*/ +
                    ":due to " + e.getMessage();
            JMSException jmse = new JMSException(exerrmsg);
            _loggerJM.severe(exerrmsg);
            jmse.initCause(e);
            throw jmse;
        }
    }

    /**
     *  Set a <CODE>boolean</CODE> property value with the specified name into 
     *  the message.
     *
     *  @param name the name of the <CODE>boolean</CODE> property
     *  @param value the <CODE>boolean</CODE> property value to set
     *  
     *  @throws JMSException if the JMS provider fails to set the property
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if properties are read-only
     */
    public void setBooleanProperty(String name, boolean value)
    throws JMSException {
        this._checkAndSetProperty("setBooleanProperty()", name, value);
        
        if (JMS_SUN_COMPRESS.equals(name)) {
            shouldCompress = value;
        }
    }

    public void setByteProperty(String name, byte value)
    throws JMSException {
        this._checkAndSetProperty("setByteProperty()", name, value);
    }

    public void setDoubleProperty(String name, double value)
    throws JMSException {
        this._checkAndSetProperty("setDoubleProperty()", name, value);
    }

    public void setFloatProperty(String name, float value)
    throws JMSException {
        this._checkAndSetProperty("setFloatProperty()", name, value);
    }

    public void setIntProperty(String name, int value)
    throws JMSException {
        this._checkAndSetProperty("setIntProperty()", name, value);
    }

    public void setJMSCorrelationID(String correlationID)
    throws JMSException {
        _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                "setJMSCorrelationID()"+correlationID);
        this.pkt.setCorrelationID(correlationID);
    }

    public void setJMSCorrelationIDAsBytes(byte [] correlationID)
    throws JMSException {
        if (_logFINE){
            String methodName = "setJMSCorrelationIDAsBytes()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + Arrays.toString(correlationID));
        }
        try {
            this.pkt.setCorrelationID(new String(correlationID, UTF8));
        } catch (Exception e) {
            String methodName = "setJMSCorrelationIDAsBytes()";
            String errMsg = _lgrMID_EXC + methodName +
                    ":Caught Exception:"+methodName+":"+e.getMessage();
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(e);
            _loggerJM.severe(errMsg);
            throw jmse;
        }
    }

    public void setJMSDeliveryMode(int deliveryMode)
    throws JMSException {
        if (_logFINE){
            String methodName = "setJMSDeliveryMode()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + ":" + deliveryMode);
        }
        if (deliveryMode == javax.jms.DeliveryMode.PERSISTENT) {
            this.pkt.setPersistent(true);
        } else if (deliveryMode == javax.jms.DeliveryMode.NON_PERSISTENT){
            this.pkt.setPersistent(false);
        } else {
            String methodName = "setJMSDeliveryMode()";
            String errMsg = _lgrMID_EXC + methodName +
                    ":Invalid deliveryMode="+ deliveryMode;
            _loggerJM.severe(errMsg);
            throw new JMSException(errMsg);
        }
    }

    public void setJMSDestination(Destination destination)
            throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSDestination()" + destination);
        }
        this.jmsDestination = destination;
    }

    public void setJMSExpiration(long expiration)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSExpiration()" + expiration);
        }
        this.pkt.setExpiration(expiration);
    }

    public void setJMSMessageID(String id)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSMessageID()" + id);
        }
        this.jmsMessageIDSet = true;
        this.jmsMessageID = id;
    }

    public void setJMSPriority(int priority)
    throws JMSException {
        if (_logFINE){
            String methodName = "setJMSPriority()";
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName + priority);
        }
        if (priority < 0 || priority > 9) {
            String methodName = "setJMSPriority()";
            String errMsg = _lgrMID_EXC + methodName +
                    ":Invalid priority="+ priority;
            _loggerJM.severe(errMsg);
            throw new JMSException(errMsg);
        }
        this.pkt.setPriority(priority);
    }

    public void setJMSRedelivered(boolean redelivered)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSRedelivered()" + redelivered);
        }
        this.pkt.setRedelivered(redelivered);
    }

    public void setJMSReplyTo(Destination replyTo)
    throws JMSException{
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSReplyTo()" + replyTo);
        }
        this.jmsReplyTo = replyTo;
    }

    public void setJMSTimestamp(long timestamp)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSTimestamp()" + timestamp);
        }
    }

    public void setJMSType(String type)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    "setJMSType()" + type);
        }
        this.pkt.setMessageType(type);
    }

    public void setLongProperty(String name, long value)
    throws JMSException {
        //Note auto-boxing usage
        this._checkAndSetProperty("setLongProperty()", name, value);
    }

    public void setObjectProperty(String name, Object value)
    throws JMSException {
        this._checkAndSetProperty("setObjectProperty()", name, value);
    }

    public void setShortProperty(String name, short value)
    throws JMSException {
        this._checkAndSetProperty("setShortProperty()", name, value);
    }

    public void setStringProperty(String name, String value)
    throws JMSException {
        this._checkAndSetProperty("setStringProperty()", name, value);
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.Message
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement com.sun.messaging.jms.Message
    /////////////////////////////////////////////////////////////////////////
    /** 
     *  Acknowledges this consumed message only.
     *  
     * <P>All consumed JMS messages in Oracle GlassFish(tm) Server Message Queue support the
     * <CODE>acknowledgeThisMessage</CODE> 
     * method for use when a client has specified that its JMS session's 
     * consumed messages are to be explicitly acknowledged.  By invoking 
     * <CODE>acknowledgeThisMessage</CODE> on a consumed message, a client
     * acknowledges only the specific message that the method is invoked on.
     * 
     * <P>Calls to <CODE>acknowledgeThisMessage</CODE> are ignored for both transacted 
     * sessions and sessions specified to use implicit acknowledgement modes.
     *
     * @exception javax.jms.JMSException if the messages fail to get
     *            acknowledged due to an internal error.
     * @exception javax.jms.IllegalStateException if this method is called
     *            on a closed session.
     *
     * @see javax.jms.Session#CLIENT_ACKNOWLEDGE
     * @see javax.jms.Message#acknowledge() javax.jms.Message.acknowledge()
     * @see com.sun.messaging.jms.Message#acknowledgeUpThroughThisMessage()
     */ 
    public void acknowledgeThisMessage()
    throws javax.jms.JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF + /*"messageId="+messageId+":"+*/
                    "acknowledgeThisMessage()");
        }
        //This must be a received message and can not be via a QueueBrowser
        if (!this.browserMessage){
            this.ds._acknowledgeThisMessage(this, this.consumerId,
                    JMSService.MessageAckType.ACKNOWLEDGE);
        }
        //If it is not a received message this is just a NOP
    }

    /** 
     *  Acknowledge consumed messages of the session up through
     *  and including this consumed message.
     *  
     *  <P>All consumed JMS messages in Oracle GlassFish(tm) Server Message Queue support the
     *  <CODE>acknowledgeUpThroughThisMessage</CODE> 
     *  method for use when a client has specified that its JMS session's 
     *  consumed messages are to be explicitly acknowledged.  By invoking 
     *  <CODE>acknowledgeUpThroughThisMessage</CODE> on a consumed message,
     *  a client acknowledges messages starting with the first
     *  unacknowledged message and ending with this message that
     *  were consumed by the session that this message was delivered to.
     * 
     *  <P>Calls to <CODE>acknowledgeUpThroughThisMessage</CODE> are
     *  ignored for both transacted sessions and sessions specified
     *  to use implicit acknowledgement modes.
     *
     *  @throws javax.jms.JMSException if the messages fail to get
     *          acknowledged due to an internal error.
     *  @throws javax.jms.IllegalStateException if this method is called
     *          on a closed session.
     *
     *  @see javax.jms.Session#CLIENT_ACKNOWLEDGE
     *  @see javax.jms.Message#acknowledge() javax.jms.Message.acknowledge()
     *  @see com.sun.messaging.jms.Message#acknowledgeThisMessage()
     */ 
    public void acknowledgeUpThroughThisMessage()
    throws javax.jms.JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/"acknowledge()");
        }
        this._unsupported("acknowledgeUpThroughThisMessage");
        //This must be a received message and cannot be via a QueueBrowser
        if (this.consumerId != 0){
            //XXX:tharakan:Temp-wire this into the above initially
            this.ds._acknowledgeThisMessage(this, this.consumerId,
                    JMSService.MessageAckType.ACKNOWLEDGE);
        }
        //If this is not a received message, this is just a NOP
    }
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods DirectPacket / javax.jms.Message
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Set the JMS default values on this JMS Message
     */
    protected void _setDefaultValues()
    throws JMSException {
        //Set the JMS Spec defaults
        this.setJMSDeliveryMode(javax.jms.DeliveryMode.PERSISTENT);
        this.setJMSPriority(javax.jms.Message.DEFAULT_PRIORITY);
        this.setJMSExpiration(javax.jms.Message.DEFAULT_TIME_TO_LIVE);
        this.setJMSDeliveryTime(javax.jms.Message.DEFAULT_DELIVERY_DELAY);
        //Set the Sun MQ defaults for this originator
        if (pktMacAddress == null) {
            this.pkt.setIP(pktIPAddress);
        } else {
            this.pkt.setIP(pktIPAddress, pktMacAddress);
        }
        this.pkt.setPort(this.DIRECT_PACKET_LOCAL_PORT);
        //The PacketType will be overridden by sub classes
        this.pkt.setPacketType(PacketType.MESSAGE);
    }

    /**
     *  Set the readOnlyBody flag for this Message
     */
    protected void _setReadOnlyBody(boolean flag) {
        this.readOnlyBody = flag;
    }

    /**
     *  Set the readOnlyProperties flag for this Message
     */
    protected void _setReadOnlyProperties(boolean flag) {
        this.readOnlyProperties = flag;
    }

    /**
     *  Reset the jmsMessageIDSet flag
     */
    protected void _resetJMSMessageID() {
        this.jmsMessageIDSet = false;
    }

    /**
     *  Construct an MQ Destination object.
     */
    private Destination _constructMQDestination(String destName)
    throws JMSException {
        //construct destination obj based on bits set in the pkt
        Destination dest = null;
        boolean isQ = pkt.getIsQueue();
        if ((destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX
                + ClientConstants.TEMPORARY_QUEUE_URI_NAME)) ||
                destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX
                + com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE)) {
            dest = new TemporaryQueue(destName);
            return dest;
        }
        if ((destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX
                + ClientConstants.TEMPORARY_TOPIC_URI_NAME)) ||
                destName.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX
                + com.sun.messaging.jmq.jmsservice.Destination.Type.TOPIC)) {
            dest = new TemporaryTopic(destName);
            return dest;
        }
        if (isQ) {
            dest = new com.sun.messaging.BasicQueue(destName);
        } else {
            dest = new com.sun.messaging.BasicTopic(destName);
        }
        return dest;
    }

    /**
     *  Check and set a property on the JMS Message
     */
    protected void _checkAndSetProperty(String methodName, String name,
            Object value)
    throws JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                methodName + ":name=" + name + ":value=" + value);
        }
        if (name == null || "".equals(name)) {
            String errMsg = _lgrMID_EXC + methodName +
                    ":name=" + name + ":value=" + value +
                    ":IllegalArgument.";
            _loggerJM.severe(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
        if (this.readOnlyProperties) {
            String errMsg = _lgrMID_EXC + methodName +
                    ":name=" + name + ":value=" + value +
                    ":Properties are Read Only.";
            _loggerJM.severe(errMsg);
            throw new javax.jms.MessageNotWriteableException(errMsg);
        }
        //Verify valid Object type of value
        if (value instanceof Boolean || value instanceof Byte ||
                value instanceof Short || value instanceof Integer ||
                value instanceof Long || value instanceof Float ||
                value instanceof Double || value instanceof String) {
            //valid object type - 
        } else {
        	String errMsg;
        	if (value==null) {
                errMsg = _lgrMID_EXC + methodName +
                ":name=" + name + 
                ":Bad property value: null";
        	} else {
                errMsg = _lgrMID_EXC + methodName +
                ":name=" + name + ":value=" + value +
                ":Bad type for property value=" + value.getClass();
        	}

            _loggerJM.severe(errMsg);
            throw new javax.jms.MessageFormatException(errMsg);
        }
        this._checkValidPropertyName(methodName, name);
        if (this.properties == null) {
            this.properties = new Hashtable<String, Object>();
        }
        this.properties.put(name, value);
    }

    /**
     *  Check the validity of the property name being used
     */
    protected void _checkValidPropertyName(String methodName, String name)
    throws JMSException {
        //Check if any of the reserved words are used
        if ("NULL".equalsIgnoreCase(name) ||
                "TRUE".equalsIgnoreCase(name) ||
                "FALSE".equalsIgnoreCase(name) ||
                "NOT".equalsIgnoreCase(name) ||
                "AND".equalsIgnoreCase(name) ||
                "OR".equalsIgnoreCase(name) ||
                "BETWEEN".equalsIgnoreCase(name) ||
                "LIKE".equalsIgnoreCase(name) ||
                "IN".equalsIgnoreCase(name) ||
                "IS".equalsIgnoreCase(name)) {

            String errMsg = _lgrMID_EXC + methodName +
                    ":Illegal to use Reserved word as property name:" + name;
            _loggerJM.severe(errMsg);
            throw new javax.jms.JMSException(errMsg);
        }
        //Verify property name follows selector rules for identifiers
        char[] namechars = name.toCharArray();
        if (Character.isJavaIdentifierStart(namechars[0])) {
            for (int i = 1; i < namechars.length; i++) {
                if (!Character.isJavaIdentifierPart(namechars[i])) {
                    //Throw JMSException indicating a bad character
                    //was used as part of the property name
                    String errMsg = _lgrMID_EXC + methodName +
                            ":Invalid character:'"+ namechars[i] +
                            "' used in property name:" + name;
                    _loggerJM.severe(errMsg);
                    throw new javax.jms.JMSException(errMsg);
                }
            }
        } else {
            String errMsg = _lgrMID_EXC + methodName +
                    ":Invalid start character:'"+ namechars[0] +
                    "' used in property name:" + name;
            _loggerJM.severe(errMsg);
            throw new javax.jms.JMSException(errMsg);
        }
    }

    /**
     *  Check for ReadOnly JMS Message Body
     *
     *  @throws MessageNotWriteableException if the message body is read only
     */
    protected void checkForReadOnlyMessageBody(String methodName)
    throws JMSException {
        if (this.readOnlyBody) {
            String errMsg = _lgrMID_EXC + methodName +
                    ":Illegal to set JMS Message body when it is read only:";
            _loggerJM.severe(errMsg);
            throw new javax.jms.MessageNotWriteableException(errMsg);
        }
    }

    /**
     *  Check for WriteOnly JMS Message Body
     *
     *  @throws MessageNotReadableException if the message body is write only
     */
    protected void checkForWriteOnlyMessageBody(String methodName)
    throws JMSException {
        if (!this.readOnlyBody) {
            String errMsg = _lgrMID_EXC + methodName +
                    ":Illegal to read JMS Message body or length when it is write only:";
            _loggerJM.severe(errMsg);
            throw new javax.jms.MessageNotReadableException(errMsg);
        }
    }

    /**
     *  Get the name of the JMS Destination for this JMS Message
     */
    protected String _getJMSDestinationName()
    throws JMSException {
        if (this.jmsDestination instanceof javax.jms.Queue){
            return ((javax.jms.Queue)this.jmsDestination).getQueueName();
        }
        if (this.jmsDestination instanceof javax.jms.Topic){
            return ((javax.jms.Topic)this.jmsDestination).getTopicName();
        }
        return (String)null;
    }

    /**
     *  Get the name of the JMS ReplyTo Destination for this JMS Message
     */
    protected String _getJMSReplyToName()
    throws JMSException{
        if (this.jmsReplyTo instanceof javax.jms.Queue){
            return ((javax.jms.Queue)this.jmsReplyTo).getQueueName();
        }
        if (this.jmsReplyTo instanceof javax.jms.Topic){
            return ((javax.jms.Topic)this.jmsReplyTo).getTopicName();
        }
        return (String)null;
    }

    /**
     *  Set the consumerId for a DirectPacket that is delivered to a consumer
     */
    protected void _setConsumerId(long consumerId){
        this.consumerId = consumerId;
    }

    /**
     *  Get the consumerId for this DirectPacket for use during acknowledgement
     */
    protected long _getConsumerId(){
        return this.consumerId;
    }

    /**
     *  Prepare a DirectPacket foe a JMS Message produce operation
     */
    protected void preparePacketForSend()
    throws JMSException {
        
        //if enable zip all messages, compress the message
        if (enableZip) {
            compress();
        } else if (shouldCompress) {
            //if message JMS_SUN_COMPRESS is set in the prop, zip it.
            compress();
        } else {
            //clear the bit.
            pkt.setFlag(PacketFlag.Z_FLAG, false); 
        }
        
        this.pkt.setDestination(this._getJMSDestinationName());
        if (this.jmsDestination instanceof javax.jms.Queue){
            this.pkt.setIsQueue(true);
        } else {
            this.pkt.setIsQueue(false);
        }
        this.pkt.setDestinationClass(this.jmsDestination.getClass().getName());
        if ((this.jmsReplyTo != null) &&
                (this.jmsReplyTo instanceof javax.jms.Queue ||
                this.jmsReplyTo instanceof javax.jms.Topic)){
            this.pkt.setReplyTo(this._getJMSReplyToName());
            this.pkt.setReplyToClass(this.jmsReplyTo.getClass().getName());
        }
        this._setBodyToPacket();
        this.pkt.setProperties(this.properties);
        this.jmsMessageIDSet = false;
        this.pkt.prepareToSend();
        this._resetJMSMessageID();

        //set the expiration before the actual send
        long expiration = this.getJMSExpiration();
        if (expiration != 0L) {
            expiration = expiration + System.currentTimeMillis();
            this.setJMSExpiration(expiration);
        }
        long deliveryTime = this.getJMSDeliveryTime();
        if (deliveryTime != 0L) {
            deliveryTime = deliveryTime + System.currentTimeMillis();
            this.setJMSDeliveryTime(deliveryTime);
        }
    }

    /**
     *  Set the Body of this JMS Message into the Packet
     */
    protected void _setBodyToPacket()
    throws JMSException {
        //NOP for a vanilla JMS Message w/o a body
    }

    /**
     *  Get the message body into the message fromthe packet
     */
    protected void _getMessageBodyFromPacket()
    throws JMSException {
        //A NOP for a JMS Message w/o a body
    }

    /**
     *  Set the MessageBody of this JMS Message into the Packet
     *
     *  @param  messageBody The message body.
     */
    protected void _setMessageBodyOfPacket(byte[] messageBody) {
        this.pkt.setMessageBody(messageBody);
    }

    /**
     *  Set the MessageBody of this JMS Message into the Packet
     *
     *  @param  messageBody The message body.
     *  @param  off The offset into body that data starts
     *  @param  len The size of message body
     */
    protected void _setMessageBodyOfPacket(byte[] messageBody, int off, int len) {
        this.pkt.setMessageBody(messageBody, off, len);
    }

    /**
     *  Get the MessageBody from the Packet
     */
    protected byte[] _getMessageBodyByteArray()
    throws JMSException {
        if (pkt.getFlag(PacketFlag.Z_FLAG)) {
            decompress();
        }

        return this.pkt.getMessageBodyByteArray();
    }

    protected void _acknowledgeThisMessageForMDB(DirectXAResource dxar)
    throws javax.jms.JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF + /*"messageId="+messageId+":"+*/
                    "acknowledgeThisMessageForMDB()");
        }
        //This is only called for an MDB message - skip consumerId check
        this.ds._acknowledgeThisMessageForMDB(this, this.consumerId,
                JMSService.MessageAckType.ACKNOWLEDGE, dxar, getClientRetries());
    }

    protected void _acknowledgeThisMessageAsDeadForMDB(DirectXAResource dxar)
    throws javax.jms.JMSException {
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF + /*"messageId="+messageId+":"+*/
                    "acknowledgeThisMessageAsDeadForMDB()");
        }
        //This is only called for an MDB message - skip consumerId check
        this.ds._acknowledgeThisMessageForMDB(this, this.consumerId,
                JMSService.MessageAckType.DEAD, dxar, getClientRetries());
    }
    /////////////////////////////////////////////////////////////////////////
    //  end MQ methods for DirectPacket / javax.jms.Message
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Throw a JMSException with the appropriate message for unsupported
     *  operations.
     *
     *  @param  methodname The method name for which this unsupported
     *          exception is to be thrown.
     */
    private void _unsupported(String methodname)
    throws JMSException {
        String unsupported = _lgrMID_WRN+
                    "messageId="+pkt.getMessageID()+":"+methodname;
        _loggerJM.warning(unsupported);
        throw new JMSException(unsupported);
    }
    
  /**
   * compress message body.
   * 
   * NOTE: This code is duplicated from MessageImpl.compress().  Any changes to
   * either method should be made to both in order to keep them in sync.
   *
   * @throws JMSException if cannot compress the message.
   */
  protected void compress() throws JMSException {

    try {
      /**
       * get unzip body bytes.
       */
      byte[] body = pkt.getMessageBodyByteArray();
      int offset = 0;
      int unzipSize = pkt.getMessageBodySize();

      /**
       * no compression if no body.
       */
      if (body == null) {
        setIntProperty(JMS_SUN_UNCOMPRESSED_SIZE, 0);
        setIntProperty(JMS_SUN_COMPRESSED_SIZE, 0);
        return;
      }

      /**
       * byte array for the ziped body
       */
      JMQByteArrayOutputStream baos =
          new JMQByteArrayOutputStream(new byte[32]);

      //get a compressor instance.
      Compressor compressor = Compressor.getInstance();

      //compress body into baos.
      compressor.compress(body, offset, unzipSize, baos);

      baos.flush();

      //get zipped body and size
      byte[] zipbody = baos.getBuf();
      int zipSize = baos.getCount();

      baos.close();

      //set zipped body into pkt.
      pkt.setMessageBody(zipbody, 0, zipSize);

      //set unzip size prop.
      setIntProperty(JMS_SUN_UNCOMPRESSED_SIZE, unzipSize);
      //set zip size prop.
      setIntProperty(JMS_SUN_COMPRESSED_SIZE, zipSize);

      //set zip flag to true.
      pkt.setFlag(PacketFlag.Z_FLAG, true);

    }
    catch (Exception ioe) {
      ioe.printStackTrace();

      JMSException jmse = new JMSException(ioe.toString());
      jmse.setLinkedException(ioe);

      throw jmse;
    }
  }

  /**
   * decompress the message body.
   *
   * NOTE: This code is duplicated from MessageImpl.decompress().  Any changes to
   * either method should be made to both in order to keep them in sync.
   * 
   * @throws JMSException if unable to decompress the message body.
   */
  protected void decompress() throws JMSException {
    //get a decompressor instance.
    Decompressor decomp = Decompressor.getInstance();

    //get ziped body.
    byte[] zipBody = pkt.getMessageBodyByteArray();

    //get unziped size
    int unzipSize = getIntProperty(JMS_SUN_UNCOMPRESSED_SIZE);

    //byte[] to hold unzip body
    byte[] unzipBody = new byte[unzipSize];

    //decompress zip body into unzip body
    decomp.decompress(zipBody, unzipBody);

    //set unzip body into packet
    pkt.setMessageBody(unzipBody, 0, unzipSize);

    //set z flag to false.
    pkt.setFlag(PacketFlag.Z_FLAG, false);

    //init shouldCompress flag to true -- for the case that same message
    //is sent without calling clear properties.
    shouldCompress = true;
  }

@Override
public <T> T getBody(Class<T> c) throws JMSException {
	return MessageImpl._getBody(this, c);
}

@Override
public boolean isBodyAssignableTo(Class c) throws JMSException {
	return MessageImpl._isBodyAssignableTo(this, c);
}

    public void updateDeliveryCount(int newDeliveryCount) {
        if (this.properties == null) {
            this.properties = new Hashtable<String, Object>();
        }
        this.properties.put(JMSService.JMSXProperties.JMSXDeliveryCount.toString(),
                            Integer.valueOf(newDeliveryCount));
    }

    public int getClientRetries() {
        return clientRetries;
    }

    public void setClientRetries(int retryCount) {
        clientRetries = retryCount;
    }
}
