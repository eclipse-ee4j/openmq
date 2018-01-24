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
 * @(#)InterestTable.java	1.12 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.Enumeration;
import javax.jms.*;

/** The Interest Table is used to hold the consumer's object that has
 *  registered interest to the Broker.
 */


class InterestTable {

    //next available interest id.
    //XXX PROTOCOL2.1
    //private long nextInterestId = 0;
    //max number of consumers per client/connection.
    //XXX PROTOCOL2.1
    //public static final long MAX_INTEREST_ID = 1000000;

    //table to hold consumer objects that has registered interests to the
    //broker.
    private Hashtable table = new Hashtable();

    //when interestId reached its maximum, this flag is set.
    //XXX PROTOCOL2.1
    //private boolean interestIdReset = false;


    /**
     * Add message consumer to the interest table.
     *
     * @param intId    the key associated with the message consumer.
     * @param consumer the message consumer to be added to the interest table.
     */
    protected void
    put (Object intId, Object consumer) {
        table.put(intId, consumer);
    }

    /**
     * Remove message consumer form the interest table.
     *
     * @param intId the key to be used for removing the message consumer
     *              from the interest table.
     */
    protected void
    remove (Object intId) {
        table.remove (intId);
    }

    /**
     * Add message consumer to the interest table.
     *
     * @param consumer the message consumer to be added to the interest table.
     */
    protected void
    addInterest (Consumer consumer) {
        put (consumer.interestId, consumer);
    }

    /**
     * Remove message consumer form the interest table.
     *
     * @param consumer the message consumer to be removed from the interest
     * table.
     */
    protected void
    removeInterest (Consumer consumer) {
        if (consumer.interestId != null) {
            remove (consumer.interestId);
        }
    }

    /**
     * Get the message consumer from the interest table based on the
     * interest id.
     *
     * @param interestId the key that the consumer is used to store in the
     *                   interest table.
     * @return Consumer the consumer in the interest table that
     *                             matches the interest id.
     */
    protected Consumer
    getConsumer (Object interestId) {
        return  (Consumer)table.get(interestId);
    }

    /**
     * Get all consumers in this connection
     */
     protected Enumeration getAllConsumers() {
        return table.elements();
     }

     /**
      * return an array of all consumers in this connection.
      */
     protected Object[] toArray() {
         return table.values().toArray();
     }

    /**
     * Get the next available interest id.
     *
     * @return the next available interest id.
     */
    //XXX PROTOCOL2.1 -- to be removed.
    /*protected synchronized
    Long getNextInterestId() {
        nextInterestId ++;

        //check if it has reached max value
        if (nextInterestId == MAX_INTEREST_ID) {
            nextInterestId = 1;
            interestIdReset = true;
        }

        //if it has reached to the limit at least once.
        if ( interestIdReset == true ) {
            boolean found = false;
            while ( !found ) {
                //check if still in use
                Object key = table.get ( new Long (nextInterestId) );
                if ( key == null ) {
                    //not in use
                    found = true;
                } else {
                    //increase one and keep trying
                    nextInterestId ++;
                    //still need to check the limit
                    if (nextInterestId == MAX_INTEREST_ID) {
                        nextInterestId = 1;
                    }
                }
            }
        }
        //XXX PROTOCOL2.1
        return new Long (nextInterestId);
    }*/

}

