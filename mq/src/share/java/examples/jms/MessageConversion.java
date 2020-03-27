/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.jms.*;

/**
 * The MessageConversion class consists only of a main method, which creates 
 * and then reads a StreamMessage and a BytesMessage.  It does not send the 
 * messages.
 * <p>
 * The program demonstrates type conversions in StreamMessages:  you can write
 * data as a String and read it as an Int, and vice versa.  The program also
 * calls clearBody() to clear the message so that it can be rewritten.
 * <p>
 * The program also shows how to write and read a BytesMessage using data types
 * other than a byte array.  Conversion between String and other types is
 * not supported.
 * <p>
 * Before it can read a BytesMessage or StreamMessage that has not been sent,
 * the program must call reset() to put the message body in read-only mode 
 * and reposition the stream.
 */
public class MessageConversion {

    /**
     * Main method.  Takes no arguments.
     */
    public static void main(String[] args) {
        ConnectionFactory    connectionFactory = null;
        Connection           connection = null;
        Session              session = null;
        BytesMessage         bytesMessage = null;
        StreamMessage        streamMessage = null;
        int                  exitResult = 0;
        
        try {
            connectionFactory = 
                SampleUtilities.getConnectionFactory();
            connection = 
                connectionFactory.createConnection();
            session = connection.createSession(false, 
                Session.AUTO_ACKNOWLEDGE);
    	} catch (Exception e) {
            System.out.println("Connection problem: " + e.toString());
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException ee) {}
            }
    	    System.exit(1);
    	} 

        try {
            /* 
             * Create a StreamMessage and write values of various data types
             * to it.
             * Reset the message, then read the values as Strings.
             * Values written to a StreamMessage as one data type can be read 
             * as Strings and vice versa (except for String to char conversion).
             */
            streamMessage = session.createStreamMessage();
    	    streamMessage.writeBoolean(false);
    	    streamMessage.writeDouble(123.456789e222);
    	    streamMessage.writeInt(223344);
    	    streamMessage.writeChar('q');
            streamMessage.reset();
            System.out.println("Reading StreamMessage items of various data"
                + " types as String:");
            System.out.println(" Boolean: " + streamMessage.readString());
            System.out.println(" Double: " + streamMessage.readString());
            System.out.println(" Int: " + streamMessage.readString());
            System.out.println(" Char: " + streamMessage.readString());
            
            /*
             * Clear the body of the StreamMessage and write several Strings
             * to it.
             * Reset the message and read the values back as other data types.
             */
            streamMessage.clearBody();
            streamMessage.writeString("true");
            streamMessage.writeString("123.456789e111");
            streamMessage.writeString("556677");
            // Not char:  String to char conversion isn't valid
            streamMessage.reset();
            System.out.println("Reading StreamMessage String items as other"
                + " data types:");
            System.out.println(" Boolean: " + streamMessage.readBoolean());
            System.out.println(" Double: " + streamMessage.readDouble());
            System.out.println(" Int: " + streamMessage.readInt());
            
            /* 
             * Create a BytesMessage and write values of various types into
             * it.
             */
            bytesMessage = session.createBytesMessage();
    	    bytesMessage.writeBoolean(false);
    	    bytesMessage.writeDouble(123.456789e22);
    	    bytesMessage.writeInt(778899);
    	    bytesMessage.writeInt(0x7f800000);
    	    bytesMessage.writeChar('z');
    	    
    	    /*
    	     * Reset the message and read the values back.  Only limited
    	     * type conversions are possible.
    	     */
            bytesMessage.reset();
            System.out.println("Reading BytesMessages of various types:");
            System.out.println(" Boolean: " + bytesMessage.readBoolean());
            System.out.println(" Double: " + bytesMessage.readDouble());
            System.out.println(" Int: " + bytesMessage.readInt());
            System.out.println(" Float: " + bytesMessage.readFloat());
            System.out.println(" Char: " + bytesMessage.readChar());
        } catch (JMSException e) {
            System.out.println("JMS Exception occurred: " + e.toString());
            exitResult = 1;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    exitResult = 1;
                }
            }
        }
        SampleUtilities.exit(exitResult);
    }
}
