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
 * @(#)ULFformatter.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.logging;

import java.util.*;
import java.util.logging.*;

import java.io.*;

import java.text.SimpleDateFormat;

import com.sun.messaging.jmq.jmsclient.ConnectionMetaDataImpl;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * ULF Message formatter.
 */
public class ULFformatter extends SimpleFormatter {

    public static final String FR_BEGIN = "[#|";
    public static final String FR_END = "|#]\n";
    public static final String FR_DELIMITER = "|";

    public static final String
        PRODUCT_NAME = ConnectionMetaDataImpl.JMSProviderName + " " +
                       ConnectionMetaDataImpl.providerVersion;

    public static final ClientResources resources = ClientResources.getResources();

    //XXX HAWK: replace time zone with offset time.
    public static final String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS z";

    //XXX HAWK: use static instance?
    private SimpleDateFormat formatter =
        new SimpleDateFormat(pattern, Locale.getDefault());

    /**
     * Format the log record.  If this is a MQ packet record, it is formatted
     * to the packet format.  Otherwise, the simple formatter format is used.
     */
    public synchronized String format(LogRecord record) {

        String str = doFormat (record);

        if ( str == null ) {
            str = super.format(record);
        }

        return str;
    }

    /**
     * Format message to ULF format.
     *
     * [#|Date&Time&Zone|LogLevel|ProductName|ModuleID|OptionalKey1=Value1
     * ;OptionalKey2=Value2;OptionalKeyN=ValueN|MessageID:MessageText|#]\n
     *
     * http://jpgserv.us.oracle.com/not/MQHawk/engineering/funcspecs/javaClientLogging/UniformLogging0.7.pdf
     *
     *
     * @param record LogRecord
     * @return String
     */
    private String doFormat (LogRecord record) {

        StringBuffer sb = new StringBuffer (FR_BEGIN);

        String datestr = formatter.format ( new Date(record.getMillis()) );

        sb.append(datestr).append(FR_DELIMITER);

        sb.append( record.getLevel().getName() ).append(FR_DELIMITER);

        sb.append(PRODUCT_NAME).append(FR_DELIMITER);

        sb.append(record.getSourceClassName()).append(FR_DELIMITER);

        Object params[] = record.getParameters();

        int length = 0;

        if ( params != null ) {
            length = params.length;
        }

        String key = record.getMessage();

        String msg = null;

        try {
            switch (length) {
            case 0:
                msg = resources.getKString(key);
                break;
            case 1:
                msg = resources.getKString(key, params[0]);
                break;
            case 2:
                msg = resources.getKString(key, params[0], params[1]);
                break;
            default:
                msg = resources.getKString(key, params);
            }
        } catch (Exception e) {
           msg = key;
        }

        Throwable throwable = record.getThrown();
        if ( throwable != null ) {
            msg = msg + "\n" + getThrowableMessage (throwable);
        }

        sb.append(msg);

        sb.append(FR_END);

        return sb.toString();
    }

    private static String getThrowableMessage (Throwable throwable) {
        String msg = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            PrintWriter pw = new PrintWriter(baos);

            throwable.printStackTrace(pw);

            pw.flush();
            baos.flush();

            baos.close();

            msg = baos.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return msg;
    }

}
