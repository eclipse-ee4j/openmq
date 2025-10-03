/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2021, 2024 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.lists.WeakValueHashMap;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;
import java.util.Collections;
import java.util.Map;
import java.util.regex.*;
import java.io.IOException;

public class DestinationUID extends StringUID {

    static final long serialVersionUID = 3047167637056417589L;

    private static Map<String, DestinationUID> topics = Collections.synchronizedMap(new WeakValueHashMap<>("DestinationUID_topics"));
    private static Map<String, DestinationUID> queues = Collections.synchronizedMap(new WeakValueHashMap<>("DestinationUID_queues"));

    private boolean isQueue = false;
    private String name = null;

    private transient Pattern regExPattern = null;

    private static String localQueue = Globals.getBrokerResources().getString(BrokerResources.M_QUEUE);

    private static String localTopic = Globals.getBrokerResources().getString(BrokerResources.M_TOPIC);

    public static void clearCache() {
        queues.clear();
        topics.clear();
    }

    protected DestinationUID(String name, boolean queue) throws BrokerException {
        super(getUniqueString(name, queue));
        this.name = name;
        this.isQueue = queue;
        if (isWildcard(this.name)) {
            if (isQueue) {
                throw new BrokerException("Wildcards are not supported for queues", Status.UNSUPPORTED_TYPE);
            }
            String regEx = createRegExString(name);
            regExPattern = Pattern.compile(regEx);
        }
    }

    public DestinationUID(String str) throws BrokerException {
        super(str);
        name = getName(str);
        isQueue = getIsQueue(str);
        if (isWildcard(this.name)) {
            if (isQueue) {
                throw new BrokerException("Wildcards are not supported for queues", Status.UNSUPPORTED_TYPE);
            }
            String regEx = createRegExString(name);
            regExPattern = Pattern.compile(regEx);
        }
    }

