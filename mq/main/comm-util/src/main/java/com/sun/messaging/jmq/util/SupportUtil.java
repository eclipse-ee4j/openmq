/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.lang.reflect.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Map;
import java.util.Iterator;

public class SupportUtil {

    public static Hashtable<String, Object> getAllStackTracesAsMap() {
        Hashtable<String, Object> ht = new Hashtable<>();
        try {
            Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
            Iterator<Map.Entry<Thread, StackTraceElement[]>> itr = map.entrySet().iterator();
            Map.Entry<Thread, StackTraceElement[]> me = null;
            while (itr.hasNext()) {
                me = itr.next();
                Thread thr = me.getKey();
                StackTraceElement[] stes = me.getValue();
                String name = thr + " 0x" + Long.toHexString(thr.hashCode());
                Vector<String> value = new Vector<>();
                for (StackTraceElement ste : stes) {
                    value.add(ste.toString());
                }
                ht.put(name, value);
            }
        } catch (Throwable thr) {
            ht.put("error", "Can not getStackTrace " + thr);

        }
        return ht;
    }

    public static String getAllStackTraces(String prefix) {
        try {
            Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
            Iterator<Map.Entry<Thread, StackTraceElement[]>> itr = map.entrySet().iterator();
            Map.Entry<Thread, StackTraceElement[]> me = null;
            StringBuilder retstr = new StringBuilder();
            while (itr.hasNext()) {
                me = itr.next();
                Thread thr = me.getKey();
                StackTraceElement[] stes = me.getValue();
                retstr.append(prefix + thr + " 0x" + Long.toHexString(thr.hashCode()) + '\n');
                for (int i = 0; i < stes.length; i++) {
                    retstr.append(prefix + '\t' + stes[i] + '\n');
                }
                retstr.append('\n');
            }
            return retstr.toString();
        } catch (Throwable thr) {
            return prefix + "Can not getStackTrace " + thr;
        }

    }

    public static String getStackTrace(String prefix) {
        Thread thr = Thread.currentThread();
        try {
            Method m = Thread.class.getMethod("getStackTrace", new Class[0]);
            StackTraceElement[] stes = (StackTraceElement[]) m.invoke(thr, new Object[0]);
            StringBuilder retstr = new StringBuilder();
            retstr.append(prefix).append(thr).append(" 0x").append(Long.toHexString(thr.hashCode())).append('\n');
            for (StackTraceElement ste : stes) {
                retstr.append(prefix).append('\t').append(ste).append('\n');
            }
            return retstr.toString();
        } catch (Throwable t) {
            return prefix + "Can not getStackTrace " + t;
        }
    }

    public static String getStackTraceString(Throwable e) {
        String str = null;
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            str = sw.toString();
        } catch (Throwable t) {
            str = e.toString();
        }
        return str;
    }

    /***********************************************************
     * BEGIN util of java.lang.instrument.Instrumentation (see
     * http://docs.oracle.com/javase/7/docs/api/java/lang/instrument/Instrumentation.html) These methods should not be
     * called in MQ production code
     ***********************************************************
     *
     * private static java.lang.instrument.Instrumentation instrumentation;
     *
     * public static void premain(String args, java.lang.instrument.Instrumentation inst) { instrumentation = inst; }
     *
     * public static long getObjectSize(Object o) { return instrumentation.getObjectSize(o); }
     *******************************************************
     *
     * END util of java.lang.instrument.Instrumentation
     *******************************************************/

}
