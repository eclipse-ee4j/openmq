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
 * @(#)PacketProperties.java	1.7 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/*
 * Only the follow property types are supported:
 *    Boolean, Byte, Short, Integer, Long, Float, Double, and String
 *
 * Format:
 *     [Name length][Name (UTF-8)][Value type][Value Length][Value]
 *
 *    Pad out to 32 bit boundry
 * 
 */   
public class PacketProperties 
{
    public static final short BOOLEAN = 1; 
    public static final short BYTE = 2;
    public static final short SHORT = 3;
    public static final short INTEGER = 4;
    public static final short LONG = 5;
    public static final short FLOAT = 6;
    public static final short DOUBLE = 7;
    public static final short STRING = 8;
    public static final short OBJECT = 9;

    public static final int VERSION1=1;

    // add OBJECT

    // add version comment

    public static void write(Map map, OutputStream os) 
            throws IOException
    {
        if (map == null) {
            return;
        }
        DataOutputStream dos = new DataOutputStream(os);

        dos.writeInt(VERSION1);
        dos.writeInt(map.size());
        Iterator<Map.Entry> itr = map.entrySet().iterator();
        Map.Entry pair = null;
        String key = null;
        Object value = null;
        while (itr.hasNext()) {
            pair = itr.next();
            key = (String)pair.getKey();
            value = pair.getValue();
            dos.writeUTF(key);
            if (value instanceof Boolean) {
                dos.writeShort(BOOLEAN);
                dos.writeBoolean(((Boolean)value).booleanValue());
            } else if (value instanceof Byte) {
                dos.writeShort(BYTE);
                dos.writeByte(((Byte)value).byteValue());
            } else if (value instanceof Short) {
                dos.writeShort(SHORT);
                dos.writeShort(((Short)value).shortValue());
            } else if (value instanceof Integer) {
                dos.writeShort(INTEGER);
                dos.writeInt(((Integer)value).intValue());
            } else if (value instanceof Long) {
                dos.writeShort(LONG);
                dos.writeLong(((Long)value).longValue());
            } else if (value instanceof Float) {
                dos.writeShort(FLOAT);
                dos.writeFloat(((Float)value).floatValue());
            } else if (value instanceof Double) {
                dos.writeShort(DOUBLE);
                dos.writeDouble(((Double)value).doubleValue());
            } else if (value instanceof String) {
                dos.writeShort(STRING);
                dos.writeUTF((String)value);
            } else {
                dos.writeShort(OBJECT);
                JMQByteArrayOutputStream bos = 
                    new JMQByteArrayOutputStream(new byte[256]);
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(value);
                oos.close();
                byte[] data = bos.getBuf();
                dos.writeInt(data.length);
                dos.write(data, 0, data.length);
            }
        }      
    }

    public static Hashtable parseProperties(InputStream is) 
        throws IOException, ClassNotFoundException
    {
        DataInputStream dis = new DataInputStream(is);

        int version = dis.readInt();
        if (version != VERSION1) {
            throw new IOException("Unsupported version of properties serialization ["
                    + version + "]");
        }
        int propcnt = dis.readInt();
        Hashtable ht = new Hashtable(propcnt);

        int cnt = 0;
        while (cnt < propcnt) {
            String key = dis.readUTF();
            if (key.length() <= 0) break;

            short type = dis.readShort();

            Object value = null;
            switch( type) {
                case BOOLEAN:
                    //value = new Boolean(dis.readBoolean());
                    value = Boolean.valueOf (dis.readBoolean());
                    break;
                case BYTE:
                    value = Byte.valueOf(dis.readByte());
                    break;
                case SHORT:
                    value = Short.valueOf(dis.readShort());
                    break;
                case INTEGER:
                    value = Integer.valueOf(dis.readInt());
                    break;
                case LONG:
                    value = Long.valueOf(dis.readLong());
                    break;
                case FLOAT:
                    value = Float.valueOf(dis.readFloat());
                    break;
                case DOUBLE:
                    value = Double.valueOf(dis.readDouble());
                    break;
                case STRING:
                    value = dis.readUTF();
                    break;
                case OBJECT:
                    int bytes = dis.readInt();
                    byte[] buf = new byte[bytes];
                    dis.read(buf,0,bytes);
                    JMQByteArrayInputStream bis = 
                        new JMQByteArrayInputStream(buf);
                    ObjectInputStream ois = new FilteringObjectInputStream(bis);
                    value = ois.readObject();
                    ois.close();
                    bis.close();
                default:
                      // ignore (dont throw exception)
            }
            ht.put(key, value);
            cnt ++;
        }      
        
        return ht;
    }



    
}
