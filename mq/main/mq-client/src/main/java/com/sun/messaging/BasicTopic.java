/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
 * @(#)BasicTopic.java	1.11 06/28/07
 */

package com.sun.messaging;

import java.util.Objects;

import jakarta.jms.*;

/**
 * A <code>BasicTopic</code> represents an identity of a repository of messages used in the JMS Publish/Subscribe
 * messaging domain.
 *
 * @see jakarta.jms.Topic jakarta.jms.Topic
 */
public class BasicTopic extends com.sun.messaging.Destination implements jakarta.jms.Topic {

    /**
     * 
     */
    private static final long serialVersionUID = 1003358501997421212L;

    /**
     * Constructs an identity of a Publish/Subscribe Topic with the default name
     */
    public BasicTopic() {
    }

    /**
     * Constructs an identity of a Publish/Subscribe Topic with the given name
     *
     * @param name The name of the Topic
     */
    public BasicTopic(String name) throws jakarta.jms.JMSException {
        super(name);
    }

    /**
     * Compares this Topic to the specified object. The result is <code>true</code> if and only if the arguement is not
     * <code>null</code> and is a <code>Topic</code> object with the same Topic Name as this object.
     *
     * @param anObject The object to compare this <code>Topic</code> against.
     * @return <code>true</code> if the object and this <code>Topic</code>are equal; <code>false</code> otherwise.
     *
     */
    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof BasicTopic) {
            try {
                // null test - since getTopicName could also return null
                String name = getTopicName();
                return Objects.equals(name, ((BasicTopic) anObject).getTopicName());
            } catch (JMSException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        String name = null;
        try {
            name = getTopicName();
        } catch (Exception ex) {
        }
        if (name == null) {
            return super.hashCode();
        }
        return name.hashCode();
    }

    /**
     * Returns whether this is a Queueing type of Destination object
     *
     * @return whether this is a Queueing type of Destination object
     */
    @Override
    public boolean isQueue() {
        return false;
    }

    /**
     * Returns whether this is a Temporary type of Destination object
     *
     * @return whether this is a Temporary type of Destination object
     */
    @Override
    public boolean isTemporary() {
        return false;
    }
}
