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
 * @(#)PacketUtil.java	1.1 07/17/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.io.*;

import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

public class PacketUtil {

    /**
     * Dump the body of the packet.
     *
     * Note: This method is being refactored from the Packet class so that it
     * can also be used by the old packet code, i.e. the client. By doing this,
     * the client doesn't have any dependency on the broker's Packet class.
     */
    public static void dumpBody(
        PrintStream os,         // Stream to dump contents to
        int         pType,      // Type of packet
        InputStream is,         // Input stream to body
        int         bodySize,   // Number of bytes in body
        Hashtable props         // Packet properties
        ) {

	os.print("   Message Body: " + bodySize + " bytes ");

	if (is == null) {
            os.println();
            os.flush();
	    return;
	}
        int n;

        switch (pType) {

        case PacketType.ACKNOWLEDGE:
        case PacketType.REDELIVER:
            SysMessageID id = new SysMessageID();
            n = bodySize/(SysMessageID.ID_SIZE + 8);

            // Read acknowledgement blocks out of body of packet
            try {
                Integer idType = null;
                if (props != null) {
                    idType = (Integer)props.get("JMQBodyType");
                }
                DataInputStream dis = new DataInputStream(is);
                while (n > 0) {
                    if (idType == null || idType.intValue() ==
                                        PacketType.CONSUMERID_L_SYSMESSAGEID) {
                        os.print("[" + dis.readLong() + ":");
                    } else {
                        os.print("[" + dis.readInt() + ":");
                    }
                    id.readID(dis);
                    os.print(id + "]");
                    n--;
                }
                dis.close();
            } catch (Exception e) {
                os.println("Exception when reading packet body: " + e);
            }
            break;

        case PacketType.TEXT_MESSAGE:
            n = bodySize;
            if (n > 40) n = 40;
            byte[] buf = new byte[40];
            try {
                is.read(buf);
                os.print("[" + new String(buf));
                if (n < bodySize) {
                    os.print(". . .");
                }
                os.print("]");
            } catch (IOException e) {
                os.println("Exception when reading packet body: " + e);
            }
            break;

        case PacketType.MAP_MESSAGE:
	case PacketType.OBJECT_MESSAGE:
            try {
                ObjectInputStream ois = new FilteringObjectInputStream(is);
                Object o = ois.readObject();
                String s = o.toString();
                if (s.length() > 512) {
                    os.println(s.substring(0, 512) + ". . .");
                } else {
                    os.println(s);
                }
                ois.close();
            } catch (Exception e) {
                os.println("Exception when deserializing packet body: " + e);
            }
            break;

        case PacketType.AUTHENTICATE:
            try {
                String type = (String)props.get("JMQAuthType");
                os.print(type + ": ");
                DataInputStream dis = new DataInputStream(is);
                if (type.equals("basic")) {
                    String username = dis.readUTF();
                    String password = dis.readUTF();
                    os.print("username=" + username + ", password=" + password);
                } else if (type.equals("digest")) {
                    String username = dis.readUTF();
                    String password = dis.readUTF();
                    os.print("username=" + username + ", password=" + password);
                } else {
                    os.print("Unknown authentication type");
                }
            } catch (Exception e) {
                os.println("Exception when reading packet body: " + e);
            }
            break;

        case PacketType.RECOVER_TRANSACTION_REPLY:
            try {
                JMQXid xid;
                Integer quantity = (Integer)props.get("JMQQuantity");
                n = 0;
                if (quantity != null) {
                    n = quantity.intValue();
                }

                DataInputStream dis = new DataInputStream(is);
                while (n > 0) {
                    xid = JMQXid.read(dis);
                    os.println("[XID=" + xid + "], ");
                    n--;
                }
            } catch (IOException e) {
                os.println("Could not decode XIDs: " + e);
            }
            break;

        case PacketType.INFO:

            try {
                ObjectInputStream dis = new FilteringObjectInputStream(is);
                Hashtable ht = (Hashtable)dis.readObject();
                Iterator itr = ht.entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry mEntry = (Map.Entry)itr.next();
                    Object key = mEntry.getKey();
                    Object value = mEntry.getValue();
                    if (value instanceof Hashtable) {
                       os.println("\tTable: " + key);

                       Iterator itr1 = ((Hashtable)value).entrySet().iterator();
                       while (itr1.hasNext()) {
                           Map.Entry mEntry1 = (Map.Entry)itr1.next();
                           Object key1 = mEntry1.getKey();
                           Object value1 = mEntry1.getValue();
                           os.println("\t\t"+key1 + "=" + value1 );
                       }
                    } else {
                        os.println("\t"+key + "=" + value );
                    }
                }
            } catch (Exception e) {
                os.println("Could not decode INFO packet: " + e);
            }
             break;

        case PacketType.START_TRANSACTION:
        case PacketType.COMMIT_TRANSACTION:
        case PacketType.ROLLBACK_TRANSACTION:
        case PacketType.END_TRANSACTION:
        case PacketType.PREPARE_TRANSACTION:
        case PacketType.RECOVER_TRANSACTION:
            try {
                JMQXid xid;
                xid = JMQXid.read(new DataInputStream(is));
                os.println("[XID=" + xid + "]");
            } catch (IOException e) {
                os.println("Could not decode XID: " + e);
            }
            break;
        case PacketType.VERIFY_TRANSACTION_REPLY:
            try {
                ObjectInputStream oos = new FilteringObjectInputStream(is);
                Object newo = oos.readObject();
                os.println(newo);
            } catch (Exception e) {
                os.println("Could not decode verify body: " + e);
            }
            break;
        }

        os.println();
        os.flush();
    }


    public static String dumpThrowable(Throwable thr) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bos);
            thr.printStackTrace(ps);
            ps.flush();
            bos.flush();
            String str = bos.toString();
            ps.close();
            bos.close();
            return str;
        } catch (Exception ex) {
            return "Exception dumping exception " + ex;
        }
    }

    public static String dumpPacket(Packet pkt) {
        return dumpPacket(pkt,"\t");
    }

    public static String dumpPacket(Packet pkt, String prefix)
    {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bos);
            pkt.dump(ps, prefix);
            ps.flush();
            bos.flush();
            String str = bos.toString();
            ps.close();
            bos.close();
            return str;
        } catch (Exception ex) {
            return "Exception dumping packet " + ex;
        }
    }

}
