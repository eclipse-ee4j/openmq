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
 * @(#)GlobalProperties.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver;

import com.sun.messaging.jmq.jmsserver.config.*;

/**
 * Singleton class which contains properties which are used
 * in several locations in the broker.<P>
 *
 * Ultimately, I'd like this to be a generic class (so you could
 * just add in a new property to watch and everything else would
 * automatically happen .. but theres not enough time in this
 * release <P>
 *
 * These properties are different than the properties in Globals,
 * because they may change at any point (and should be retrieve
 * each time) <P>
 */

// FOR NOW, when you add a new property:
//	1. create a new index
//	2. add the property name to property_names
//	3. create a global property (e.g. AUTOCREATE_TOPIC)
//	4. update updateProperty
public class GlobalProperties implements ConfigListener
{
    private static final Object lock = GlobalProperties.class;
    private static volatile GlobalProperties globals = null;

    /*
     * Property names
     */
    private final int AUTOCREATE_TOPIC_NDX = 0;
    private final int AUTOCREATE_QUEUE_NDX = 1;
    private final int REDELIVER_NDX = 2;
    private final int TRANSACTION_DEBUG_NDX = 3;

    public final String[] property_names =
       { Globals.IMQ + ".autocreate.topic", 
         Globals.IMQ + ".autocreate.queue",
         Globals.IMQ + ".redelivered.optimization",
         Globals.IMQ + ".transaction.debug" /* PRIVATE */
       };

    public boolean AUTOCREATE_TOPIC = false;
    public boolean AUTOCREATE_QUEUE = false;
    public boolean REDELIVER_OPTIMIZATION = true;
    public boolean TRANSACTION_DEBUG = false;

    private GlobalProperties() {
        BrokerConfig config = Globals.getConfig();
        for (int i =0; i < property_names.length; i ++) {
            updateProperty(property_names[i]);
            config.addListener(property_names[i], this);
        }
    }

    public void updateProperty(String propname) {
        BrokerConfig config = Globals.getConfig();

        // a case statement might be cleaner, but this is OK for
        // this release
        if (propname.equals(property_names[AUTOCREATE_TOPIC_NDX])) {
            AUTOCREATE_TOPIC=config.getBooleanProperty(propname);
        } else if (propname.equals(property_names[AUTOCREATE_QUEUE_NDX])) {
            AUTOCREATE_QUEUE=config.getBooleanProperty(propname);
        } else if (propname.equals(property_names[REDELIVER_NDX])) {
            REDELIVER_OPTIMIZATION=config.getBooleanProperty(propname, true);
        } else if (propname.equals(property_names[TRANSACTION_DEBUG_NDX])) {
            TRANSACTION_DEBUG=config.getBooleanProperty(propname);
        }
   }

    public static GlobalProperties getGlobalProperties() {
        if (globals == null) {
            synchronized(lock) {
                if (globals == null)
                    globals = new GlobalProperties();
            }
        }
        return globals;
    }

    /**
     * method which is called to validate that the passed in
     * name/value is valid.
     *
     * If the data is not valid, a PropertyUpdateException should be
     * thrown.
     *
     * @param name the name of the property to be validated
     * @param value the new value requested for that property
     * @throws PropertyUpdateException the the value is invalid
     *
     */
    public void validate(String name, String value)
        throws PropertyUpdateException
    {
        // dont bother for now
    }

    /**
     * method which is called then a class which is interested in
     * the state of a specific property should updated its internal
     * state based on the new value of the property.
     *
     * @param name the name of the property to be validated
     * @param value the new value requested for that property
     * @return true if the property has taken affect, false if it
     *        will not take affect until the next broker restart
     *
     */
    public boolean update(String name, String value)
    {
        updateProperty(name);
        return true;
    }
}

