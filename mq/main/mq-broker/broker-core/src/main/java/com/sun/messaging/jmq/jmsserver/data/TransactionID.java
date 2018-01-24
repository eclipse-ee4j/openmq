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
 * @(#)TransactionID.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.*;

public class TransactionID implements Serializable
{
    private int port = 0;
    private byte[] IP = null;
    private int transid = 0;
    private String unique_id = null;

    public TransactionID(int transid, byte[] IP, int port)
    {
        this.port = port;
        this.IP = IP;
        this.transid = transid;
    }

    private void initUniqueStr() {

        unique_id = "";
        
        for (int i = 0; i < IP.length; i ++) {
            unique_id +=Integer.toHexString(((int)IP[i]) & 0xff);
        }
        unique_id += ":" + Integer.toHexString(port) + ":" + Integer.toHexString(transid);
    }

    public boolean equals(Object obj)
    {
        if (! (obj instanceof TransactionID)) 
            return false;

        TransactionID tid = (TransactionID) obj;

        if (port != tid.port || transid != tid.transid) {
            return false;
        }

        if ((IP == null || tid.IP == null) && IP != tid.IP ) 
            return false;

        if (IP.length != tid.IP.length)
            return false;

        for (int i =0; i < IP.length; i ++)
            if (IP[i] != tid.IP[i])
                return false;
        return true;
    }

    public int hashCode() {
        int h = 0;
        for (int i =0; i < IP.length; i ++)
            h += (Byte.valueOf(IP[i])).intValue();

        h = 31*h + port;
        h = 31*h + transid;

        return h;
    }

    public String toString() {
/*
        return "TransactionID[" + transid + "," +
                 (new String(IP)) + "," + port + "]";
*/
        return "TransactionID[" + transid + "," +
                  port + "]";
    }

    public String getUniqueName() {
        if (unique_id == null) {
            initUniqueStr();
        }
        return unique_id;

    }

}
