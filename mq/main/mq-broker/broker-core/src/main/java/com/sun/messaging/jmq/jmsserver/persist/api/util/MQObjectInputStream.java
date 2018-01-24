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
 * @(#)MQObjectInputStream.java	1.7 07/25/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api.util;

import java.io.*;

import com.sun.messaging.jmq.util.io.ClassFilter;

/**
 * A special subclasses of ObjectInputStream that is used for store migration.
 * This class allow us to deserialize an old object format by allowing us to
 * locate class file containing the definitions for the old object format that
 * has been moved to a different package.
 *
 * As an example, we want to change the TransactionUID class, which would make
 * it incompatible with the serialized version in the old store. First, we
 * moved the original version of the TransactionUID to another package, e.g.
 * com.sun.messaging.jmq.jmsserver.data.migration.TransactionUID. Next, we
 * create a new version of com.sun.messaging.jmq.jmsserver.data.TransactionUID
 * class. When loading the old serialized TransactionUID object, we use this
 * class to locate the class definition of the old TransactionUID which has
 * been moved to com.sun.messaging.jmq.jmsserver.data.migration.
 */
public class MQObjectInputStream extends ObjectInputStream {

    public MQObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    /**
     * Overide the ObjectInputStream.resolveClass() to return the old class
     * definition for serialized object prior to 3.7 release.
     */
    protected Class resolveClass(ObjectStreamClass osc)
        throws IOException, ClassNotFoundException {

        String className = osc.getName();
        if (className != null && !className.isEmpty() && ClassFilter.isBlackListed(className)) {
          throw new InvalidClassException("Unauthorized deserialization attempt", osc.getName());
        }

        Class clazz = null;
        String name = osc.getName();
        long serialVersion = osc.getSerialVersionUID();

        // For performance we check serial version ID before the class name
        if (serialVersion == 1518763750089861353L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.data.migration.TransactionAcknowledgement");
            }
        } else if (serialVersion == -6848527428749630176L) {
                if (name.equals("com.sun.messaging.jmq.jmsserver.data.TransactionState")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.data.migration.TransactionState");
            }
        } else if (serialVersion == 4438769866522991889L) {
                if (name.equals("com.sun.messaging.jmq.jmsserver.data.TransactionState")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.data.migration.thrasher2.TransactionState");
            }
        } else if (serialVersion == 4132677693277056907L) {
                if (name.equals("com.sun.messaging.jmq.jmsserver.data.TransactionState")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.data.migration.finch.TransactionState");
            }
        } else if (serialVersion == 3158474602500727000L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.data.TransactionUID")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.data.migration.TransactionUID");
            }
        } else if (serialVersion == 5231476734057401743L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.core.ConsumerUID")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.core.migration.ConsumerUID");
            }
        } else if (serialVersion == 8099322820906352261L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.core.ConsumerUID")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.core.migration.thrasher.ConsumerUID");
            }
        } else if (serialVersion == 5642215309770752611L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.persist.TransactionInfo")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo");
            }
        } else if (serialVersion == -6833553314062089908L) {
            if (name.equals("com.sun.messaging.jmq.jmsserver.persist.HABrokerInfo")) {
                clazz = Class.forName("com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo");
            }
        }

        // Return the class definition of old serialized object
        if (clazz != null) {
            return clazz;
        }

        return super.resolveClass(osc);
    }
}
