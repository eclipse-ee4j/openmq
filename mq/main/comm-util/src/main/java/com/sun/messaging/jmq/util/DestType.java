/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

/**
 * DestType defines the bitmaps for setting destination types. Strictly speaking a destination has three attributes:
 *
 * <pre>
 *      1. Its type (Queue or Topic)
 *      2. Its lifespan (temporary or not)
 *      3. Its flavor (single, etc)
 * </pre>
 *
 * In practice all combinations are not used (for example you don't have round-robin topics), but by using bitmaps we
 * have that flexibility.
 * <P>
 * This class defines the bitmaps to specify these three components of a destination type.
 * <P>
 * A couple examples of specifying a destination type are:
 *
 * <pre>
 * // A queue
 * int type = DEST_TYPE_QUEUE;
 *
 * // A temporary topic
 * int type = DEST_TYPE_TOPIC | DEST_TMP;
 * </pre>
 */
public class DestType {

    public static final int DEST_TYPE_QUEUE = 0x00000001;
    public static final int DEST_TYPE_TOPIC = 0x00000002;

    public static final int DEST_TEMP = 0x00000010;
    public static final int DEST_AUTO = 0x00000020;
    public static final int DEST_INTERNAL = 0x00000040;
    public static final int DEST_ADMIN = 0x00000080;
    public static final int DEST_DMQ = 0x00001000;

    /**
     * @since 3.7
     */
    public static final int DEST_LOCAL = 0x00002000;

    /**
     * @deprecated since 3.5
     */
    @Deprecated
    public static final int DEST_FLAVOR_SINGLE = 0x00000100;

    /**
     * Internal destination name prefix
     *
     * @since 3.5
     */
    public static final String INTERNAL_DEST_PREFIX = "mq.";

    public static final String QUEUESTR = "queue"; // used by acl
    public static final String TOPICSTR = "topic";

    /**
     * access control method
     */
    public static String queueOrTopic(int type) {
        // only access control for non-temp QUEUES and TOPICS
        if ((type & DEST_TEMP) == DEST_TEMP) {
            return null;
        }
        if ((type & DEST_TYPE_QUEUE) == DEST_TYPE_QUEUE) {
            return toString(DEST_TYPE_QUEUE);
        }
        if ((type & DEST_TYPE_TOPIC) == DEST_TYPE_TOPIC) {
            return toString(DEST_TYPE_TOPIC);
        }
        return null;
    }

    /**
     * used by access control
     */
    public static boolean isQueueStr(String queueOrTopic) {
        if (queueOrTopic == null) { // should never happen
            return false;
        }
        return queueOrTopic.equals(QUEUESTR);
    }

    public static boolean isQueue(int mask) {
        return (mask & DEST_TYPE_QUEUE) == DEST_TYPE_QUEUE;
    }

    public static boolean isTopic(int mask) {
        return (mask & DEST_TYPE_TOPIC) == DEST_TYPE_TOPIC;
    }

    public static boolean isTemporary(int mask) {
        return (mask & DEST_TEMP) == DEST_TEMP;
    }

    /**
     * @since 4.0
     */
    public static boolean isLocal(int mask) {
        return (mask & DEST_LOCAL) == DEST_LOCAL;
    }

    /**
     * @since 3.5
     */
    public static boolean isAutoCreated(int mask) {
        return (mask & DEST_AUTO) == DEST_AUTO;
    }

    /**
     * @since 3.5
     */
    public static boolean isAdmin(int mask) {
        return (mask & DEST_ADMIN) == DEST_ADMIN;
    }

    /**
     * @since 3.5
     */
    public static boolean isInternal(int mask) {
        return (mask & DEST_INTERNAL) == DEST_INTERNAL;
    }

    /**
     * @since 3.6
     */
    public static boolean isDMQ(int mask) {
        return (mask & DEST_DMQ) == DEST_DMQ;
    }

    /**
     * @since 3.5
     */
    public static boolean destNameIsInternal(String destName) {
        if ((destName != null) && destName.startsWith(INTERNAL_DEST_PREFIX)) {
            return (true);
        }

        return (false);
    }

    /**
     * @since 3.5
     */
    public static boolean destNameIsInternalLogging(String destName) {
        if ((destName != null) && destName.startsWith(INTERNAL_DEST_PREFIX + "log.broker")) {
            return (true);
        }

        return (false);
    }

    /**
     * @deprecated since 3.5
     */
    @Deprecated
    public static boolean isSingle(int mask) {
        return (mask & DEST_FLAVOR_SINGLE) == DEST_FLAVOR_SINGLE;
    }

    public static String toString(int mask) {
        StringBuilder s = new StringBuilder();

        if (isQueue(mask)) {
            s.append(QUEUESTR);
        } else if (isTopic(mask)) {
            s.append(TOPICSTR);
        } else {
            s.append("?????");
        }

        if (isTemporary(mask)) {
            s.append(":temporary");
        }
        if (isAutoCreated(mask)) {
            s.append(":autocreated");
        }
        if (isInternal(mask)) {
            s.append(":internal");
        }
        if (isAdmin(mask)) {
            s.append(":admin");
        }
        if (isLocal(mask)) {
            s.append(":local");
        }

        if (isSingle(mask)) {
            s.append(":single");
        }

        return s.toString();
    }
}
