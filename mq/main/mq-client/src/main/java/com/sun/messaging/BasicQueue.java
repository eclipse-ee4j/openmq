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
 * @(#)BasicQueue.java	1.10 06/28/07
 */ 

package com.sun.messaging;

import javax.jms.*;

/**
 * A <code>BasicQueue</code> represents an identity of a repository of messages used
 * in the JMS Point-To-Point messaging domain.
 *
 * @see         javax.jms.Queue javax.jms.Queue
 */
public class BasicQueue extends com.sun.messaging.Destination implements javax.jms.Queue {

    /**
     * Constructs an identity of a Point-To-Point Queue with the default name
     */
    public BasicQueue () {
	super();
    }

    /**
     * Constructs an identity of a Point-To-Point Queue with the given name
     *
     * @param   name The name of the Queue
     */
    public BasicQueue (String name) throws javax.jms.JMSException {
	super(name);
    }

    /**
     * Compares this Queue to the specified object.
     * The result is <code>true</code> if and only if the arguement is not
     * <code>null</code> and is a <code>Queue</code> object with the same
     * Queue Name as this object.
     *
     * @param   anObject  The object to compare this <code>Queue</code> against.
     * @return  <code>true</code> if the object and this <code>Queue</code>are equal;
     *          <code>false</code> otherwise.
     *
     */
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if ((anObject != null) && (anObject instanceof BasicQueue)) {
            try {
                //null test - since getQueueName could also return null
                String name = getQueueName();
                if (name != null) {
                    return name.equals(((BasicQueue)anObject).getQueueName());
                } else {
                    return (name == ((BasicQueue)anObject).getQueueName()) ;
                }
            } catch(JMSException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        String name = null;
        try {
            name = getQueueName();
        } catch (Exception ex) {
        }
        if (name == null) return super.hashCode();
        return name.hashCode();
    }

    /**
     * Returns whether this is a Queueing type of Destination object
     *
     * @return whether this is a Queueing type of Destination object
     */
    public boolean isQueue() {
        return true;
    }

    /**
     * Returns whether this is a Temporary type of Destination object
     *
     * @return whether this is a Temporary type of Destination object
     */
    public boolean isTemporary() {
        return false;
    }
}