    /**
     * handles transient data when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        if (isWildcard(this.name)) {
            try {
                if (isQueue) {
                    throw new BrokerException("Wildcards are not supported for queues", Status.UNSUPPORTED_TYPE);
                }
                String regEx = createRegExString(name);
                regExPattern = Pattern.compile(regEx);
            } catch (BrokerException ex) {
                // nothing we can do
                Globals.getLogger().logStack(Logger.ERROR, ex.getMessage(), ex);
            }
        }

    }

    public String getDestType() {
        if (isQueue) {
            return localQueue;
        }
        return localTopic;
    }

    public String getLocalizedName() {
        return Globals.getBrokerResources().getString(BrokerResources.M_DESTINATION, name, getDestType());
    }

    private String getName(String str) {
        return str.substring(2);
    }

    private boolean getIsQueue(String str) {
        return str.charAt(0) == 'Q';
    }

    public static boolean isWildcard(String str) {
        return str.contains("*") || str.contains(">");
    }

    public static String createRegExString(String str) throws BrokerException {
        // ^ needs to start line
        // $ needs to end line unless >
        // . needs to be backslashed
        // * replaced with [\w]+ // NOTE: not UTF-8
        // * replaced with "[\\p{L},\\p{Lu},\\p{Digit}]+
        // > replaced with [\S]*
        // ** replaced with [\S]*
        //
        // As far as validation:
        // there should be . before/after *
        // there should be . before and nothing after >
        // line should never start or end in .
        // there should never be two . in a line

        boolean dot = false; // for validation
        boolean toEndMatch = false;
        StringBuilder buffer = new StringBuilder(str.length() * 2);
        buffer.append('^');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
            case '*':
                boolean doubleAsterisk = false;
                if (i != 0 && !dot) {
                    throw new BrokerException(str + "-Wildcard should be surrounded by .", Status.NOT_ACCEPTABLE);
                }
                if (i != (str.length() - 1)) {
                    // see if we have a following asterisk
                    if (str.charAt(i + 1) == '*') {
                        doubleAsterisk = true;
                        i++;
                    }
                    // make sure dot or wildcard is next
                    if ((i != (str.length() - 1)) && str.charAt(i + 1) != '.' && str.charAt(i + 1) != '>') {
                        throw new BrokerException(str + "-Wildcard should be surrounded by .", Status.NOT_ACCEPTABLE);
                    }
                }
                if (doubleAsterisk) {
                    buffer.append("[\\S]+");
                } else {
                    // buffer.append("[\\w]+");
                    // buffer.append("[\\p{L},\\p{Lu},_,\\o44,\\p{Digit}]+");
                    buffer.append("[\\p{L}\\p{Lu}\\x24\\x5f\\p{Digit}]+");
                }
                dot = false;
                break;
            case '$':
                // substitute hex value
                buffer.append("\\x24");
                break;
            case '>':
                if (dot) {
                    throw new BrokerException(str + "-Wildcard should never be preceded by .", Status.NOT_ACCEPTABLE);
                }
                toEndMatch = true;
                // ok, if the previous character was a wildcard, add a dot
                if (i > 0 && str.charAt(i - 1) == '*') {
                    buffer.append("\\.[\\S]*");
                } else {
                    buffer.append("(\\.|$|^)[\\S]*");
                }
                dot = false;
                break;
            case '.':
                if (i == 0) { // dot at begining
                    throw new BrokerException(str + "-Bad wildcard, name starts with .", Status.NOT_ACCEPTABLE);
                }
                if (i == (str.length() - 1)) {
                    throw new BrokerException(str + "-Bad wildcard, name ends with .", Status.NOT_ACCEPTABLE);
                }
                if (dot) {
                    throw new BrokerException(str + "-Bad wildcard, name was ..", Status.NOT_ACCEPTABLE);
                }
                dot = true;
                buffer.append("\\.");
                break;
            default:
                dot = false;
                buffer.append(c);
            }
        }
        if (!toEndMatch) {
            buffer.append('$');
        }
        return buffer.toString();
    }

    public String getName() {
        return name;
    }

    public boolean isQueue() {
        return isQueue;
    }

    public static DestinationUID getUID(String name, int type) throws BrokerException {
        return getUID(name, DestType.isQueue(type));
    }

    public boolean isWildcard() {
        return regExPattern != null;
    }

    /** @throws IllegalArgumentException */
    public static boolean match(DestinationUID u1, DestinationUID u2) {
        // ok, there are two possible choices:
        // 1 has a wildcard
        // neither has a wildcard
        //
        // if both have a wildcard, something is wrong.

        if (u1.regExPattern != null && u2.regExPattern != null) {
            throw new IllegalArgumentException("Can not compare two wildcards: " + u1 + " -> " + u2);
        }

        if (u1.regExPattern == null && u2.regExPattern == null) {
            return u1.equals(u2);
        }

        if (u1.isQueue() != u2.isQueue()) {
            return false;
        }

        Pattern p = u1.regExPattern;
        String str = u2.getName();
        if (p == null) {
            str = u1.getName();
            p = u2.regExPattern;
        }

        Matcher m = p.matcher(str);
        return m.matches();
    }

    public static DestinationUID getUID(String name, boolean isQueue) throws BrokerException {

        DestinationUID duid = null;
        if (isQueue) {
            duid = queues.get(name);
            if (duid == null) {
                duid = new DestinationUID(name, isQueue);
                queues.put(name, duid);
            }
        } else {
            duid =  topics.get(name);
            if (duid == null) {
                duid = new DestinationUID(name, isQueue);
                topics.put(name, duid);
            }
        }
        return duid;
    }

    public static void clearUID(DestinationUID uid) {
        if (uid.isQueue()) {
            queues.remove(uid.getName());
        } else {
            topics.remove(uid.getName());
        }
    }

    public String getLongString() {
        if (isQueue) {
            return "queue:" + name;
        }
        return "topic:" + name;
    }

    public static String getUniqueString(String name, boolean isQueue) {
        StringBuilder buf = new StringBuilder();
        if (isQueue) {
            buf.append("Q:");
        } else {
            buf.append("T:");
        }
        buf.append(name);

        if (buf.indexOf("/") != -1) {
            for (int i = 0; i < buf.length(); i++) {
                char c = buf.charAt(i);
                if (c == '/') {
                    buf.setCharAt(i, '_');
                }
            }
        }
        return buf.toString();
    }
}
